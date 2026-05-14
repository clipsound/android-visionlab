package com.visionlab.app.ml.yolo

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

private fun iou(a: RectF, b: RectF): Float {
    val x1 = max(a.left, b.left)
    val y1 = max(a.top, b.top)
    val x2 = min(a.right, b.right)
    val y2 = min(a.bottom, b.bottom)
    val interW = max(0f, x2 - x1)
    val interH = max(0f, y2 - y1)
    val inter = interW * interH
    if (inter <= 0f) return 0f
    val areaA = max(0f, a.width()) * max(0f, a.height())
    val areaB = max(0f, b.width()) * max(0f, b.height())
    val union = areaA + areaB - inter
    return if (union <= 0f) 0f else inter / union
}

fun nonMaxSuppression(
    boxes: List<Pair<RectF, Pair<Int, Float>>>,
    iouThreshold: Float,
    maxDetections: Int,
): List<Pair<RectF, Pair<Int, Float>>> {
    val sorted = boxes.sortedByDescending { it.second.second }
    val selected = ArrayList<Pair<RectF, Pair<Int, Float>>>()
    val suppressed = BooleanArray(sorted.size)
    for (i in sorted.indices) {
        if (suppressed[i]) continue
        val cand = sorted[i]
        selected.add(cand)
        if (selected.size >= maxDetections) break
        for (j in i + 1 until sorted.size) {
            if (suppressed[j]) continue
            if (iou(cand.first, sorted[j].first) >= iouThreshold) {
                suppressed[j] = true
            }
        }
    }
    return selected
}
