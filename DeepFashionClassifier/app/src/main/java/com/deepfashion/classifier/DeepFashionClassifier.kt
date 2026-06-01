package com.deepfashion.classifier

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.*
import androidx.preference.PreferenceManager
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DeepFashionClassifier(private val context: Context) {

    private var ortSession: OrtSession? = null
    private val inputSize = 224
    private val numClasses = 50
    private var lastPerfThreadsEnabled: Boolean? = null

    private val categories = CategoryRepository.englishNames

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val ortEnv = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open("models/deepfashion_classifier.onnx").readBytes()
            val sessionOptions = OrtSession.SessionOptions()
            val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val perfThreads = prefs.getBoolean("pref_performance_threads", true)
            if (perfThreads) {
                val threads = computeThreadCount()
                try { sessionOptions.setIntraOpNumThreads(threads) } catch (_: Throwable) { }
                try { sessionOptions.setInterOpNumThreads(threads) } catch (_: Throwable) { }
            } else {
                try { sessionOptions.setIntraOpNumThreads(1) } catch (_: Throwable) { }
                try { sessionOptions.setInterOpNumThreads(1) } catch (_: Throwable) { }
            }
            ortSession = ortEnv.createSession(modelBytes, sessionOptions)
            lastPerfThreadsEnabled = perfThreads
        } catch (e: Exception) {
            Log.e("DeepFashionClassifier", "模型加载失败", e)
            throw RuntimeException("无法加载DeepFashion模型", e)
        }
    }

    private fun ensureSessionUpToDate() {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val perfThreads = prefs.getBoolean("pref_performance_threads", true)
        val needReload = (lastPerfThreadsEnabled == null) || (lastPerfThreadsEnabled != perfThreads) || (ortSession == null)
        if (needReload) {
            try { ortSession?.close() } catch (_: Exception) { }
            ortSession = null
            loadModel()
        }
    }

    private fun isZh(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return (prefs.getString("pref_language", "zh") ?: "zh") == "zh"
    }

    fun classifyImage(bitmap: Bitmap): ClassificationResult {
        return classifyImageTopK(bitmap, 1).first()
    }

    fun classifyImageTopK(bitmap: Bitmap, k: Int = 3): List<ClassificationResult> {
        ensureSessionUpToDate()
        val session = ortSession ?: throw RuntimeException("模型未加载")
        var inputTensor: OnnxTensor? = null
        var outputTensor: OnnxTensor? = null
        try {
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val inputArray = bitmapToFloatArray(resizedBitmap)
            val inputBuffer = ByteBuffer.allocateDirect(inputArray.size * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
            inputBuffer.put(inputArray)
            inputBuffer.rewind()
            val ortEnv = OrtEnvironment.getEnvironment()
            val inputName = session.inputNames.iterator().next()
            inputTensor = OnnxTensor.createTensor(ortEnv, inputBuffer, longArrayOf(1, 3, 224, 224))
            val inputs = mutableMapOf<String, OnnxTensorLike>()
            inputs[inputName] = inputTensor
            val outputs = session.run(inputs)
            val outputName = session.outputNames.iterator().next()
            val firstOutput: OnnxValue = try {
                outputs.get(outputName).orElse(outputs.get(0))
            } catch (_: Exception) {
                outputs.get(0)
            }
            outputTensor = (firstOutput as? OnnxTensor) ?: throw RuntimeException("输出格式错误")
            val outputBuffer = outputTensor.floatBuffer
            outputBuffer.rewind()
            val probabilities = FloatArray(numClasses)
            outputBuffer.get(probabilities)
            outputs.close()
            inputTensor.close()
            outputTensor.close()
            inputTensor = null
            outputTensor = null

            val probs = softmax(probabilities)
            val topIndices = probs.indices.sortedByDescending { probs[it] }.take(k.coerceAtMost(numClasses))
            val isZh = isZh()
            return topIndices.map { idx ->
                val categoryEn = if (idx < categories.size) categories[idx] else "Unknown"
                val category = CategoryRepository.getDisplayName(categoryEn, isZh)
                ClassificationResult(
                    category = category,
                    confidence = probs[idx],
                    description = CategoryRepository.getDescription(categoryEn, isZh),
                    categoryEn = categoryEn
                )
            }
        } catch (e: Exception) {
            try {
                inputTensor?.close()
                outputTensor?.close()
            } catch (_: Exception) { }
            Log.e("DeepFashionClassifier", "分类失败", e)
            throw RuntimeException("分类失败", e)
        }
    }

    private fun bitmapToFloatArray(bitmap: Bitmap): FloatArray {
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        val floatBuffer = FloatArray(3 * inputSize * inputSize)
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16 and 0xFF) / 255.0f
            val g = (pixel shr 8 and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            floatBuffer[i] = (r - mean[0]) / std[0]
            floatBuffer[inputSize * inputSize + i] = (g - mean[1]) / std[1]
            floatBuffer[2 * inputSize * inputSize + i] = (b - mean[2]) / std[2]
        }
        return floatBuffer
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: 0f
        val exp = logits.map { kotlin.math.exp(it - max) }
        val sum = exp.sum()
        return exp.map { (it / sum).toFloat() }.toFloatArray()
    }

    fun getThreadCount(): Int {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return if (prefs.getBoolean("pref_performance_threads", true)) computeThreadCount() else 1
    }

    private fun computeThreadCount(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return maxOf(2, cores / 2)
    }

    fun close() {
        ortSession?.close()
        ortSession = null
    }

    data class ClassificationResult(
        val category: String,
        val confidence: Float,
        val description: String,
        val categoryEn: String = category
    )
}
