package com.deepfashion.classifier

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.deepfashion.classifier.databinding.ActivityBatchClassifyBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

class BatchClassifyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBatchClassifyBinding

    private val multiPicker = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) processImages(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBatchClassifyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.batch_classify)
        binding.btnSelectImages.setOnClickListener { multiPicker.launch("image/*") }
    }

    private fun processImages(uris: List<Uri>) {
        binding.btnSelectImages.isEnabled = false
        binding.progressBar.max = uris.size
        binding.progressBar.progress = 0
        lifecycleScope.launch {
            val results = withContext(Dispatchers.Default) {
                val list = mutableListOf<BatchResultItem>()
                uris.forEachIndexed { index, uri ->
                    try {
                        val imagesDir = java.io.File(filesDir, "images")
                        if (!imagesDir.exists()) imagesDir.mkdirs()
                        val target = java.io.File(imagesDir, "batch_${System.currentTimeMillis()}_$index.jpg")
                        val input: InputStream? = contentResolver.openInputStream(uri)
                        input?.use { it.copyTo(target.outputStream()) }
                        val bitmap = BitmapFactory.decodeFile(target.absolutePath)
                        if (bitmap != null) {
                            val r = ClassifierProvider.classifyImage(this@BatchClassifyActivity, bitmap, target.absolutePath)
                            list.add(BatchResultItem(target.absolutePath, r.category, r.confidence, r.description))
                        }
                    } catch (_: Exception) { }
                    withContext(Dispatchers.Main) {
                        binding.progressBar.progress = index + 1
                        binding.tvProgress.text = getString(R.string.batch_progress, index + 1, uris.size)
                    }
                }
                list
            }
            BatchResultStore.items = results
            if (results.isEmpty()) {
                Toast.makeText(this@BatchClassifyActivity, R.string.classify_failed, Toast.LENGTH_SHORT).show()
                binding.btnSelectImages.isEnabled = true
            } else {
                startActivity(Intent(this@BatchClassifyActivity, BatchResultActivity::class.java))
                finish()
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
