package com.visionlab.app.ml

import android.content.Context
import com.visionlab.app.core.ModelDescriptor
import com.visionlab.app.core.VisionModel
import com.visionlab.app.core.VisionResult
import com.visionlab.app.core.VisionTask
import com.visionlab.app.ml.yolo.YoloTfliteDetector

object VisionModelFactory {
    fun create(
        context: Context,
        descriptor: ModelDescriptor,
        confidenceThreshold: Float = 0.25f,
    ): VisionModel {
        return when (descriptor.task) {
            VisionTask.OBJECT_DETECTION -> {
                if (descriptor.id != "yolo_tflite_default") {
                    return StubVisionModel(descriptor)
                }
                if (!assetExists(context, descriptor.assetPath)) {
                    return PlaceholderVisionModel(
                        descriptor,
                        "Modello mancante in assets:\n${descriptor.assetPath}\n\nVedi README per esportarlo da Ultralytics.",
                    )
                }
                runCatching { YoloTfliteDetector(context, descriptor, confidenceThreshold = confidenceThreshold) }
                    .getOrElse { e ->
                        PlaceholderVisionModel(
                            descriptor,
                            "Errore caricamento YOLO:\n${e.message}",
                        )
                    }
            }
            VisionTask.INSTANCE_SEGMENTATION,
            VisionTask.POSE_ESTIMATION,
            VisionTask.DEPTH_ESTIMATION,
            VisionTask.TEXT_OCR,
            -> StubVisionModel(descriptor)
        }
    }

    private fun assetExists(context: Context, assetPath: String): Boolean {
        if (assetPath.isBlank()) return false
        return runCatching {
            context.assets.openFd(assetPath).close()
            true
        }.getOrDefault(false)
    }
}

private class PlaceholderVisionModel(
    override val descriptor: ModelDescriptor,
    private val message: String,
) : VisionModel {
    override fun close() = Unit
    override fun run(bitmap: RgbBitmap): VisionResult = VisionResult.Unsupported(message)
}

private class StubVisionModel(
    override val descriptor: ModelDescriptor,
) : VisionModel {
    override fun close() = Unit
    override fun run(bitmap: RgbBitmap): VisionResult {
        val msg = when (descriptor.task) {
            VisionTask.INSTANCE_SEGMENTATION ->
                "Segmentazione: prossimo passo è un modello YOLO-seg / DeepLab in TFLite + decoder maschere."
            VisionTask.POSE_ESTIMATION ->
                "Pose: prossimo passo è MoveNet/YOLO-pose in TFLite + skeleton renderer."
            VisionTask.DEPTH_ESTIMATION ->
                "Profondità: prossimo passo è un modello depth TFLite/ONNX + visualizzazione pseudo-color."
            VisionTask.TEXT_OCR ->
                "OCR: prossimo passo è ML Kit Text Recognition oppure un detector+recognizer TFLite."
            VisionTask.OBJECT_DETECTION ->
                "Detection: seleziona il modello YOLO predefinito."
        }
        return VisionResult.Unsupported(msg)
    }
}
