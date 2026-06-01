package com.deepfashion.classifier

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            saveCrashLog(thread, throwable)
        } catch (_: Exception) { }
        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun saveCrashLog(thread: Thread, throwable: Throwable) {
        val logsDir = File(context.filesDir, "logs")
        if (!logsDir.exists()) logsDir.mkdirs()
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val file = File(logsDir, "crash_${sdf.format(Date())}.txt")
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val content = buildString {
            appendLine("=== Crash Report ===")
            appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("Thread: ${thread.name}")
            appendLine("App: ${context.packageName}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            append(sw.toString())
        }
        file.writeText(content, Charsets.UTF_8)
    }

    companion object {
        fun install(context: Context) {
            val appContext = context.applicationContext
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current !is CrashHandler) {
                Thread.setDefaultUncaughtExceptionHandler(CrashHandler(appContext, current))
            }
        }
    }
}
