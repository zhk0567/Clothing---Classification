package com.deepfashion.classifier

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.preference.PreferenceManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

object DemoDataSeeder {

    private const val PREF_DEMO_SEEDED = "pref_demo_data_seeded"

    private data class DemoSpec(
        val daysAgo: Int,
        val hour: Int,
        val minute: Int,
        val english: String,
        val confidence: Float,
        val favorite: Boolean
    )

    private val specs = listOf(
        DemoSpec(0, 9, 12, "Tee", 0.93f, false),
        DemoSpec(0, 14, 35, "Jeans", 0.89f, true),
        DemoSpec(1, 10, 5, "Dress", 0.91f, true),
        DemoSpec(1, 18, 42, "Hoodie", 0.87f, true),
        DemoSpec(2, 11, 20, "Skirt", 0.85f, false),
        DemoSpec(3, 15, 8, "Blazer", 0.88f, true),
        DemoSpec(4, 8, 55, "Sweater", 0.90f, false),
        DemoSpec(5, 16, 30, "Shorts", 0.82f, false),
        DemoSpec(6, 12, 18, "Jacket", 0.86f, false),
        DemoSpec(7, 19, 45, "Cardigan", 0.84f, false),
        DemoSpec(9, 13, 10, "Chinos", 0.83f, false),
        DemoSpec(10, 9, 40, "Tank", 0.79f, false),
        DemoSpec(12, 17, 22, "Blouse", 0.88f, true),
        DemoSpec(14, 10, 15, "Joggers", 0.81f, false),
        DemoSpec(16, 14, 50, "Coat", 0.92f, false),
        DemoSpec(18, 11, 33, "Sundress", 0.87f, false),
        DemoSpec(20, 16, 5, "Bomber", 0.85f, false),
        DemoSpec(22, 8, 28, "Leggings", 0.80f, false),
        DemoSpec(25, 15, 12, "Button-Down", 0.86f, false),
        DemoSpec(28, 13, 48, "Parka", 0.91f, false),
        DemoSpec(29, 20, 10, "Romper", 0.83f, false),
        DemoSpec(29, 20, 55, "Peacoat", 0.88f, false)
    )

    fun seedIfEmpty(context: Context) {
        if (HistoryRepository.loadAll(context, limit = 1).isNotEmpty()) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getBoolean(PREF_DEMO_SEEDED, false)) return
        val count = seed(context, replace = false)
        if (count > 0) {
            prefs.edit().putBoolean(PREF_DEMO_SEEDED, true).apply()
        }
    }

    fun hasDemoData(context: Context): Boolean {
        return HistoryRepository.loadAll(context, limit = Int.MAX_VALUE)
            .any { it.imagePath?.contains("/demo_") == true }
    }

    /** @return number of records inserted */
    fun seed(context: Context, replace: Boolean): Int {
        if (replace) {
            HistoryRepository.deleteDemoEntries(context)
        } else if (hasDemoData(context)) {
            return 0
        }
        val isZh = isZh(context)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val cal = Calendar.getInstance()
        var index = 1
        specs.forEach { spec ->
            cal.timeInMillis = System.currentTimeMillis()
            cal.add(Calendar.DAY_OF_YEAR, -spec.daysAgo)
            cal.set(Calendar.HOUR_OF_DAY, spec.hour)
            cal.set(Calendar.MINUTE, spec.minute)
            cal.set(Calendar.SECOND, Random.nextInt(0, 59))
            val time = sdf.format(cal.time)
            val category = CategoryRepository.getDisplayName(spec.english, isZh)
            val imagePath = createDemoImage(context, index, category, spec.english)
            HistoryRepository.appendEntry(
                context, time, category, spec.confidence, imagePath, spec.favorite
            )
            index++
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(PREF_DEMO_SEEDED, true)
            .apply()
        return specs.size
    }

    fun clearDemo(context: Context): Int {
        return HistoryRepository.deleteDemoEntries(context)
    }

    private fun isZh(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return (prefs.getString("pref_language", "zh") ?: "zh") == "zh"
    }

    private fun createDemoImage(context: Context, index: Int, label: String, english: String): String {
        val w = 540
        val h = 720
        val baseColor = categoryColor(english)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(baseColor)

        val garmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 200
        }
        canvas.drawRoundRect(RectF(w * 0.18f, h * 0.22f, w * 0.82f, h * 0.72f), 32f, 32f, garmentPaint)

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shiftColor(baseColor, 0.75f)
            alpha = 160
        }
        canvas.drawRoundRect(RectF(w * 0.28f, h * 0.30f, w * 0.72f, h * 0.62f), 20f, 20f, accentPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f
            textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 0f, 2f, 0x66000000)
        }
        canvas.drawText(label, w / 2f, h * 0.90f, textPaint)

        val dir = File(context.filesDir, "images")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "demo_${"%03d".format(index)}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bitmap.recycle()
        return file.absolutePath
    }

    private fun categoryColor(english: String): Int {
        val hash = english.hashCode()
        val hue = (hash and 0xFFFF) % 360
        return Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.45f, 0.82f))
    }

    private fun shiftColor(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * factor).coerceIn(0.2f, 1f)
        return Color.HSVToColor(hsv)
    }
}
