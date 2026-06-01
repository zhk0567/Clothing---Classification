package com.deepfashion.classifier

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.deepfashion.classifier.databinding.ActivityCategoryDetailBinding

class CategoryDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategoryDetailBinding
    private var categoryEn: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        categoryEn = intent.getStringExtra(EXTRA_CATEGORY_EN) ?: return finish()
        val isZh = resources.configuration.locales[0].language.startsWith("zh")
        val displayName = CategoryRepository.getDisplayName(categoryEn, isZh)
        val count = StatisticsRepository.countForCategory(this, displayName)
            .let { c ->
                val enCount = StatisticsRepository.countForCategory(this, categoryEn)
                maxOf(c, enCount)
            }

        binding.tvCategoryTitle.text = displayName
        binding.tvHistoryCount.text = getString(R.string.category_history_count, count)
        binding.tvDescription.text = CategoryRepository.getDescription(categoryEn, isZh)

        binding.btnStartRecognize.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.btnViewCategoryHistory.setOnClickListener {
            val intent = Intent(this, MainContainerActivity::class.java)
            intent.putExtra(MainContainerActivity.EXTRA_TAB, MainContainerActivity.TAB_HISTORY)
            intent.putExtra(MainContainerActivity.EXTRA_CATEGORY_FILTER, categoryEn)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
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

    companion object {
        const val EXTRA_CATEGORY_EN = "category_en"
    }
}
