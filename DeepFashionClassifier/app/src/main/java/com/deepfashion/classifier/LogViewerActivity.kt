package com.deepfashion.classifier

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.deepfashion.classifier.databinding.ActivityLogViewerBinding
import java.io.File

class LogViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogViewerBinding
    private var logFiles: List<File> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.crash_logs)

        binding.btnClearLogs.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_logs)
                .setMessage(R.string.clear_history_message)
                .setPositiveButton(R.string.clear) { _, _ ->
                    logsDir().listFiles()?.forEach { it.delete() }
                    refreshList()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        refreshList()
    }

    private fun logsDir(): File {
        val dir = File(filesDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun refreshList() {
        logFiles = logsDir().listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        if (logFiles.isEmpty()) {
            binding.tvEmpty.visibility = android.view.View.VISIBLE
            binding.listLogs.visibility = android.view.View.GONE
        } else {
            binding.tvEmpty.visibility = android.view.View.GONE
            binding.listLogs.visibility = android.view.View.VISIBLE
            binding.listLogs.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, logFiles.map { it.name })
            binding.listLogs.setOnItemClickListener { _, _, position, _ ->
                val content = logFiles[position].readText(Charsets.UTF_8)
                AlertDialog.Builder(this)
                    .setTitle(logFiles[position].name)
                    .setMessage(content)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNegativeButton(R.string.delete_log) { _, _ ->
                        logFiles[position].delete()
                        refreshList()
                    }
                    .show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        return true
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }
}
