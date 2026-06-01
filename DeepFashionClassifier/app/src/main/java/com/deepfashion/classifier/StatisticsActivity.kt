package com.deepfashion.classifier

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.deepfashion.classifier.databinding.ActivityStatisticsBinding

class StatisticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatisticsBinding
    private var trendDays = 7

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.statistics)

        val summary = StatisticsRepository.getSummary(this)
        binding.tvTotalCount.text = getString(R.string.stat_total, summary.totalCount)
        binding.tvWeekCount.text = getString(R.string.stat_week, summary.weekCount)
        binding.tvAvgConfidence.text = getString(R.string.stat_avg_confidence, (summary.avgConfidence * 100).toInt())

        val categoryCounts = StatisticsRepository.getCategoryCounts(this)
        val least = categoryCounts.minByOrNull { it.count }
        binding.tvLeastCategory.text = if (least != null && least.count > 0) {
            getString(R.string.stat_least_category, least.category, least.count)
        } else {
            getString(R.string.home_no_history)
        }

        refreshTrendChart()
        refreshPieChart(categoryCounts)

        binding.chip7Days.setOnClickListener { selectTrendDays(7) }
        binding.chip30Days.setOnClickListener { selectTrendDays(30) }
    }

    private fun selectTrendDays(days: Int) {
        trendDays = days
        binding.chip7Days.isChecked = days == 7
        binding.chip30Days.isChecked = days == 30
        binding.tvTrendTitle.text = if (days == 7) getString(R.string.trend_7_days) else getString(R.string.trend_30_days)
        refreshTrendChart()
    }

    private fun refreshTrendChart() {
        val daily = StatisticsRepository.getDailyCounts(this, trendDays)
        binding.chartTrend.setData(daily.map { it.dateLabel }, daily.map { it.count })
    }

    private fun refreshPieChart(categoryCounts: List<CategoryCount>) {
        val top = categoryCounts.take(6)
        val otherCount = categoryCounts.drop(6).sumOf { it.count }
        val labels = top.map { it.category.take(8) }.toMutableList()
        val values = top.map { it.count }.toMutableList()
        if (otherCount > 0) {
            labels.add(getString(R.string.other_categories))
            values.add(otherCount)
        }
        binding.chartCategories.setData(labels, values)
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
