package com.deepfashion.classifier

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CropOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dimPaint = Paint().apply { color = 0x99000000.toInt() }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }

    private var imageBounds = RectF()
    private val cropRect = RectF()
    private val minSize = 80f
    private val handleRadius = 24f

    private enum class TouchMode { NONE, MOVE, RESIZE_TL, RESIZE_TR, RESIZE_BL, RESIZE_BR }
    private var touchMode = TouchMode.NONE
    private var lastX = 0f
    private var lastY = 0f

    fun setImageBounds(left: Float, top: Float, right: Float, bottom: Float) {
        imageBounds.set(left, top, right, bottom)
        if (cropRect.isEmpty) {
            val margin = min(imageBounds.width(), imageBounds.height()) * 0.1f
            cropRect.set(
                imageBounds.left + margin,
                imageBounds.top + margin,
                imageBounds.right - margin,
                imageBounds.bottom - margin
            )
        }
        invalidate()
    }

    fun getCropRectInView(): RectF = RectF(cropRect)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (imageBounds.isEmpty) return

        canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, dimPaint)
        canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, dimPaint)
        canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, dimPaint)

        canvas.drawRect(cropRect, borderPaint)
        listOf(
            cropRect.left to cropRect.top,
            cropRect.right to cropRect.top,
            cropRect.left to cropRect.bottom,
            cropRect.right to cropRect.bottom
        ).forEach { (x, y) ->
            canvas.drawCircle(x, y, handleRadius, handlePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (imageBounds.isEmpty) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchMode = detectMode(event.x, event.y)
                lastX = event.x
                lastY = event.y
                return touchMode != TouchMode.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                when (touchMode) {
                    TouchMode.MOVE -> moveCrop(dx, dy)
                    TouchMode.RESIZE_TL -> resizeCrop(dx, dy, true, true)
                    TouchMode.RESIZE_TR -> resizeCrop(dx, dy, false, true)
                    TouchMode.RESIZE_BL -> resizeCrop(dx, dy, true, false)
                    TouchMode.RESIZE_BR -> resizeCrop(dx, dy, false, false)
                    else -> {}
                }
                lastX = event.x
                lastY = event.y
                invalidate()
                return touchMode != TouchMode.NONE
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchMode = TouchMode.NONE
            }
        }
        return true
    }

    private fun detectMode(x: Float, y: Float): TouchMode {
        val corners = listOf(
            TouchMode.RESIZE_TL to Pair(cropRect.left, cropRect.top),
            TouchMode.RESIZE_TR to Pair(cropRect.right, cropRect.top),
            TouchMode.RESIZE_BL to Pair(cropRect.left, cropRect.bottom),
            TouchMode.RESIZE_BR to Pair(cropRect.right, cropRect.bottom)
        )
        for ((mode, pt) in corners) {
            if (abs(x - pt.first) < handleRadius * 2 && abs(y - pt.second) < handleRadius * 2) {
                return mode
            }
        }
        if (cropRect.contains(x, y)) return TouchMode.MOVE
        return TouchMode.NONE
    }

    private fun moveCrop(dx: Float, dy: Float) {
        var l = cropRect.left + dx
        var t = cropRect.top + dy
        var r = cropRect.right + dx
        var b = cropRect.bottom + dy
        if (l < imageBounds.left) {
            r -= l - imageBounds.left
            l = imageBounds.left
        }
        if (t < imageBounds.top) {
            b -= t - imageBounds.top
            t = imageBounds.top
        }
        if (r > imageBounds.right) {
            l -= r - imageBounds.right
            r = imageBounds.right
        }
        if (b > imageBounds.bottom) {
            t -= b - imageBounds.bottom
            b = imageBounds.bottom
        }
        cropRect.set(l, t, r, b)
    }

    private fun resizeCrop(dx: Float, dy: Float, leftEdge: Boolean, topEdge: Boolean) {
        var l = cropRect.left
        var t = cropRect.top
        var r = cropRect.right
        var b = cropRect.bottom
        if (leftEdge) l += dx else r += dx
        if (topEdge) t += dy else b += dy
        l = l.coerceIn(imageBounds.left, r - minSize)
        t = t.coerceIn(imageBounds.top, b - minSize)
        r = r.coerceIn(l + minSize, imageBounds.right)
        b = b.coerceIn(t + minSize, imageBounds.bottom)
        cropRect.set(l, t, r, b)
    }
}
