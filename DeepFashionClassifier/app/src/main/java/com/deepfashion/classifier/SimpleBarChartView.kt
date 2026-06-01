package com.deepfashion.classifier

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class SimpleBarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, com.google.android.material.R.color.design_default_color_primary)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, android.R.color.darker_gray)
        textSize = 24f
    }
    private var labels: List<String> = emptyList()
    private var values: List<Float> = emptyList()

    fun setData(labels: List<String>, values: List<Int>) {
        this.labels = labels
        this.values = values.map { it.toFloat() }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return
        val max = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val barWidth = width.toFloat() / values.size * 0.6f
        val gap = width.toFloat() / values.size * 0.4f
        values.forEachIndexed { i, v ->
            val left = i * (barWidth + gap) + gap / 2
            val barHeight = (height - 40) * (v / max)
            val top = height - 30 - barHeight
            canvas.drawRect(left, top, left + barWidth, height - 30f, barPaint)
            if (i < labels.size) {
                canvas.drawText(labels[i], left, height - 8f, textPaint)
            }
        }
    }
}
