package com.visionlab.app.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.RectF
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.visionlab.app.camera.Camera2CaptureOverrides
import com.visionlab.app.camera.ImageAnalysisResolutionSelectors
import com.visionlab.app.camera.toBitmapArgb8888
import com.visionlab.app.core.Detection
import com.visionlab.app.core.ModelRegistry
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionLabScreen(
    vm: VisionLabViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted },
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val state = vm.state

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("VisionLab") },
            )
        },
    ) { padding ->
        var hasFrontCamera by remember { mutableStateOf(false) }
        LaunchedEffect(context) {
            val p = ProcessCameraProvider.getInstance(context).get()
            hasFrontCamera = p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        }

        val pickGallery = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
            onResult = { uri -> uri?.let { vm.loadGalleryUri(it) } },
        )

        var panelModeExpanded by remember { mutableStateOf(true) }
        var panelModelExpanded by remember { mutableStateOf(true) }
        var panelGalleryExpanded by remember { mutableStateOf(true) }
        var panelDetectExpanded by remember { mutableStateOf(false) }
        var panelPerfExpanded by remember { mutableStateOf(false) }
        var panelCameraExpanded by remember { mutableStateOf(false) }

        var modelMenuExpanded by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .safeDrawingPadding()
                .padding(12.dp),
        ) {
            // Metà schermo: anteprima camera o immagine galleria
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (state.mode) {
                    RunMode.LIVE -> {
                        if (!hasCameraPermission) {
                            Text(
                                text = "Permesso fotocamera negato. Abilitalo dalle impostazioni.",
                                modifier = Modifier.align(Alignment.Center),
                            )
                        } else {
                            Box(Modifier.fillMaxSize()) {
                                CameraPreviewSection(
                                    lifecycleOwner = lifecycleOwner,
                                    vm = vm,
                                    onFrame = { bitmap, rotationDegrees ->
                                        vm.submitCameraFrame(bitmap, rotationDegrees)
                                    },
                                )
                                if (state.frameW > 0 && state.frameH > 0) {
                                    DetectionOverlay(
                                        detections = state.detections,
                                        frameW = state.frameW,
                                        frameH = state.frameH,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                    RunMode.GALLERY -> {
                        GalleryImagePane(state = state)
                    }
                }
            }

            // Metà schermo: controlli in pannelli collassabili
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CollapsiblePanel(
                    title = "Modalità",
                    expanded = panelModeExpanded,
                    onExpandedChange = { panelModeExpanded = it },
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = state.mode == RunMode.LIVE,
                            onClick = { vm.setMode(RunMode.LIVE) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) { Text("Live") }
                        SegmentedButton(
                            selected = state.mode == RunMode.GALLERY,
                            onClick = { vm.setMode(RunMode.GALLERY) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) { Text("Galleria") }
                    }
                }

                CollapsiblePanel(
                    title = "Modello",
                    expanded = panelModelExpanded,
                    onExpandedChange = { panelModelExpanded = it },
                ) {
                    ExposedDropdownMenuBox(
                        expanded = modelMenuExpanded,
                        onExpandedChange = { modelMenuExpanded = !modelMenuExpanded },
                    ) {
                        OutlinedTextField(
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            readOnly = true,
                            value = state.selectedModel.displayName,
                            onValueChange = {},
                            label = { Text("Modello TFLite") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded)
                            },
                        )
                        ExposedDropdownMenu(
                            expanded = modelMenuExpanded,
                            onDismissRequest = { modelMenuExpanded = false },
                        ) {
                            ModelRegistry.models.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.displayName) },
                                    onClick = {
                                        modelMenuExpanded = false
                                        vm.setSelectedModel(m)
                                    },
                                )
                            }
                        }
                    }
                }

                if (state.mode == RunMode.GALLERY) {
                    CollapsiblePanel(
                        title = "Galleria",
                        expanded = panelGalleryExpanded,
                        onExpandedChange = { panelGalleryExpanded = it },
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    pickGallery.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                            ) {
                                Text("Scegli foto")
                            }
                            if (state.galleryBitmap != null) {
                                Button(onClick = { vm.clearGallery() }) {
                                    Text("Pulisci")
                                }
                            }
                        }
                    }
                }

                CollapsiblePanel(
                    title = "Rilevamento",
                    expanded = panelDetectExpanded,
                    onExpandedChange = { panelDetectExpanded = it },
                ) {
                    ConfidenceThresholdRow(vm = vm, state = state)
                }

                CollapsiblePanel(
                    title = "Prestazioni",
                    expanded = panelPerfExpanded,
                    onExpandedChange = { panelPerfExpanded = it },
                ) {
                    MetricsBar(
                        fps = state.fps,
                        inferMs = state.inferMs,
                        message = state.infoMessage,
                    )
                }

                if (state.mode == RunMode.LIVE && hasCameraPermission) {
                    CollapsiblePanel(
                        title = "Fotocamera",
                        expanded = panelCameraExpanded,
                        onExpandedChange = { panelCameraExpanded = it },
                    ) {
                        LiveCameraControlPanel(
                            vm = vm,
                            state = state,
                            hasFrontCamera = hasFrontCamera,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsiblePanel(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (expanded) "▼" else "▶",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun GalleryImagePane(state: VisionUiState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = state.galleryBitmap
        if (bmp == null) {
            Text(
                text = "Usa il pannello «Galleria» sotto per scegliere un'immagine.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            if (state.frameW > 0 && state.frameH > 0) {
                DetectionOverlay(
                    detections = state.detections,
                    frameW = state.frameW,
                    frameH = state.frameH,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ConfidenceThresholdRow(
    vm: VisionLabViewModel,
    state: VisionUiState,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Soglia confidenza: ${(state.confidenceThreshold * 100f).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = state.confidenceThreshold,
            onValueChange = { vm.setConfidenceThreshold(it) },
            valueRange = 0.05f..0.95f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MetricsBar(
    fps: Float,
    inferMs: Float,
    message: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "FPS: ${fps.as1()}   |   Inferenza: ${inferMs.as1()} ms",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun Float.as1(): String = "%.1f".format(this)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveCameraControlPanel(
    vm: VisionLabViewModel,
    state: VisionUiState,
    hasFrontCamera: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !state.useFrontCamera,
                onClick = { vm.setUseFrontCamera(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Posteriore") }
            SegmentedButton(
                selected = state.useFrontCamera,
                onClick = { vm.setUseFrontCamera(true) },
                enabled = hasFrontCamera,
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Anteriore") }
        }
        if (!hasFrontCamera) {
            Text(
                text = "Fotocamera anteriore non disponibile su questo dispositivo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Esposizione automatica (AE)",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(
                checked = state.cameraAeAuto,
                onCheckedChange = { vm.setCameraAeAuto(it) },
            )
        }

        val canManual =
            !state.cameraAeAuto &&
                state.exposureMaxNs > state.exposureMinNs &&
                state.isoMax > state.isoMin

        if (!state.cameraAeAuto) {
            if (!canManual) {
                Text(
                    text = "Calibrazione sensore in corso… riprova tra un attimo dopo l’anteprima.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                val expNs = Camera2CaptureOverrides.previewExposureNanoseconds(
                    state.exposureSlider,
                    state.exposureMinNs,
                    state.exposureMaxNs,
                )
                val isoVal = Camera2CaptureOverrides.previewIsoValue(
                    state.gainSlider,
                    state.isoMin,
                    state.isoMax,
                )
                val ms = expNs / 1_000_000.0
                Text(
                    text = "Shutter (tempo): ${"%.2f".format(ms)} ms",
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = state.exposureSlider,
                    onValueChange = { vm.setExposureSlider(it) },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Gain (ISO): $isoVal",
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = state.gainSlider,
                    onValueChange = { vm.setGainSlider(it) },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalCamera2Interop::class)
@Composable
private fun CameraPreviewSection(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    vm: VisionLabViewModel,
    onFrame: (Bitmap, Int) -> Unit,
) {
    val context = LocalContext.current
    val state = vm.state
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    val selector = remember(state.useFrontCamera) {
        if (state.useFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )

    DisposableEffect(lifecycleOwner, previewView, selector) {
        boundCamera = null
        val provider = ProcessCameraProvider.getInstance(context).get()
        if (!provider.hasCamera(selector)) {
            onDispose { }
        } else {
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val analysisResolution = ImageAnalysisResolutionSelectors.analysis640x480()

            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(analysisResolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            analysis.setAnalyzer(executor) { image ->
                val rot = image.imageInfo.rotationDegrees
                val bmp = runCatching { image.toBitmapArgb8888() }.getOrNull()
                image.close()
                if (bmp != null) {
                    onFrame(bmp, rot)
                }
            }

            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                analysis,
            )
            boundCamera = camera

            runCatching {
                val cameraId = Camera2CameraInfo.from(camera.cameraInfo).cameraId
                val mgr = context.getSystemService(android.content.Context.CAMERA_SERVICE) as CameraManager
                val ch = mgr.getCameraCharacteristics(cameraId)
                val expR = ch.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                val isoR = ch.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                if (expR != null && isoR != null) {
                    mainExecutor.execute {
                        vm.setCameraSensorCapabilities(
                            expR.lower,
                            expR.upper,
                            isoR.lower,
                            isoR.upper,
                        )
                    }
                }
            }.onFailure { e ->
                Log.w("VisionLab", "Lettura limiti sensore: ${e.message}")
            }

            onDispose {
                analysis.clearAnalyzer()
                boundCamera = null
                provider.unbindAll()
                executor.shutdown()
            }
        }
    }

    LaunchedEffect(
        boundCamera,
        state.cameraAeAuto,
        state.exposureSlider,
        state.gainSlider,
        state.exposureMinNs,
        state.exposureMaxNs,
        state.isoMin,
        state.isoMax,
    ) {
        val cam = boundCamera ?: return@LaunchedEffect
        if (state.cameraAeAuto) {
            Camera2CaptureOverrides.setAutoExposure(cam)
        } else if (state.exposureMaxNs > state.exposureMinNs && state.isoMax > state.isoMin) {
            Camera2CaptureOverrides.setManualShutterAndGain(
                cam,
                state.exposureSlider,
                state.gainSlider,
                state.exposureMinNs,
                state.exposureMaxNs,
                state.isoMin,
                state.isoMax,
            )
        }
    }
}

@Composable
private fun DetectionOverlay(
    detections: List<Detection>,
    frameW: Int,
    frameH: Int,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        if (frameW <= 0 || frameH <= 0) return@Canvas

        val s = min(size.width / frameW, size.height / frameH)
        val dx = (size.width - frameW * s) / 2f
        val dy = (size.height - frameH * s) / 2f

        fun mapRect(rect: RectF): Rect {
            val left = rect.left * s + dx
            val top = rect.top * s + dy
            val right = rect.right * s + dx
            val bottom = rect.bottom * s + dy
            return Rect(left, top, right, bottom)
        }

        detections.forEach { d ->
            val r = mapRect(d.box)
            drawPath(
                path = Path().apply {
                    addRect(r)
                },
                color = Color(0xFF22C55E),
                style = Stroke(width = 3f),
            )

            val label = "${d.label} ${(d.score * 100f).toInt()}%"
            val layout = textMeasurer.measure(
                text = AnnotatedString(label),
                style = TextStyle(color = Color.White, fontSize = 12.sp),
            )

            val bgTopLeft = Offset(r.left, max(0f, r.top - layout.size.height))
            drawRect(
                color = Color(0x99000000),
                topLeft = bgTopLeft,
                size = Size(layout.size.width.toFloat(), layout.size.height.toFloat()),
            )
            drawText(
                textLayoutResult = layout,
                topLeft = bgTopLeft,
            )
        }
    }
}
