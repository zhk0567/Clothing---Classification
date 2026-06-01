package com.deepfashion.classifier

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** 兼容旧入口，转发至 MainContainerActivity */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainContainerActivity::class.java))
        finish()
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }
}
