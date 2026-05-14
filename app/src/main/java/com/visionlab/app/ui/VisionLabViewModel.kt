package com.visionlab.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.visionlab.app.core.Detection
import com.visionlab.app.core.ModelDescriptor
import com.visionlab.app.core.ModelRegistry
import com.visionlab.app.core.VisionModel
import com.visionlab.app.core.VisionResult
import com.visionlab.app.ml.RgbBitmap
import com.visionlab.app.ml.VisionModelFactory
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class RunMode {
    LIVE,
    GALLERY,
}

data class VisionUiState(
    val mode: RunMode = RunMode.LIVE,
    val selectedModel: ModelDescriptor = ModelRegistry.models.first(),
    val confidenceThreshold: Float = 0.25f,
    val detections: List<Detection> = emptyList(),
    val infoMessage: String? = null,
    val fps: Float = 0f,
    val inferMs: Float = 0f,
    val frameW: Int = 0,
    val frameH: Int = 0,
    val galleryBitmap: Bitmap? = null,
    /** Live: true = fotocamera frontale (se disponibile). */
    val useFrontCamera: Boolean = false,
    /** Live: esposizione automatica; se false usa shutter + gain manuali (Camera2). */
    val cameraAeAuto: Boolean = true,
    /** Live: 0…1 → tempo di esposizione (scala log tra min/max del sensore). */
    val exposureSlider: Float = 0.35f,
    /** Live: 0…1 → ISO lineare tra min/max. */
    val gainSlider: Float = 0.5f,
    val exposureMinNs: Long = 0L,
    val exposureMaxNs: Long = 0L,
    val isoMin: Int = 0,
    val isoMax: Int = 0,
)

class VisionLabViewModel(application: Application) : AndroidViewModel(application) {
    var state by mutableStateOf(VisionUiState())
        private set

    /**
     * Caricamento TFLite + GpuDelegate/OpenCL può richiedere ~1–2s: non va sul main thread
     * (evita centinaia di frame saltati all’avvio).
     */
    private val modelRef = AtomicReference<VisionModel?>(null)
    private val modelInitJob: Job

    /** TFLite Interpreter non è thread-safe: una sola inferenza alla volta. */
    private val inferenceMutex = Mutex()

    init {
        state = state.copy(infoMessage = "Caricamento modello…")
        val app = application
        val descriptor = state.selectedModel
        val threshold = state.confidenceThreshold
        modelInitJob = viewModelScope.launch(Dispatchers.Default) {
            val m = VisionModelFactory.create(app, descriptor, threshold)
            modelRef.set(m)
            withContext(Dispatchers.Main) {
                state = state.copy(infoMessage = null)
            }
        }
    }

    private suspend fun awaitModel(): VisionModel {
        modelInitJob.join()
        return modelRef.get() ?: error("Modello non inizializzato")
    }

    fun setMode(mode: RunMode) {
        state = state.copy(mode = mode)
    }

    fun setUseFrontCamera(useFront: Boolean) {
        if (useFront == state.useFrontCamera) return
        state = state.copy(
            useFrontCamera = useFront,
            exposureMinNs = 0L,
            exposureMaxNs = 0L,
            isoMin = 0,
            isoMax = 0,
        )
    }

    fun setCameraAeAuto(auto: Boolean) {
        if (auto == state.cameraAeAuto) return
        state = state.copy(cameraAeAuto = auto)
    }

    fun setExposureSlider(value: Float) {
        state = state.copy(exposureSlider = value.coerceIn(0f, 1f))
    }

    fun setGainSlider(value: Float) {
        state = state.copy(gainSlider = value.coerceIn(0f, 1f))
    }

    fun setCameraSensorCapabilities(
        exposureMinNs: Long,
        exposureMaxNs: Long,
        isoMin: Int,
        isoMax: Int,
    ) {
        state = state.copy(
            exposureMinNs = exposureMinNs,
            exposureMaxNs = exposureMaxNs,
            isoMin = isoMin,
            isoMax = isoMax,
        )
    }

    fun setConfidenceThreshold(value: Float) {
        val v = value.coerceIn(0.05f, 0.95f)
        if (v == state.confidenceThreshold) return
        state = state.copy(confidenceThreshold = v)
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { awaitModel().setConfidenceThreshold(v) }
        }
    }

    fun setSelectedModel(descriptor: ModelDescriptor) {
        if (descriptor.id == state.selectedModel.id) return
        val threshold = state.confidenceThreshold
        viewModelScope.launch(Dispatchers.Default) {
            modelInitJob.join()
            val old = modelRef.get() ?: return@launch
            val newModel = VisionModelFactory.create(getApplication(), descriptor, threshold)
            modelRef.set(newModel)
            old.close()
            withContext(Dispatchers.Main) {
                state = state.copy(
                    selectedModel = descriptor,
                    detections = emptyList(),
                    infoMessage = null,
                    fps = 0f,
                    inferMs = 0f,
                    frameW = 0,
                    frameH = 0,
                )
            }
        }
    }

    override fun onCleared() {
        runBlocking(Dispatchers.Default) {
            modelInitJob.join()
            modelRef.getAndSet(null)?.close()
        }
        state.galleryBitmap?.let { if (!it.isRecycled) it.recycle() }
    }

    fun submitCameraFrame(bitmap: Bitmap, rotationDegrees: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val m = runCatching { awaitModel() }.getOrElse { e ->
                Log.e(TAG, "Modello non pronto", e)
                if (!bitmap.isRecycled) bitmap.recycle()
                return@launch
            }
            if (!inferenceMutex.tryLock()) {
                if (!bitmap.isRecycled) bitmap.recycle()
                return@launch
            }
            try {
                val res = m.run(RgbBitmap(bitmap, rotationDegrees))
                withContext(Dispatchers.Main) {
                    applyResult(res, bitmap.width, bitmap.height)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Inferenza (camera) fallita", e)
                withContext(Dispatchers.Main) {
                    state = state.copy(infoMessage = e.message ?: e.toString())
                }
            } finally {
                inferenceMutex.unlock()
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }

    fun analyzeGalleryBitmap(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            val m = runCatching { awaitModel() }.getOrElse { e ->
                Log.e(TAG, "Modello non pronto (galleria)", e)
                withContext(Dispatchers.Main) {
                    state = state.copy(
                        galleryBitmap = bitmap,
                        infoMessage = e.message ?: e.toString(),
                        detections = emptyList(),
                    )
                }
                return@launch
            }
            val res = try {
                inferenceMutex.withLock {
                    runCatching { m.run(RgbBitmap(bitmap, 0)) }.getOrElse { e ->
                        VisionResult.Unsupported("Errore inferenza:\n${e.message}")
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Inferenza (galleria) fallita", e)
                VisionResult.Unsupported(e.message ?: e.toString())
            }

            withContext(Dispatchers.Main) {
                val old = state.galleryBitmap
                if (old != null && old !== bitmap && !old.isRecycled) {
                    old.recycle()
                }

                when (res) {
                    is VisionResult.Detections -> {
                        state = state.copy(
                            galleryBitmap = bitmap,
                            detections = res.items,
                            infoMessage = null,
                            fps = res.metrics.fps,
                            inferMs = res.metrics.lastInferenceMs,
                            frameW = res.coordSpaceWidth,
                            frameH = res.coordSpaceHeight,
                        )
                    }
                    is VisionResult.Unsupported -> {
                        state = state.copy(
                            galleryBitmap = bitmap,
                            detections = emptyList(),
                            infoMessage = res.message,
                            fps = 0f,
                            inferMs = 0f,
                            frameW = bitmap.width,
                            frameH = bitmap.height,
                        )
                    }
                }
            }
        }
    }

    fun clearGallery() {
        state.galleryBitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
        state = state.copy(
            galleryBitmap = null,
            detections = emptyList(),
            infoMessage = null,
            frameW = 0,
            frameH = 0,
        )
    }

    fun loadGalleryUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = decodeBitmapMaxSide(getApplication(), uri, maxSide = 1600)
            if (bmp == null) {
                withContext(Dispatchers.Main) {
                    state = state.copy(infoMessage = "Impossibile leggere l'immagine.")
                }
                return@launch
            }
            analyzeGalleryBitmap(bmp)
        }
    }

    private fun applyResult(res: VisionResult, w: Int, h: Int) {
        when (res) {
            is VisionResult.Detections -> {
                state = state.copy(
                    detections = res.items,
                    infoMessage = null,
                    fps = res.metrics.fps,
                    inferMs = res.metrics.lastInferenceMs,
                    frameW = res.coordSpaceWidth,
                    frameH = res.coordSpaceHeight,
                )
            }
            is VisionResult.Unsupported -> {
                state = state.copy(
                    detections = emptyList(),
                    infoMessage = res.message,
                    fps = 0f,
                    inferMs = 0f,
                    frameW = w,
                    frameH = h,
                )
            }
        }
    }
}

private const val TAG = "VisionLab"

private fun decodeBitmapMaxSide(app: Application, uri: Uri, maxSide: Int): Bitmap? {
    val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    app.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, optsBounds)
    } ?: return null

    val w = optsBounds.outWidth
    val h = optsBounds.outHeight
    if (w <= 0 || h <= 0) return null

    var sample = 1
    val maxDim = maxOf(w, h)
    while (maxDim / sample > maxSide) {
        sample *= 2
    }

    return app.contentResolver.openInputStream(uri)?.use { input2 ->
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeStream(input2, null, opts)
    }
}
