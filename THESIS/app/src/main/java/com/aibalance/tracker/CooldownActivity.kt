package com.aibalance.tracker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.ViewGroup
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
                val current = prefs.getInt(SettingsActivity.KEY_BONUS_MINUTES_EARNED, 0)
                prefs.edit().putInt(SettingsActivity.KEY_BONUS_MINUTES_EARNED, current + bonus).apply()
            }
            navigateToDashboard()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cooldown)

        val tvTimer = findViewById<TextView>(R.id.tvTimer)
        val tvTimerLabel = findViewById<TextView>(R.id.tvTimerLabel)
        val tvDailyUsage = findViewById<TextView>(R.id.tvDailyUsage)
        val tvWeeklyUsage = findViewById<TextView>(R.id.tvWeeklyUsage)
        val progressDaily = findViewById<ProgressBar>(R.id.progressDailyCooldown)
        val progressWeekly = findViewById<ProgressBar>(R.id.progressWeekly)
        val btnPlayGame = findViewById<Button>(R.id.btnPlayGame)
        val btnReturn = findViewById<Button>(R.id.btnReturnDashboard)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        val todayUsage = intent.getLongExtra(EXTRA_TODAY_USAGE_MINUTES, 0L)
        val dailyLimit = intent.getLongExtra(EXTRA_DAILY_LIMIT_MINUTES, 60L)
        val weeklyUsage = intent.getLongExtra(EXTRA_WEEKLY_USAGE_MINUTES, 0L)
        val weeklyLimit = dailyLimit * 7L

        progressDaily.max = dailyLimit.toInt().coerceAtLeast(1)
        progressDaily.progress = todayUsage.coerceAtMost(dailyLimit).toInt()
        tvDailyUsage.text = "$todayUsage / $dailyLimit min"

        progressWeekly.max = weeklyLimit.toInt().coerceAtLeast(1)
        progressWeekly.progress = weeklyUsage.coerceAtMost(weeklyLimit).toInt()
        tvWeeklyUsage.text = "$weeklyUsage min"

        val cooldownMinutes = prefs.getInt(SettingsActivity.KEY_COOLDOWN_DURATION_MINUTES, 10)
        btnReturn.visibility = View.GONE

        startCooldownTimer(cooldownMinutes, tvTimer, tvTimerLabel, btnPlayGame, btnReturn)

        btnPlayGame.setOnClickListener {
            gameLauncher.launch(Intent(this, GameActivity::class.java))
        }
        btnReturn.setOnClickListener { navigateToDashboard() }

        bottomNav.selectedItemId = R.id.nav_cooldown
        bottomNav.setOnItemSelectedListener { item ->
            animateNavIcon(bottomNav, item.itemId)
            when (item.itemId) {
                R.id.nav_dashboard -> { navigateToDashboard(); true }
                R.id.nav_stats -> {
                    startActivity(Intent(this, StatsActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    })
                    true
                }
                R.id.nav_cooldown -> true
                R.id.nav_game -> {
                    startActivity(Intent(this, GameActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun startCooldownTimer(
        minutes: Int,
        tvTimer: TextView,
        tvTimerLabel: TextView,
        btnPlayGame: Button,
        btnReturn: Button
    ) {
        val durationMs = minutes.coerceAtLeast(1) * 60_000L
        timer?.cancel()
        timer = object : CountDownTimer(durationMs, 1000L) {
            override fun onTick(ms: Long) {
                val total = ms / 1000L
                tvTimer.text = String.format("%02d:%02d", total / 60L, total % 60L)
                // Subtle pulse every second
                tvTimer.animate().scaleX(1.03f).scaleY(1.03f).setDuration(300).withEndAction {
                    tvTimer.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
                }.start()
            }

            override fun onFinish() {
                tvTimer.text = "Done!"
                tvTimer.setTextColor(Color.parseColor("#1D9E75"))
                tvTimerLabel.text = "Cooldown complete! Great job taking a break."
                tvTimerLabel.setTextColor(Color.parseColor("#1D9E75"))
                btnPlayGame.visibility = View.GONE
                btnReturn.visibility = View.VISIBLE
            }
        }.start()
    }

    private fun animateNavIcon(nav: BottomNavigationView, itemId: Int) {
        val menuView = nav.getChildAt(0) as? ViewGroup ?: return
        val order = listOf(R.id.nav_dashboard, R.id.nav_stats, R.id.nav_cooldown, R.id.nav_game)
        val idx = order.indexOf(itemId)
        if (idx < 0 || idx >= menuView.childCount) return
        menuView.getChildAt(idx).run {
            animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }.start()
        }
    }

    private fun navigateToDashboard() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        })
        finish()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
