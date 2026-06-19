package com.deepfashion.classifier

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
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
        textAlign = Paint.Align.CENTER
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
        if (values.isEmpty() || width == 0) return

        val labelArea = dp(28f)
        val chartBottom = height - labelArea
        val count = values.size
        val slotWidth = width.toFloat() / count
        val barWidth = slotWidth * 0.55f

        textPaint.textSize = sp(
            when {
                count <= 7 -> 11f
                count <= 14 -> 10f
                else -> 9f
            }
        )

        val labelStride = computeLabelStride(count)
        val max = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f

        values.forEachIndexed { i, v ->
            val centerX = slotWidth * i + slotWidth / 2f
            val left = centerX - barWidth / 2f
            val barHeight = chartBottom * (v / max)
            val top = chartBottom - barHeight
            canvas.drawRect(left, top, left + barWidth, chartBottom, barPaint)

            if (i < labels.size && shouldShowLabel(i, count, labelStride)) {
                canvas.drawText(labels[i], centerX, height - dp(6f), textPaint)
            }
        }
    }

    /** 根据图表宽度计算标签间隔，避免 30 日视图 x 轴文字重叠 */
    private fun computeLabelStride(count: Int): Int {
        if (count <= 7) return 1
        val sampleWidth = textPaint.measureText(labels.firstOrNull() ?: "00/00")
        val minSpacing = sampleWidth * 1.3f
        val maxVisible = (width / minSpacing).toInt().coerceIn(4, 8)
        return kotlin.math.ceil(count.toFloat() / maxVisible).toInt().coerceAtLeast(1)
    }

    private fun shouldShowLabel(index: Int, count: Int, stride: Int): Boolean {
        if (index % stride == 0) return true
        // 确保最后一天有标签（且与上一个标签间距足够）
        if (index == count - 1) {
            val prevLabeled = count - 1 - ((count - 1) % stride)
            return index - prevLabeled >= stride / 2 + 1
        }
        return false
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
