package com.deepfashion.classifier

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import androidx.preference.PreferenceManager
import java.util.Locale

object LocaleManager {
    fun wrap(base: Context): Context {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(base)
        val lang = prefs.getString("pref_language", "zh") ?: "zh"
        val locale = if (lang == "en") Locale.ENGLISH else Locale.CHINA
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            base.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            base.resources.updateConfiguration(config, base.resources.displayMetrics)
            base
        }
    }
}


