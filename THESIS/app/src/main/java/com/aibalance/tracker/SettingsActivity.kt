package com.aibalance.tracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "ai_balance_prefs"
        const val KEY_DAILY_LIMIT_MINUTES = "daily_limit_minutes"
        const val KEY_COOLDOWN_DURATION_MINUTES = "cooldown_duration_minutes"
        const val KEY_GENTLE_REMINDERS = "gentle_reminders"
        const val KEY_LIMIT_ALERT = "limit_alert"
        const val KEY_STREAK_NOTIFICATIONS = "streak_notifications"
        const val KEY_WEEKLY_SUMMARY = "weekly_summary"
        const val KEY_BONUS_MINUTES_EARNED = "bonus_minutes_earned"
        const val KEY_WORDS_SOLVED = "words_solved"
        const val KEY_STREAK_DAYS = "streak_days"
    }

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val seekDaily = findViewById<SeekBar>(R.id.seekDailyLimit)
        val seekCooldown = findViewById<SeekBar>(R.id.seekCooldown)
        val tvDailyValue = findViewById<TextView>(R.id.tvDailyLimitValue)
        val tvCooldownValue = findViewById<TextView>(R.id.tvCooldownValue)

        val switchGentle = findViewById<SwitchMaterial>(R.id.switchGentleReminders)
        val switchLimit = findViewById<SwitchMaterial>(R.id.switchLimitAlert)
        val switchStreak = findViewById<SwitchMaterial>(R.id.switchStreakNotifications)
        val switchWeekly = findViewById<SwitchMaterial>(R.id.switchWeeklySummary)
        val btnSave = findViewById<Button>(R.id.btnSaveSettings)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        val dailyLimit = prefs.getInt(KEY_DAILY_LIMIT_MINUTES, 60)
        val cooldown = prefs.getInt(KEY_COOLDOWN_DURATION_MINUTES, 10)

        seekDaily.progress = (dailyLimit - 10).coerceIn(0, 110)
        seekCooldown.progress = (cooldown - 5).coerceIn(0, 55)

        tvDailyValue.text = getString(R.string.minutes_value, dailyLimit.toLong())
        tvCooldownValue.text = getString(R.string.minutes_value, cooldown.toLong())

        switchGentle.isChecked = prefs.getBoolean(KEY_GENTLE_REMINDERS, true)
        switchLimit.isChecked = prefs.getBoolean(KEY_LIMIT_ALERT, true)
        switchStreak.isChecked = prefs.getBoolean(KEY_STREAK_NOTIFICATIONS, true)
        switchWeekly.isChecked = prefs.getBoolean(KEY_WEEKLY_SUMMARY, true)

        seekDaily.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + 10
                tvDailyValue.text = getString(R.string.minutes_value, value.toLong())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        seekCooldown.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + 5
                tvCooldownValue.text = getString(R.string.minutes_value, value.toLong())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        btnSave.setOnClickListener {
            prefs.edit()
                .putInt(KEY_DAILY_LIMIT_MINUTES, seekDaily.progress + 10)
                .putInt(KEY_COOLDOWN_DURATION_MINUTES, seekCooldown.progress + 5)
                .putBoolean(KEY_GENTLE_REMINDERS, switchGentle.isChecked)
                .putBoolean(KEY_LIMIT_ALERT, switchLimit.isChecked)
                .putBoolean(KEY_STREAK_NOTIFICATIONS, switchStreak.isChecked)
                .putBoolean(KEY_WEEKLY_SUMMARY, switchWeekly.isChecked)
                .apply()
            finish()
        }

        bottomNav.selectedItemId = R.id.nav_settings
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    true
                }
                R.id.nav_cooldown -> {
                    startActivity(Intent(this, CooldownActivity::class.java))
                    true
                }
                R.id.nav_settings -> true
                else -> false
            }
        }
    }
}
