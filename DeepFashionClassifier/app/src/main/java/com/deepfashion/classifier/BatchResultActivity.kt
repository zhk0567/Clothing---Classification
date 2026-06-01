package com.deepfashion.classifier

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.deepfashion.classifier.databinding.ActivityBatchResultBinding

class BatchResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBatchResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBatchResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.batch_results)

        val items = BatchResultStore.items
        val display = items.map { "${it.category} · ${(it.confidence * 100).toInt()}%" }
        binding.listBatchResults.adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, display) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setPadding(24, 24, 24, 24)
                return v
            }
        }
        binding.listBatchResults.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            openResult(items[pos])
        }

        binding.btnSaveAll.setOnClickListener {
            if (items.isEmpty()) return@setOnClickListener
            binding.btnSaveAll.isEnabled = false
            var saved = 0
            items.forEach { item ->
                HistoryRepository.addEntry(this, item.category, item.confidence, item.imagePath)
                saved++
            }
            Toast.makeText(this, getString(R.string.batch_save_success, saved), Toast.LENGTH_SHORT).show()
            binding.btnSaveAll.isEnabled = true
        }
    }

    private fun openResult(item: BatchResultItem) {
        val intent = Intent(this, ResultActivity::class.java)
        intent.putExtra("category", item.category)
        intent.putExtra("confidence", item.confidence)
        intent.putExtra("description", item.description)
        intent.putExtra("imagePath", item.imagePath)
        intent.putExtra("fromHistory", false)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
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
