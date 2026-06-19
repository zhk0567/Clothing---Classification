package com.deepfashion.classifier

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

object HistoryThumbnailCache {

    private val cache = object : LruCache<String, Bitmap>(4 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun get(path: String): Bitmap? = cache.get(path)

    fun decodeThumbnail(path: String, targetPx: Int = 112): Bitmap? {
        cache.get(path)?.let { return it }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > targetPx || bounds.outHeight / sample > targetPx) {
                sample *= 2
            }
            val bitmap = BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return null
            cache.put(path, bitmap)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    fun preload(paths: List<String>) {
        paths.forEach { path ->
            if (cache.get(path) == null) {
                decodeThumbnail(path)
            }
        }
    }
}
