package com.deepfashion.classifier

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.min

class SimplePieChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Slice(val label: String, val value: Float, val color: Int)

    private val slices = mutableListOf<Slice>()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, android.R.color.darker_gray)
        textSize = 28f
    }
    private val colors = intArrayOf(
        0xFF6200EE.toInt(), 0xFF03DAC5.toInt(), 0xFF018786.toInt(),
        0xFFBB86FC.toInt(), 0xFFCF6679.toInt(), 0xFF3700B3.toInt(),
        0xFF757575.toInt()
    )

    fun setData(labels: List<String>, values: List<Int>) {
        slices.clear()
        val total = values.sum().coerceAtLeast(1)
        labels.zip(values).forEachIndexed { i, (label, value) ->
            slices.add(Slice(label, value.toFloat() / total, colors[i % colors.size]))
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (slices.isEmpty()) return
        val size = min(width, height).toFloat() * 0.85f
        val left = (width - size) / 2f
        val top = 16f
        val oval = RectF(left, top, left + size, top + size)
        var start = -90f
        slices.forEach { slice ->
            val sweep = slice.value * 360f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = slice.color }
            canvas.drawArc(oval, start, sweep, true, paint)
            start += sweep
        }
        var legendY = top + size + 24f
        slices.forEach { slice ->
            val pct = (slice.value * 100).toInt()
            canvas.drawText("${slice.label} ($pct%)", 16f, legendY, textPaint)
            legendY += 36f
        }
    }
}
