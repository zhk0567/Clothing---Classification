package com.deepfashion.classifier

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class StatisticsSummary(
    val totalCount: Int,
    val weekCount: Int,
    val avgConfidence: Float,
    val topCategory: String?,
    val topCategoryCount: Int,
    val favoriteCount: Int
)

data class DailyCount(val dateLabel: String, val count: Int)

data class CategoryCount(val category: String, val count: Int)

object StatisticsRepository {

    fun getSummary(context: Context): StatisticsSummary {
        val items = HistoryRepository.loadAll(context, limit = Int.MAX_VALUE)
        if (items.isEmpty()) {
            return StatisticsSummary(0, 0, 0f, null, 0, 0)
        }
        val weekItems = items.filter { isWithinWeek(it.time) }
        val avgConf = items.map { it.confidence }.average().toFloat()
        val categoryCounts = getCategoryCounts(context)
        val top = categoryCounts.maxByOrNull { it.count }
        return StatisticsSummary(
            totalCount = items.size,
            weekCount = weekItems.size,
            avgConfidence = avgConf,
            topCategory = top?.category,
            topCategoryCount = top?.count ?: 0,
            favoriteCount = items.count { it.isFavorite }
        )
    }

    fun getCategoryCounts(context: Context): List<CategoryCount> {
        val items = HistoryRepository.loadAll(context, limit = Int.MAX_VALUE)
        return items.groupBy { it.category }
            .map { (cat, list) -> CategoryCount(cat, list.size) }
            .sortedByDescending { it.count }
    }

    fun getDailyCounts(context: Context, days: Int): List<DailyCount> {
        val items = HistoryRepository.loadAll(context, limit = Int.MAX_VALUE)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val labelSdf = SimpleDateFormat("MM/dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val result = mutableListOf<DailyCount>()
        for (i in days - 1 downTo 0) {
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dayStr = sdf.format(cal.time)
            val label = labelSdf.format(cal.time)
            val count = items.count { item ->
                try {
                    val itemDay = sdf.format(
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(item.time) ?: Date()
                    )
                    itemDay == dayStr
                } catch (_: Exception) {
                    false
                }
            }
            result.add(DailyCount(label, count))
        }
        return result
    }

    fun countForCategory(context: Context, categoryDisplay: String): Int {
        val items = HistoryRepository.loadAll(context, limit = Int.MAX_VALUE)
        return items.count { it.category == categoryDisplay }
    }

    fun getStorageSizeBytes(context: Context): Long {
        var size = 0L
        val historyFile = File(context.filesDir, "history.jsonl")
        if (historyFile.exists()) size += historyFile.length()
        val imagesDir = File(context.filesDir, "images")
        if (imagesDir.exists()) {
            imagesDir.walkTopDown().filter { it.isFile }.forEach { size += it.length() }
        }
        return size
    }

    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    private fun isWithinWeek(timeStr: String): Boolean {
        return try {
            val itemDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(timeStr) ?: return false
            val now = Date()
            val diff = now.time - itemDate.time
            diff >= 0 && diff <= 7 * 24 * 60 * 60 * 1000L
        } catch (_: Exception) {
            false
        }
    }
}
