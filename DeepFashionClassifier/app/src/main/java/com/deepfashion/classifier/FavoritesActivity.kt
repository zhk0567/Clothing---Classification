package com.deepfashion.classifier

import android.content.Intent
import android.os.Bundle
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.deepfashion.classifier.databinding.ActivityFavoritesBinding

class FavoritesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoritesBinding
    private lateinit var adapter: HistoryAdapter
    private var items: List<HistoryItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.favorites)

        items = HistoryRepository.loadAll(this).filter { it.isFavorite }
        adapter = HistoryAdapter(this, items)
        binding.listFavorites.adapter = adapter

        if (items.isEmpty()) {
            Toast.makeText(this, R.string.no_favorites, Toast.LENGTH_SHORT).show()
        }

        binding.listFavorites.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val item = items[pos]
            val intent = Intent(this, ResultActivity::class.java)
            intent.putExtra("category", item.category)
            intent.putExtra("confidence", item.confidence)
            intent.putExtra("description", getString(R.string.history_record))
            if (!item.imagePath.isNullOrBlank()) intent.putExtra("imagePath", item.imagePath)
            intent.putExtra("fromHistory", true)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    override fun onResume() {
        super.onResume()
        items = HistoryRepository.loadAll(this).filter { it.isFavorite }
        adapter.update(items)
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
