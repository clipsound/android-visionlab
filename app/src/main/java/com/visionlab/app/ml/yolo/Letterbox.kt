package com.visionlab.app.ml.yolo

import kotlin.math.min

data class LetterboxResult(
    val tensorBitmap: android.graphics.Bitmap,
    val scale: Float,
    val padX: Float,
    val padY: Float,
    val inputSize: Int,
)

fun letterboxToSquare(
    src: android.graphics.Bitmap,
    inputSize: Int,
): LetterboxResult {
    val w = src.width.toFloat()
    val h = src.height.toFloat()
    val scale = min(inputSize / w, inputSize / h)
    val newW = (w * scale).toInt().coerceAtLeast(1)
    val newH = (h * scale).toInt().coerceAtLeast(1)

    val scaled = android.graphics.Bitmap.createScaledBitmap(src, newW, newH, true)
    val out = android.graphics.Bitmap.createBitmap(inputSize, inputSize, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    canvas.drawColor(android.graphics.Color.rgb(114, 114, 114))

    val padX = (inputSize - newW) / 2f
    val padY = (inputSize - newH) / 2f
    canvas.drawBitmap(scaled, padX, padY, null)

    if (scaled != src) {
        scaled.recycle()
    }

    return LetterboxResult(
        tensorBitmap = out,
        scale = scale,
        padX = padX,
        padY = padY,
        inputSize = inputSize,
    )
}

fun mapLetterboxToOriginal(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    letterbox: LetterboxResult,
): android.graphics.RectF {
    val x1o = (x1 - letterbox.padX) / letterbox.scale
    val y1o = (y1 - letterbox.padY) / letterbox.scale
    val x2o = (x2 - letterbox.padX) / letterbox.scale
    val y2o = (y2 - letterbox.padY) / letterbox.scale
    return android.graphics.RectF(x1o, y1o, x2o, y2o)
}
