package com.visionlab.app.ml.yolo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.visionlab.app.core.Detection
import com.visionlab.app.core.ModelDescriptor
import com.visionlab.app.core.VisionMetrics
import com.visionlab.app.core.VisionModel
import com.visionlab.app.core.VisionResult
import com.visionlab.app.labels.LabelRepository
import com.visionlab.app.ml.RgbBitmap
import com.visionlab.app.ml.TfliteEngine
import org.tensorflow.lite.DataType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

class YoloTfliteDetector(
    context: Context,
    override val descriptor: ModelDescriptor,
    confidenceThreshold: Float = 0.25f,
    private val iouThreshold: Float = 0.45f,
    private val maxDetections: Int = 100,
) : VisionModel {
    private val labels = LabelRepository(context)
    private val engine = TfliteEngine(context, descriptor.assetPath, useGpu = true)
    private val interpreter = engine.interpreter()

    @Volatile
    private var confThreshold: Float = confidenceThreshold.coerceIn(0.05f, 0.95f)

    override fun setConfidenceThreshold(threshold: Float) {
        confThreshold = threshold.coerceIn(0.05f, 0.95f)
    }

    private val inputTensor = interpreter.getInputTensor(0)
    private val outputTensor = interpreter.getOutputTensor(0)

    private val inputShape: IntArray = inputTensor.shape()
    private val outputShape: IntArray = outputTensor.shape()

    private val inputH: Int
    private val inputW: Int
    private val inputFloatBuffer: ByteBuffer

    init {
        require(inputShape.size == 4) { "Input shape atteso NHWC rank=4, trovato ${inputShape.contentToString()}" }
        val channelLast = inputShape[3] == 3
        require(channelLast) {
            "Per ora supportiamo solo NHWC [1,H,W,3]. Shape: ${inputShape.contentToString()}"
        }
        inputH = inputShape[1]
        inputW = inputShape[2]

        require(inputTensor.dataType() == DataType.FLOAT32) {
            "Per la prima versione serve un modello TFLite con input FLOAT32. Esporta con int8=false oppure aggiungi quantizzazione in app."
        }
        require(outputTensor.dataType() == DataType.FLOAT32) {
            "Per la prima versione serve output FLOAT32."
        }

        val inputBytes = 4 * 1 * inputH * inputW * 3
        inputFloatBuffer = ByteBuffer.allocateDirect(inputBytes).order(ByteOrder.nativeOrder())
    }

    private var lastFps: Float = 0f
    private val frameTimesMs = ArrayDeque<Long>(30)
    private var lastInferenceMs: Float = 0f

    override fun close() {
        engine.close()
    }

    override fun run(bitmap: RgbBitmap): VisionResult {
        val t0 = System.nanoTime()
        val oriented = rotateIfNeeded(bitmap.bitmap, bitmap.rotationDegrees)
        val lb = letterboxToSquare(oriented, inputH)

        fillNhwcFloat01(lb.tensorBitmap, inputFloatBuffer, inputW, inputH)

        val out = allocateOutputArray()
        interpreter.run(inputFloatBuffer, out)

        val d1 = outputShape[1]
        val d2 = outputShape[2]
        val channels = min(d1, d2)
        val anchors = max(d1, d2)
        val transposed = d1 < d2

        val numClasses = channels - 4
        require(numClasses > 0) { "Canali output inattesi: $channels" }

        val candidates = ArrayList<Pair<RectF, Pair<Int, Float>>>(256)
        for (i in 0 until anchors) {
            val cx = get(out, transposed, i, 0)
            val cy = get(out, transposed, i, 1)
            val w = get(out, transposed, i, 2)
            val h = get(out, transposed, i, 3)

            var bestScore = -1f
            var bestCls = -1
            for (c in 0 until numClasses) {
                val raw = get(out, transposed, i, 4 + c)
                val s = classScoreActivation(raw)
                if (s > bestScore) {
                    bestScore = s
                    bestCls = c
                }
            }
            if (bestCls < 0) continue
            if (bestScore < confThreshold) continue

            val maxCoord = maxOf(cx, cy, w, h)
            val boxScale = if (maxCoord <= 1.5f) lb.inputSize.toFloat() else 1f

            val x1 = (cx - w / 2f) * boxScale
            val y1 = (cy - h / 2f) * boxScale
            val x2 = (cx + w / 2f) * boxScale
            val y2 = (cy + h / 2f) * boxScale

            val mapped = mapLetterboxToOriginal(x1, y1, x2, y2, lb)
            val clipW = oriented.width.toFloat()
            val clipH = oriented.height.toFloat()
            mapped.left = mapped.left.coerceIn(0f, clipW)
            mapped.top = mapped.top.coerceIn(0f, clipH)
            mapped.right = mapped.right.coerceIn(0f, clipW)
            mapped.bottom = mapped.bottom.coerceIn(0f, clipH)

            if (mapped.width() <= 1f || mapped.height() <= 1f) continue

            candidates.add(mapped to (bestCls to bestScore))
        }

        val coordSpaceWidth = oriented.width
        val coordSpaceHeight = oriented.height

        lb.tensorBitmap.recycle()
        if (oriented !== bitmap.bitmap) {
            oriented.recycle()
        }

        val picked = nonMaxSuppression(candidates, iouThreshold, maxDetections)
        val items = picked.map { (box, clsScore) ->
            val (cls, score) = clsScore
            Detection(label = labels.labelFor(cls), score = score, box = box)
        }

        val t1 = System.nanoTime()
        lastInferenceMs = (t1 - t0) / 1_000_000f

        val now = System.currentTimeMillis()
        frameTimesMs.addLast(now)
        if (frameTimesMs.size > 30) frameTimesMs.removeFirst()
        if (frameTimesMs.size >= 2) {
            val dt = (frameTimesMs.last() - frameTimesMs.first()).coerceAtLeast(1L).toFloat()
            lastFps = (frameTimesMs.size - 1) * 1000f / dt
        }

        return VisionResult.Detections(
            items = items,
            metrics = VisionMetrics(fps = lastFps, lastInferenceMs = lastInferenceMs),
            coordSpaceWidth = coordSpaceWidth,
            coordSpaceHeight = coordSpaceHeight,
        )
    }

    private fun allocateOutputArray(): Array<Array<FloatArray>> {
        require(outputShape.size == 3) { "Output shape atteso [1,A,C] o [1,C,A], trovato ${outputShape.contentToString()}" }
        val d0 = outputShape[0]
        val d1 = outputShape[1]
        val d2 = outputShape[2]
        return Array(d0) { Array(d1) { FloatArray(d2) } }
    }

    private fun get(out: Array<Array<FloatArray>>, transposed: Boolean, anchor: Int, channel: Int): Float {
        return if (!transposed) {
            out[0][anchor][channel]
        } else {
            out[0][channel][anchor]
        }
    }

    /**
     * Export Ultralytics: spesso probabilità già in [0,1], altre volte logit.
     * Se esce da [0,1] (o è chiaramente logit), applica sigmoid.
     */
    private fun classScoreActivation(raw: Float): Float {
        if (raw in 0f..1f) return raw
        val x = raw.coerceIn(-80f, 80f)
        return 1f / (1f + exp(-x))
    }

    private fun fillNhwcFloat01(bitmap: Bitmap, buffer: ByteBuffer, width: Int, height: Int) {
        buffer.clear()
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (p in pixels) {
            val r = ((p shr 16) and 0xff) / 255f
            val g = ((p shr 8) and 0xff) / 255f
            val b = (p and 0xff) / 255f
            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }
        buffer.rewind()
    }

    private fun rotateIfNeeded(src: Bitmap, rotationDegrees: Int): Bitmap {
        val r = ((rotationDegrees % 360) + 360) % 360
        if (r == 0) return src
        val m = android.graphics.Matrix()
        m.postRotate(r.toFloat())
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}
