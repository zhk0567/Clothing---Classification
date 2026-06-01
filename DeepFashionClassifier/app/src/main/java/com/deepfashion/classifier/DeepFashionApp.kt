package com.deepfashion.classifier

import android.app.Application

class DeepFashionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        // 移除已废弃的实时检测相关偏好项
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this).edit()
            .remove("pref_realtime_detection")
            .remove("pref_detection_interval")
            .apply()
    }
}
