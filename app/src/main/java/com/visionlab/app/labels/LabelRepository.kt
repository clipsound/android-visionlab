package com.visionlab.app.labels

import android.content.Context

class LabelRepository(context: Context) {
    private val labels: List<String> = run {
        val text = context.assets.open("labels/coco80.txt").bufferedReader().readLines()
        text.map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun labelFor(classId: Int): String {
        if (classId in labels.indices) return labels[classId]
        return "cls_$classId"
    }

    val size: Int get() = labels.size
}
