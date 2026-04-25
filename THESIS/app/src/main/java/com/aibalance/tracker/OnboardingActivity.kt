package com.aibalance.tracker

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class OnboardingActivity : AppCompatActivity() {

    companion object {
        const val KEY_ONBOARDING_DONE = "onboarding_done"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        NotificationHelper.createChannels(this)

        findViewById<Button>(R.id.btnGrantPermission).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        findViewById<Button>(R.id.btnGetStarted).setOnClickListener {
            getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
                .edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
            UsageCheckWorker.schedule(this)
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }
}
