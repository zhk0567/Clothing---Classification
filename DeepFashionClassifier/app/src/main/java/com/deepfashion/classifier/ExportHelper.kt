package com.deepfashion.classifier

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ExportHelper {

    fun exportAllJsonl(context: Context): Boolean {
        val content = HistoryRepository.exportJsonl(context)
        if (content.isBlank()) {
            Toast.makeText(context, R.string.no_data_to_export, Toast.LENGTH_SHORT).show()
            return false
        }
        return saveToDownloads(context, "history_export.jsonl", content, "application/json")
    }

    fun exportAllCsv(context: Context): Boolean {
        val content = HistoryRepository.exportCsv(context)
        if (content.lines().size <= 1) {
            Toast.makeText(context, R.string.no_data_to_export, Toast.LENGTH_SHORT).show()
            return false
        }
        return saveToDownloads(context, "history_export.csv", content, "text/csv")
    }

    fun exportZipBackup(context: Context): Boolean {
        val jsonl = HistoryRepository.exportJsonl(context)
        if (jsonl.isBlank()) {
            Toast.makeText(context, R.string.no_data_to_export, Toast.LENGTH_SHORT).show()
            return false
        }
        return try {
            val cacheFile = File(context.cacheDir, "backup_${System.currentTimeMillis()}.zip")
            ZipOutputStream(FileOutputStream(cacheFile)).use { zos ->
                zos.putNextEntry(ZipEntry("history.jsonl"))
                zos.write(jsonl.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                val imagesDir = File(context.filesDir, "images")
                if (imagesDir.exists()) {
                    imagesDir.listFiles()?.forEach { img ->
                        if (img.isFile) {
                            zos.putNextEntry(ZipEntry("images/${img.name}"))
                            FileInputStream(img).use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
            }
            val bytes = cacheFile.readBytes()
            cacheFile.delete()
            saveBytesToDownloads(context, "deepfashion_backup.zip", bytes, "application/zip")
        } catch (_: Exception) {
            Toast.makeText(context, R.string.export_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun importFromUri(context: Context, uri: Uri, replace: Boolean): Boolean {
        return try {
            val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            } ?: ""
            val input = context.contentResolver.openInputStream(uri) ?: return false
            if (name.endsWith(".zip", ignoreCase = true)) {
                importZip(context, input, replace)
            } else {
                val content = input.bufferedReader(Charsets.UTF_8).readText()
                val count = if (replace) {
                    HistoryRepository.replaceFromJsonl(context, content)
                } else {
                    HistoryRepository.mergeFromJsonl(context, content)
                }
                count > 0
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun importZip(context: Context, input: java.io.InputStream, replace: Boolean): Boolean {
        if (replace) HistoryRepository.clearAll(context)
        var jsonlContent = StringBuilder()
        val imagesDir = File(context.filesDir, "images")
        if (!imagesDir.exists()) imagesDir.mkdirs()
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "history.jsonl" || entry.name.endsWith("history.jsonl")) {
                    jsonlContent = StringBuilder(zis.readBytes().toString(Charsets.UTF_8))
                } else if (entry.name.startsWith("images/") && !entry.isDirectory) {
                    val outFile = File(imagesDir, File(entry.name).name)
                    FileOutputStream(outFile).use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        if (jsonlContent.isEmpty()) return false
        val count = if (replace) {
            HistoryRepository.replaceFromJsonl(context, jsonlContent.toString())
        } else {
            HistoryRepository.mergeFromJsonl(context, jsonlContent.toString())
        }
        return count > 0
    }

    fun exportSelected(context: Context, items: List<HistoryItem>): Boolean {
        val sb = StringBuilder("time,category,confidence,imagePath,isFavorite\n")
        items.forEach { item ->
            sb.append("\"${item.time}\",\"${item.category}\",${item.confidence},\"${item.imagePath ?: ""}\",${item.isFavorite}\n")
        }
        return saveToDownloads(context, "history_selected.csv", sb.toString(), "text/csv")
    }

    private fun saveToDownloads(context: Context, fileName: String, content: String, mimeType: String): Boolean {
        return saveBytesToDownloads(context, fileName, content.toByteArray(Charsets.UTF_8), mimeType)
    }

    private fun saveBytesToDownloads(context: Context, fileName: String, bytes: ByteArray, mimeType: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    Toast.makeText(context, R.string.export_success, Toast.LENGTH_SHORT).show()
                    true
                } else false
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { it.write(bytes) }
                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                intent.data = Uri.fromFile(file)
                context.sendBroadcast(intent)
                Toast.makeText(context, R.string.export_success, Toast.LENGTH_SHORT).show()
                true
            }
        } catch (_: Exception) {
            Toast.makeText(context, R.string.export_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }
}
