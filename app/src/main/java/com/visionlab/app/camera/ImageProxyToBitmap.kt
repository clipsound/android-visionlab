package com.visionlab.app.camera

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy

fun ImageProxy.toBitmapArgb8888(): Bitmap {
    require(planes.size == 1) { "Expected RGBA_8888 single plane, got planes=${planes.size}" }
    val plane = planes[0]
    val buffer = plane.buffer.duplicate()
    buffer.rewind()

    val width = width
    val height = height
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    if (pixelStride == 4 && rowStride == 4 * width) {
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    val row = IntArray(width)
    for (y in 0 until height) {
        val rowStart = y * rowStride
        for (x in 0 until width) {
            val pos = rowStart + x * pixelStride
            buffer.position(pos)
            val r = buffer.get().toInt() and 0xff
            val g = buffer.get().toInt() and 0xff
            val b = buffer.get().toInt() and 0xff
            val a = buffer.get().toInt() and 0xff
            row[x] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(row, 0, width, 0, y, width, 1)
    }
    return bitmap
}
