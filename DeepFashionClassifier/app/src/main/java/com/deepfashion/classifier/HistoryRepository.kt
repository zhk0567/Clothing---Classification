package com.deepfashion.classifier

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryItem(
    val time: String,
    val category: String,
    val confidence: Float,
    val imagePath: String?,
    val isFavorite: Boolean = false
)

object HistoryRepository {
    private const val FILE_NAME = "history.jsonl"

    private fun historyFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    fun addEntry(context: Context, category: String, confidence: Float, imagePath: String?): HistoryItem? {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timeStr = sdf.format(Date())
        val record = JSONObject().apply {
            put("time", timeStr)
            put("category", category)
            put("confidence", confidence)
            if (imagePath != null) put("imagePath", imagePath)
        }
        val file = historyFile(context)
        file.appendText(record.toString() + "\n", Charsets.UTF_8)
        return HistoryItem(
            time = timeStr,
            category = category,
            confidence = confidence,
            imagePath = imagePath,
            isFavorite = false
        )
    }

    fun loadAll(context: Context, limit: Int = 200): List<HistoryItem> {
        val file = historyFile(context)
        if (!file.exists()) return emptyList()
        val lines = file.readLines(Charsets.UTF_8).asReversed()
        val items = ArrayList<HistoryItem>()
        for (line in lines) {
            if (line.isBlank()) continue
            try {
                val obj = JSONObject(line)
                items.add(
                    HistoryItem(
                        time = obj.optString("time", ""),
                        category = obj.optString("category", ""),
                        confidence = obj.optDouble("confidence", 0.0).toFloat(),
                        imagePath = obj.optString("imagePath").takeIf { it.isNotEmpty() },
                        isFavorite = obj.optBoolean("isFavorite", false)
                    )
                )
                if (items.size >= limit) break
            } catch (_: Exception) { }
        }
        return items
    }

    fun toggleFavorite(context: Context, target: HistoryItem): Boolean {
        val file = historyFile(context)
        if (!file.exists()) return false
        val lines = file.readLines(Charsets.UTF_8)
        val updated = lines.map { line ->
            try {
                val obj = JSONObject(line)
                val same = obj.optString("time", "") == target.time &&
                        obj.optString("category", "") == target.category &&
                        Math.abs(obj.optDouble("confidence", 0.0) - target.confidence) < 1e-6 &&
                        obj.optString("imagePath").takeIf { it.isNotEmpty() } == target.imagePath
                if (same) {
                    val newObj = JSONObject(line)
                    newObj.put("isFavorite", !target.isFavorite)
                    return@map newObj.toString()
                }
            } catch (_: Exception) { }
            line
        }
        file.writeText(updated.joinToString("\n"), Charsets.UTF_8)
        return !target.isFavorite
    }

    fun deleteEntry(context: Context, target: HistoryItem) {
        val file = historyFile(context)
        if (!file.exists()) return
        val lines = file.readLines(Charsets.UTF_8)
        val kept = lines.filter { line ->
            try {
                val obj = JSONObject(line)
                val same = obj.optString("time", "") == target.time &&
                        obj.optString("category", "") == target.category &&
                        Math.abs(obj.optDouble("confidence", 0.0) - target.confidence) < 1e-6 &&
                        obj.optString("imagePath").takeIf { it.isNotEmpty() } == target.imagePath
                !same
            } catch (e: Exception) { true }
        }
        file.writeText(kept.joinToString("\n"), Charsets.UTF_8)
        // 删除图片文件（若存在且位于应用目录）
        try {
            if (!target.imagePath.isNullOrBlank()) {
                val img = File(target.imagePath)
                if (img.exists() && img.absolutePath.startsWith(context.filesDir.absolutePath)) {
                    img.delete()
                }
            }
        } catch (_: Exception) { }
    }

    fun clearAll(context: Context) {
        val file = historyFile(context)
        if (file.exists()) file.delete()
        // 清空内部图片目录
        val imagesDir = File(context.filesDir, "images")
        if (imagesDir.exists()) {
            imagesDir.deleteRecursively()
        }
    }
}


