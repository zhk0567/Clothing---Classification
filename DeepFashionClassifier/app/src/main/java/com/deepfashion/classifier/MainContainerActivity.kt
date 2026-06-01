package com.deepfashion.classifier

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.deepfashion.classifier.databinding.ActivityMainContainerBinding

class MainContainerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainContainerBinding
    private var currentFragmentTag: String = TAG_HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainContainerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            val initialTab = intent.getStringExtra(EXTRA_TAB)
            val categoryFilter = intent.getStringExtra(EXTRA_CATEGORY_FILTER)
            when (initialTab) {
                TAB_HISTORY -> {
                    showFragment(HistoryFragment.newInstance(categoryFilter), TAG_HISTORY)
                    binding.bottomNavigation.selectedItemId = R.id.nav_history
                }
                else -> showFragment(HomeFragment(), TAG_HOME)
            }
        } else {
            currentFragmentTag = savedInstanceState.getString(STATE_FRAGMENT, TAG_HOME)
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> showFragment(HomeFragment(), TAG_HOME)
                R.id.nav_classify -> showFragment(ClassifyFragment(), TAG_CLASSIFY)
                R.id.nav_categories -> showFragment(CategoryListFragment(), TAG_CATEGORIES)
                R.id.nav_history -> showFragment(HistoryFragment.newInstance(), TAG_HISTORY)
                R.id.nav_more -> showFragment(MoreFragment(), TAG_MORE)
                else -> false
            }
        }

        checkOnboarding()
    }

    private fun checkOnboarding() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean(PREF_ONBOARDING_DONE, false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }

    private fun showFragment(fragment: Fragment, tag: String): Boolean {
        if (currentFragmentTag == tag && supportFragmentManager.findFragmentByTag(tag) != null) {
            return true
        }
        currentFragmentTag = tag
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment, tag)
            .commit()
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_FRAGMENT, currentFragmentTag)
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    companion object {
        const val PREF_ONBOARDING_DONE = "pref_onboarding_done"
        const val EXTRA_TAB = "extra_tab"
        const val EXTRA_CATEGORY_FILTER = "extra_category_filter"
        const val TAB_HISTORY = "history"
        private const val STATE_FRAGMENT = "state_fragment"
        private const val TAG_HOME = "home"
        private const val TAG_CLASSIFY = "classify"
        private const val TAG_CATEGORIES = "categories"
        private const val TAG_HISTORY = "history"
        private const val TAG_MORE = "more"
    }
}
