package com.deepfashion.classifier

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.preference.PreferenceManager

object ShareImageHelper {

    fun drawWatermark(context: Context, bitmap: Bitmap, category: String, confidence: Float): Bitmap {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean("pref_share_watermark", true)) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val appName = context.getString(R.string.app_name)
        val text = "$appName · $category ${(confidence * 100).toInt()}%"
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (result.width * 0.035f).coerceAtLeast(28f)
        }
        val padding = paint.textSize * 0.6f
        val textWidth = paint.measureText(text)
        val barHeight = paint.textSize + padding * 2
        val bar = RectF(
            result.width - textWidth - padding * 2,
            result.height - barHeight - padding,
            result.width.toFloat() - padding,
            result.height.toFloat() - padding
        )
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 0, 0, 0)
        }
        canvas.drawRoundRect(bar, 8f, 8f, bgPaint)
        canvas.drawText(text, bar.left + padding, bar.bottom - padding, paint)
        return result
    }

    fun stitchCompareLongImage(
        context: Context,
        first: HistoryItem,
        second: HistoryItem,
        firstDesc: String,
        secondDesc: String
    ): Bitmap? {
        val bmp1 = first.imagePath?.let { android.graphics.BitmapFactory.decodeFile(it) }
        val bmp2 = second.imagePath?.let { android.graphics.BitmapFactory.decodeFile(it) }
        val width = 1080
        val imgH = 400
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 36f
        }
        val descPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 28f
        }
        val section1H = imgH + 180 + measureWrappedHeight(firstDesc, descPaint, width - 80)
        val section2H = imgH + 180 + measureWrappedHeight(secondDesc, descPaint, width - 80)
        val totalH = section1H + section2H + 40
        val result = Bitmap.createBitmap(width, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        var y = 40f
        y = drawSection(canvas, bmp1, first, firstDesc, width, imgH, textPaint, descPaint, y)
        canvas.drawLine(40f, y, width - 40f, y, Paint().apply { color = Color.LTGRAY; strokeWidth = 2f })
        y += 20f
        drawSection(canvas, bmp2, second, secondDesc, width, imgH, textPaint, descPaint, y)
        return drawWatermark(context, result, "${first.category} vs ${second.category}", 0f)
    }

    private fun drawSection(
        canvas: Canvas,
        image: Bitmap?,
        item: HistoryItem,
        desc: String,
        width: Int,
        imgH: Int,
        titlePaint: Paint,
        descPaint: Paint,
        startY: Float
    ): Float {
        var y = startY
        if (image != null) {
            val scaled = Bitmap.createScaledBitmap(image, width - 80, imgH, true)
            canvas.drawBitmap(scaled, 40f, y, null)
            if (scaled != image) scaled.recycle()
            y += imgH + 20f
        }
        canvas.drawText("${item.category} · ${(item.confidence * 100).toInt()}%", 40f, y + titlePaint.textSize, titlePaint)
        y += titlePaint.textSize + 16f
        canvas.drawText(item.time, 40f, y + descPaint.textSize, descPaint)
        y += descPaint.textSize + 20f
        y += drawWrappedText(canvas, desc, 40f, y, descPaint, width - 80)
        return y + 20f
    }

    private fun measureWrappedHeight(text: String, paint: Paint, maxWidth: Int): Int {
        var lines = 1
        var line = ""
        for (word in text.split(" ", "\n")) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(test) > maxWidth) {
                lines++
                line = word
            } else line = test
        }
        return (lines * paint.textSize * 1.3f).toInt()
    }

    private fun drawWrappedText(canvas: Canvas, text: String, x: Float, startY: Float, paint: Paint, maxWidth: Int): Float {
        var y = startY
        var line = ""
        for (word in text.split(" ", "\n")) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(test) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line, x, y + paint.textSize, paint)
                y += paint.textSize * 1.3f
                line = word
            } else line = test
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line, x, y + paint.textSize, paint)
            y += paint.textSize * 1.3f
        }
        return y - startY
    }
}
