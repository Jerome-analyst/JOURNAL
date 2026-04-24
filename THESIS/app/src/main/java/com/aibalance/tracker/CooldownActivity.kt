package com.aibalance.tracker

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class CooldownActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TODAY_USAGE_MINUTES = "extra_today_usage_minutes"
        const val EXTRA_DAILY_LIMIT_MINUTES = "extra_daily_limit_minutes"
        const val EXTRA_WEEKLY_USAGE_MINUTES = "extra_weekly_usage_minutes"
        const val EXTRA_BONUS_MINUTES = "extra_bonus_minutes"
    }

    private val prefs by lazy {
        getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
    }

    private var timer: CountDownTimer? = null

    private val gameLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val bonus = result.data?.getIntExtra(EXTRA_BONUS_MINUTES, 0) ?: 0
            if (bonus > 0) {
                val currentBonus = prefs.getInt(SettingsActivity.KEY_BONUS_MINUTES_EARNED, 0)
                prefs.edit().putInt(SettingsActivity.KEY_BONUS_MINUTES_EARNED, currentBonus + bonus).apply()
            }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cooldown)

        val tvCountdown = findViewById<TextView>(R.id.tvCountdown)
        val tvDailyValue = findViewById<TextView>(R.id.tvDailyValue)
        val tvWeeklyValue = findViewById<TextView>(R.id.tvWeeklyValue)
        val progressDaily = findViewById<ProgressBar>(R.id.progressDailyCooldown)
        val progressWeekly = findViewById<ProgressBar>(R.id.progressWeekly)
        val btnPlayMiniGame = findViewById<Button>(R.id.btnPlayMiniGame)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        val todayUsage = intent.getLongExtra(EXTRA_TODAY_USAGE_MINUTES, 0L)
        val dailyLimit = intent.getLongExtra(EXTRA_DAILY_LIMIT_MINUTES, 60L)
        val weeklyUsage = intent.getLongExtra(EXTRA_WEEKLY_USAGE_MINUTES, 0L)
        val weeklyLimit = dailyLimit * 7L

        progressDaily.max = dailyLimit.toInt().coerceAtLeast(1)
        progressDaily.progress = todayUsage.coerceAtMost(dailyLimit).toInt()
        tvDailyValue.text = getString(R.string.daily_usage_format, todayUsage, dailyLimit)

        progressWeekly.max = weeklyLimit.toInt().coerceAtLeast(1)
        progressWeekly.progress = weeklyUsage.coerceAtMost(weeklyLimit).toInt()
        tvWeeklyValue.text = getString(R.string.weekly_usage_format, weeklyUsage)

        val cooldownMinutes = prefs.getInt(SettingsActivity.KEY_COOLDOWN_DURATION_MINUTES, 10)
        startCooldownTimer(cooldownMinutes, tvCountdown)

        btnPlayMiniGame.setOnClickListener {
            gameLauncher.launch(Intent(this, GameActivity::class.java))
        }

        bottomNav.selectedItemId = R.id.nav_cooldown
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    true
                }
                R.id.nav_cooldown -> true
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun startCooldownTimer(minutes: Int, tvCountdown: TextView) {
        val durationMillis = minutes.coerceAtLeast(1) * 60_000L
        timer?.cancel()

        // Countdown runs while cooldown is active and updates the large timer label.
        timer = object : CountDownTimer(durationMillis, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSeconds = millisUntilFinished / 1000L
                val mm = totalSeconds / 60L
                val ss = totalSeconds % 60L
                tvCountdown.text = String.format("%02d:%02d", mm, ss)
            }

            override fun onFinish() {
                tvCountdown.text = "00:00"
            }
        }.start()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
