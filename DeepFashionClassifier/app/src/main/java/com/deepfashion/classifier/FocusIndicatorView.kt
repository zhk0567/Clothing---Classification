package com.deepfashion.classifier

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation

class FocusIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFFFFEB3B.toInt() // yellow
    }
    
    private var focusRect = Rect()
    private var isShowing = false
    
    companion object {
        private const val SIZE = 100 // dp, will be converted to pixels
    }
    
    fun show(x: Float, y: Float) {
        val sizePx = (SIZE * resources.displayMetrics.density).toInt()
        focusRect.set(
            (x - sizePx / 2).toInt(),
            (y - sizePx / 2).toInt(),
            (x + sizePx / 2).toInt(),
            (y + sizePx / 2).toInt()
        )
        isShowing = true
        visibility = VISIBLE
        invalidate()
        
        // 淡出动画
        val fadeOut = AlphaAnimation(1f, 0f).apply {
            duration = 800
            startOffset = 500
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    isShowing = false
                    visibility = GONE
                }
                override fun onAnimationRepeat(animation: Animation?) {}
            })
        }
        startAnimation(fadeOut)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isShowing) {
            // 绘制对焦框（四个角的L形）
            val cornerLength = 20f * resources.displayMetrics.density
            // 左上角
            canvas.drawLine(
                focusRect.left.toFloat(), focusRect.top.toFloat(),
                focusRect.left + cornerLength, focusRect.top.toFloat(), paint
            )
            canvas.drawLine(
                focusRect.left.toFloat(), focusRect.top.toFloat(),
                focusRect.left.toFloat(), focusRect.top + cornerLength, paint
            )
            // 右上角
            canvas.drawLine(
                focusRect.right.toFloat(), focusRect.top.toFloat(),
                focusRect.right - cornerLength, focusRect.top.toFloat(), paint
            )
            canvas.drawLine(
                focusRect.right.toFloat(), focusRect.top.toFloat(),
                focusRect.right.toFloat(), focusRect.top + cornerLength, paint
            )
            // 左下角
            canvas.drawLine(
                focusRect.left.toFloat(), focusRect.bottom.toFloat(),
                focusRect.left + cornerLength, focusRect.bottom.toFloat(), paint
            )
            canvas.drawLine(
                focusRect.left.toFloat(), focusRect.bottom.toFloat(),
                focusRect.left.toFloat(), focusRect.bottom - cornerLength, paint
            )
            // 右下角
            canvas.drawLine(
                focusRect.right.toFloat(), focusRect.bottom.toFloat(),
                focusRect.right - cornerLength, focusRect.bottom.toFloat(), paint
            )
            canvas.drawLine(
                focusRect.right.toFloat(), focusRect.bottom.toFloat(),
                focusRect.right.toFloat(), focusRect.bottom - cornerLength, paint
            )
        }
    }
}

