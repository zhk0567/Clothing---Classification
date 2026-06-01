package com.deepfashion.classifier

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.deepfashion.classifier.databinding.ActivityCompareBinding

class CompareActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompareBinding
    private var first: HistoryItem? = null
    private var second: HistoryItem? = null
    private val allItems by lazy { HistoryRepository.loadAll(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompareBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.compare)

        binding.btnPickFirst.setOnClickListener { pickRecord(true) }
        binding.btnPickSecond.setOnClickListener { pickRecord(false) }
        binding.btnGenerateLongImage.setOnClickListener { generateLongImage() }
    }

    private fun pickRecord(isFirst: Boolean) {
        if (allItems.isEmpty()) {
            Toast.makeText(this, R.string.home_no_history, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = allItems.map { "${it.category} · ${it.time}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(if (isFirst) R.string.pick_first_record else R.string.pick_second_record)
            .setItems(labels) { _, which ->
                val item = allItems[which]
                if (isFirst) {
                    first = item
                    showItem(binding.tvFirst, binding.imgFirst, binding.tvFirstDesc, item)
                } else {
                    second = item
                    showItem(binding.tvSecond, binding.imgSecond, binding.tvSecondDesc, item)
                }
                updateSummary()
            }
            .show()
    }

    private fun showItem(
        tv: android.widget.TextView,
        img: android.widget.ImageView,
        descTv: android.widget.TextView,
        item: HistoryItem
    ) {
        tv.text = "${item.category}\n${(item.confidence * 100).toInt()}%\n${item.time}"
        if (!item.imagePath.isNullOrBlank()) {
            img.setImageBitmap(BitmapFactory.decodeFile(item.imagePath))
        }
        val isZh = resources.configuration.locales[0].language.startsWith("zh")
        val categoryEn = CategoryRepository.getEnglishName(item.category) ?: item.category
        descTv.text = CategoryRepository.getDescription(categoryEn, isZh)
    }

    private fun updateSummary() {
        val a = first
        val b = second
        if (a == null || b == null) {
            binding.tvCompareSummary.visibility = View.GONE
            binding.btnGenerateLongImage.visibility = View.GONE
            return
        }
        binding.tvCompareSummary.visibility = View.VISIBLE
        val sameCategory = a.category == b.category
        val diffPct = ((b.confidence - a.confidence) * 100).toInt()
        val diffStr = when {
            diffPct > 0 -> "+$diffPct%"
            diffPct < 0 -> "$diffPct%"
            else -> "0%"
        }
        val categoryMsg = if (sameCategory) {
            getString(R.string.compare_same_category)
        } else {
            "${a.category} vs ${b.category}"
        }
        binding.tvCompareSummary.text = "$categoryMsg\n${getString(R.string.compare_confidence_diff, diffStr)}"
        binding.btnGenerateLongImage.visibility = View.VISIBLE
    }

    private fun generateLongImage() {
        val a = first ?: return
        val b = second ?: return
        val isZh = resources.configuration.locales[0].language.startsWith("zh")
        val descA = CategoryRepository.getDescription(CategoryRepository.getEnglishName(a.category) ?: a.category, isZh)
        val descB = CategoryRepository.getDescription(CategoryRepository.getEnglishName(b.category) ?: b.category, isZh)
        val bitmap = ShareImageHelper.stitchCompareLongImage(this, a, b, descA, descB) ?: run {
            Toast.makeText(this, R.string.image_load_failed, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val cacheDir = java.io.File(cacheDir, "share")
            cacheDir.mkdirs()
            val tempFile = java.io.File(cacheDir, "compare_${System.currentTimeMillis()}.png")
            tempFile.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "${applicationContext.packageName}.fileprovider", tempFile
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(intent, getString(R.string.long_image_share)))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show()
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
