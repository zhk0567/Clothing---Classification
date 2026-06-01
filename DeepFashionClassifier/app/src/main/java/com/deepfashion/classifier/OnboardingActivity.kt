package com.deepfashion.classifier

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.deepfashion.classifier.databinding.ActivityOnboardingBinding
import com.deepfashion.classifier.databinding.ItemOnboardingPageBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private var page = 0

    private val pages = listOf(
        R.string.onboarding_title_1 to R.string.onboarding_desc_1,
        R.string.onboarding_title_2 to R.string.onboarding_desc_2,
        R.string.onboarding_title_3 to R.string.onboarding_desc_3,
        R.string.onboarding_title_4 to R.string.onboarding_desc_4
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewPager.adapter = OnboardingAdapter(pages.map { getString(it.first) to getString(it.second) })
        binding.btnNext.setOnClickListener {
            if (page < pages.size - 1) {
                page++
                binding.viewPager.currentItem = page
                updateButton()
            } else {
                PreferenceManager.getDefaultSharedPreferences(this)
                    .edit().putBoolean(MainContainerActivity.PREF_ONBOARDING_DONE, true).apply()
                finish()
            }
        }
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                page = position
                updateButton()
            }
        })
    }

    private fun updateButton() {
        binding.btnNext.text = if (page == pages.size - 1) getString(R.string.get_started) else getString(R.string.next)
    }

    private class OnboardingAdapter(private val items: List<Pair<String, String>>) :
        RecyclerView.Adapter<OnboardingAdapter.VH>() {
        class VH(val binding: ItemOnboardingPageBinding) : RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemOnboardingPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.binding.tvOnboardingTitle.text = items[position].first
            holder.binding.tvOnboardingDesc.text = items[position].second
        }
        override fun getItemCount() = items.size
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }
}
