package com.deepfashion.classifier

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.util.concurrent.CopyOnWriteArrayList

data class DetectionBox(
    val rect: RectF,
    val score: Float
)

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val boxes = CopyOnWriteArrayList<DetectionBox>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0xFFFFC107.toInt() // amber
    }

    fun setBoxes(newBoxes: List<DetectionBox>) {
        boxes.clear()
        boxes.addAll(newBoxes)
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (b in boxes) {
            canvas.drawRoundRect(b.rect, 12f, 12f, paint)
        }
    }
}


