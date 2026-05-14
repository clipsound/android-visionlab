package com.visionlab.app.core

import com.visionlab.app.ml.RgbBitmap

data class Detection(
    val label: String,
    val score: Float,
    val box: android.graphics.RectF,
)

data class VisionMetrics(
    val fps: Float,
    val lastInferenceMs: Float,
)

sealed class VisionResult {
    data class Detections(
        val items: List<Detection>,
        val metrics: VisionMetrics,
        /** Larghezza/altezza dello spazio in cui sono definite le coordinate delle `box` (dopo rotazione usata in inferenza). */
        val coordSpaceWidth: Int,
        val coordSpaceHeight: Int,
    ) : VisionResult()

    data class Unsupported(
        val message: String,
    ) : VisionResult()
}

interface VisionModel {
    val descriptor: ModelDescriptor
    fun close()
    fun run(bitmap: RgbBitmap): VisionResult

    /** Solo i detector che usano una soglia (es. YOLO); default no-op. */
    fun setConfidenceThreshold(threshold: Float) = Unit
}
