package com.visionlab.app.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

private const val TAG = "VisionLab"

/**
 * Motivo per cui Logcat mostra CPU vs GPU (cerca tag `VisionLab`):
 * - "TFLite: GpuDelegate attivo" → GPU
 * - "TFLite: GpuDelegate non disponibile, uso CPU" → CPU (con eccezione se presente)
 * - "TFLite: GPU disabilitata da configurazione" → useGpu=false
 */
class TfliteEngine(
    context: Context,
    assetPath: String,
    useGpu: Boolean = true,
    numThreads: Int = 4,
) : AutoCloseable {
    private val gpuDelegate: GpuDelegate? = if (useGpu) {
        runCatching { GpuDelegate() }
            .onFailure { e ->
                Log.w(TAG, "TFLite: GpuDelegate non creato (${e.javaClass.simpleName}: ${e.message})", e)
            }
            .getOrNull()
    } else {
        Log.i(TAG, "TFLite: GPU disabilitata da configurazione (useGpu=false), solo CPU")
        null
    }

    init {
        if (useGpu) {
            if (gpuDelegate != null) {
                Log.i(TAG, "TFLite: GpuDelegate attivo (inferenza su GPU quando supportata dal modello)")
            } else {
                Log.w(TAG, "TFLite: GpuDelegate non disponibile, uso CPU (thread=$numThreads)")
            }
        }
    }

    private val interpreter: Interpreter = Interpreter(
        loadModelFile(context, assetPath),
        Interpreter.Options().apply {
            setNumThreads(numThreads)
            gpuDelegate?.let { addDelegate(it) }
        },
    )

    fun interpreter(): Interpreter = interpreter

    override fun close() {
        interpreter.close()
        gpuDelegate?.close()
    }

    private fun loadModelFile(context: Context, assetPath: String): MappedByteBuffer {
        val afd = context.assets.openFd(assetPath)
        FileInputStream(afd.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }
}
