package com.deepfashion.classifier

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.appcompat.app.AppCompatDelegate

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        return true
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }
}

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        // 首次默认开启实时检测
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (!prefs.contains("pref_realtime_detection")) {
            prefs.edit().putBoolean("pref_realtime_detection", true).apply()
        }

        // 语言切换即时生效：去除“下次启动”提示
        findPreference<ListPreference>("pref_language")?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, _ ->
                // 立即更新全局语言
                activity?.recreate()
                true
            }

        // 性能优先即时生效：由分类器按需重建 session
        findPreference<Preference>("pref_performance_threads")?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, _ -> true }

        // 主题切换：跟随系统 / 浅色 / 深色
        findPreference<ListPreference>("pref_theme_mode")?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                when (newValue as String) {
                    "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
                activity?.recreate()
                true
            }

        // 实时检测配置变更：立即生效（返回相机界面后自动应用）
        findPreference<Preference>("pref_realtime_detection")?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, _ -> true }
        findPreference<ListPreference>("pref_detection_interval")?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, _ -> true }

        // 移除本地模型开关（不再提供）
    }
}


