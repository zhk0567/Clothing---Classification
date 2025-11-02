package com.deepfashion.classifier

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import ai.onnxruntime.*
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

class Detector(private val context: Context) {
    companion object { private const val TAG = "Detector" }
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var inputName: String? = null
    private var inputSize: Int = 320
    private var loadAttempted: Boolean = false

    fun loadIfAvailable(): Boolean {
        if (session != null) return true
        if (loadAttempted) return false
        loadAttempted = true
        return try {
            val am = context.assets
            val path = "models/yolov8n.onnx"
            val modelBytes = am.open(path).use { it.readBytes() }
            env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions()
            session = env!!.createSession(modelBytes, opts)
            inputName = session!!.inputNames.first()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Load failed", e)
            false
        }
    }

    data class Prediction(val box: RectF, val score: Float, val cls: Int)

    fun detect(bitmap: Bitmap, rotationDegrees: Int, viewW: Int, viewH: Int): List<Prediction> {
        val s = session ?: return emptyList()
        val size = inputSize

        // 1) 旋转到传感器正向
        val rotated = if (rotationDegrees != 0) {
            val m = Matrix()
            m.postRotate(rotationDegrees.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        } else bitmap

        // 2) letterbox 到 size×size，记录缩放与边距
        val rw = rotated.width.toFloat()
        val rh = rotated.height.toFloat()
        val scale = min(size / rw, size / rh)
        val nw = (rw * scale).toInt()
        val nh = (rh * scale).toInt()
        val padX = (size - nw) / 2
        val padY = (size - nh) / 2

        val letterboxed = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(letterboxed)
        val dst = android.graphics.Rect(padX, padY, padX + nw, padY + nh)
        canvas.drawColor(0xFF000000.toInt())
        canvas.drawBitmap(rotated, null, dst, null)

        val tensor = bitmapToFloat(letterboxed)
        val inputShape = longArrayOf(1, 3, size.toLong(), size.toLong())
        val fb = FloatBuffer.wrap(tensor)
        val input = OnnxTensor.createTensor(env, fb, inputShape)
        val outputs = s.run(mapOf(inputName!! to input))
        val outVal = outputs[0]
        val preds = mutableListOf<Prediction>()

        // PreviewView uses center-crop (fill) scaling; compute mapping from rotated image -> view
        val scaleFill = max(viewW / rw, viewH / rh)
        val dispW = rw * scaleFill
        val dispH = rh * scaleFill
        val cropX = (dispW - viewW) / 2f
        val cropY = (dispH - viewH) / 2f
        fun mapToView(xImg: Float, yImg: Float): Pair<Float, Float> {
            val x = xImg * scaleFill - cropX
            val y = yImg * scaleFill - cropY
            return x to y
        }

        // Use tensor shape + flat buffer to avoid brittle casts
        val outTensor = outVal as OnnxTensor
        val outShape = outTensor.info.shape
        val total = outShape.fold(1L) { acc, v -> acc * v }.toInt()
        val flat = FloatArray(total)
        outTensor.floatBuffer.get(flat)

        // Case 1: End-to-end with NMS -> [N,6]
        if (outShape.size == 2 && outShape[1] == 6L) {
            val n = outShape[0].toInt()
            var idx = 0
            for (i in 0 until n) {
                val x1i = (flat[idx++] - padX) / scale
                val y1i = (flat[idx++] - padY) / scale
                val x2i = (flat[idx++] - padX) / scale
                val y2i = (flat[idx++] - padY) / scale
                val score = flat[idx++]
                val cls = flat[idx++].toInt()
                val (x1, y1) = mapToView(x1i, y1i)
                val (x2, y2) = mapToView(x2i, y2i)
                if (score >= 0.25f) preds.add(Prediction(RectF(x1, y1, x2, y2), score, cls))
            }
            return preds
        }

        // Case 2: Raw head: [1,N,84] or [1,84,N] (N can be 2100, 8400, etc.)
        val isNhwc = (outShape.size == 3 && outShape[2] >= 6L)
        val isChw = (outShape.size == 3 && outShape[1] >= 6L)
        val boxes: List<FloatArray> = if (isNhwc) {
            val num = outShape[1].toInt()
            val dim = outShape[2].toInt()
            List(num) { i ->
                val row = FloatArray(dim)
                System.arraycopy(flat, i * dim, row, 0, dim)
                row
            }
        } else if (isChw) {
            val dim = outShape[1].toInt()
            val num = outShape[2].toInt()
            // transpose [dim,num] -> [num,dim]
            val trans = Array(num) { FloatArray(dim) }
            var idx = 0
            for (d in 0 until dim) {
                for (n in 0 until num) {
                    trans[n][d] = flat[idx++]
                }
            }
            trans.toList()
        } else {
            emptyList()
        }
        for (b in boxes) {
            val x = b[0]; val y = b[1]; val w = b[2]; val h = b[3]
            var bestScore = 0f; var bestCls = -1
            for (i in 4 until b.size) {
                val sc = b[i]
                if (sc > bestScore) { bestScore = sc; bestCls = i - 4 }
            }
            val score = bestScore
            if (score < 0.25f) continue
            val x1i = (x - w / 2f) - padX
            val y1i = (y - h / 2f) - padY
            val x2i = (x + w / 2f) - padX
            val y2i = (y + h / 2f) - padY
            val x1s = (x1i / scale)
            val y1s = (y1i / scale)
            val x2s = (x2i / scale)
            val y2s = (y2i / scale)
            val (x1, y1) = mapToView(x1s, y1s)
            val (x2, y2) = mapToView(x2s, y2s)
            preds.add(Prediction(RectF(x1, y1, x2, y2), score, bestCls))
        }
        // simple NMS
        return nms(preds, 0.5f)
    }

    private fun nms(boxes: List<Prediction>, iouThresh: Float): List<Prediction> {
        val sorted = boxes.sortedByDescending { it.score }.toMutableList()
        val keep = mutableListOf<Prediction>()
        while (sorted.isNotEmpty()) {
            val a = sorted.removeAt(0)
            keep.add(a)
            val it = sorted.iterator()
            while (it.hasNext()) {
                val b = it.next()
                if (iou(a.box, b.box) > iouThresh) it.remove()
            }
        }
        return keep
    }

    private fun iou(a: RectF, b: RectF): Float {
        val x1 = max(a.left, b.left)
        val y1 = max(a.top, b.top)
        val x2 = min(a.right, b.right)
        val y2 = min(a.bottom, b.bottom)
        val inter = max(0f, x2 - x1) * max(0f, y2 - y1)
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val union = areaA + areaB - inter + 1e-6f
        return (inter / union)
    }

    private fun bitmapToFloat(bm: Bitmap): FloatArray {
        val w = bm.width; val h = bm.height
        val pixels = IntArray(w * h)
        bm.getPixels(pixels, 0, w, 0, 0, w, h)
        val data = FloatArray(3 * w * h)
        var idx = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = pixels[y * w + x]
                val r = ((c shr 16) and 0xFF) / 255f
                val g = ((c shr 8) and 0xFF) / 255f
                val b = (c and 0xFF) / 255f
                data[idx] = r; data[idx + w * h] = g; data[idx + 2 * w * h] = b
                idx++
            }
        }
        return data
    }
}


