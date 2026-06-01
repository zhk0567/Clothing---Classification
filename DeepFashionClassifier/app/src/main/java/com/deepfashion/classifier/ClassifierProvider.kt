package com.deepfashion.classifier

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.util.LinkedHashMap

object ClassifierProvider {

    private var classifier: DeepFashionClassifier? = null
    private val cache = object : LinkedHashMap<String, DeepFashionClassifier.ClassificationResult>(21, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DeepFashionClassifier.ClassificationResult>?): Boolean {
            return size > 20
        }
    }

    @Synchronized
    fun get(context: Context): DeepFashionClassifier {
        if (classifier == null) {
            classifier = DeepFashionClassifier(context.applicationContext)
        }
        return classifier!!
    }

    fun classifyImage(context: Context, bitmap: Bitmap, imagePath: String? = null): DeepFashionClassifier.ClassificationResult {
        val key = cacheKey(imagePath)
        if (key != null) {
            synchronized(cache) {
                cache[key]?.let { return it }
            }
        }
        val result = get(context).classifyImage(bitmap)
        if (key != null) {
            synchronized(cache) { cache[key] = result }
        }
        return result
    }

    fun classifyImageTopK(
        context: Context,
        bitmap: Bitmap,
        k: Int = 3,
        @Suppress("UNUSED_PARAMETER") imagePath: String? = null
    ): List<DeepFashionClassifier.ClassificationResult> {
        return get(context).classifyImageTopK(bitmap, k)
    }

    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    private fun cacheKey(imagePath: String?): String? {
        if (imagePath.isNullOrBlank()) return null
        val file = File(imagePath)
        if (!file.exists()) return null
        return "${file.absolutePath}:${file.lastModified()}"
    }
}
