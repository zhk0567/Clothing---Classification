package com.deepfashion.classifier

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.deepfashion.classifier.databinding.ActivityExportBinding

class ExportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExportBinding

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        AlertDialog.Builder(this)
            .setTitle(R.string.import_mode_title)
            .setItems(arrayOf(getString(R.string.import_merge), getString(R.string.import_replace))) { _, which ->
                val replace = which == 1
                val ok = ExportHelper.importFromUri(this, uri, replace)
                android.widget.Toast.makeText(
                    this,
                    if (ok) R.string.import_success else R.string.import_failed,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                if (ok) refresh()
            }
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.export_data)

        refresh()

        binding.btnExportJsonl.setOnClickListener { ExportHelper.exportAllJsonl(this) }
        binding.btnExportCsv.setOnClickListener { ExportHelper.exportAllCsv(this) }
        binding.btnExportZip.setOnClickListener { ExportHelper.exportZipBackup(this) }
        binding.btnImportBackup.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "application/zip", "text/*", "*/*"))
        }
        binding.btnClearAll.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_history_title)
                .setMessage(R.string.clear_history_message)
                .setPositiveButton(R.string.clear) { _, _ ->
                    HistoryRepository.clearAll(this)
                    refresh()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun refresh() {
        val size = StatisticsRepository.getStorageSizeBytes(this)
        val count = HistoryRepository.loadAll(this, limit = Int.MAX_VALUE).size
        binding.tvStorageSize.text = getString(R.string.storage_size, StatisticsRepository.formatSize(size))
        binding.tvRecordCount.text = getString(R.string.record_count, count)
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
