package com.deepfashion.classifier

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.deepfashion.classifier.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        loadSummary()
    }

    private fun loadSummary() {
        val ctx = requireContext()
        val summary = StatisticsRepository.getSummary(ctx)
        binding.tvWeekStats.text = getString(R.string.home_week_count, summary.weekCount)
        binding.tvTopCategory.text = if (summary.topCategory != null) {
            getString(R.string.home_top_category, summary.topCategory, summary.topCategoryCount)
        } else {
            getString(R.string.home_no_history)
        }

        binding.recentContainer.removeAllViews()
        val recent = HistoryRepository.loadAll(ctx, limit = 3)
        if (recent.isEmpty()) {
            val tv = TextView(ctx)
            tv.text = getString(R.string.home_no_history)
            binding.recentContainer.addView(tv)
        } else {
            recent.forEach { item ->
                val tv = TextView(ctx)
                tv.text = "${item.category} · ${(item.confidence * 100).toInt()}% · ${item.time}"
                tv.setPadding(0, 8, 0, 8)
                binding.recentContainer.addView(tv)
            }
        }

        binding.btnQuickCamera.setOnClickListener {
            startActivity(Intent(ctx, CameraActivity::class.java))
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
