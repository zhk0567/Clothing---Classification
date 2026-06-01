package com.deepfashion.classifier

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class GridOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = 0x66FFFFFF
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawLine(w / 3, 0f, w / 3, h, paint)
        canvas.drawLine(2 * w / 3, 0f, 2 * w / 3, h, paint)
        canvas.drawLine(0f, h / 3, w, h / 3, paint)
        canvas.drawLine(0f, 2 * h / 3, w, 2 * h / 3, paint)
    }
}
