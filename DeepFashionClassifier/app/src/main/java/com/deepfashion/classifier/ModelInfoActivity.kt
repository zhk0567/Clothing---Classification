package com.deepfashion.classifier

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.deepfashion.classifier.databinding.ActivityModelInfoBinding

class ModelInfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityModelInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.model_info)

        binding.tvModelInfo.text = getString(
            R.string.model_info_content,
            "1.2",
            "50",
            "61.55",
            "224",
            ClassifierProvider.get(this).getThreadCount().toString()
        )
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
