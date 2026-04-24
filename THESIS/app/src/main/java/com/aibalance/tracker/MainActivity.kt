package com.aibalance.tracker

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var tracker: AiUsageTracker
    private lateinit var adapter: AiUsageAdapter

    private lateinit var tvTodayUsage: TextView
    private lateinit var tvRemainingMinutes: TextView
    private lateinit var tvWaterPercent: TextView
    private lateinit var tvStreakDays: TextView
    private lateinit var tvWordsSolved: TextView
    private lateinit var tvBonusMinutes: TextView
    private lateinit var tvPermissionHint: TextView
    private lateinit var btnGrantAccess: Button
    private lateinit var btnSettings: Button
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var progressDaily: ProgressBar
    private lateinit var glassContainers: List<FrameLayout>
    private lateinit var glassFills: List<View>

    private val prefs by lazy {
        getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
    }

    private val defaultDailyLimitMinutes = 60L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tracker = AiUsageTracker(this)

        bindViews()
        setupRecycler()
        setupActions()
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    private fun bindViews() {
        tvTodayUsage = findViewById(R.id.tvTodayUsage)
        tvRemainingMinutes = findViewById(R.id.tvRemainingMinutes)
        tvWaterPercent = findViewById(R.id.tvWaterPercent)
        tvStreakDays = findViewById(R.id.tvStreakDays)
        tvWordsSolved = findViewById(R.id.tvWordsSolved)
        tvBonusMinutes = findViewById(R.id.tvBonusMinutes)
        tvPermissionHint = findViewById(R.id.tvPermissionHint)
        btnGrantAccess = findViewById(R.id.btnGrantAccess)
        btnSettings = findViewById(R.id.btnSettings)
        bottomNav = findViewById(R.id.bottomNav)
        progressDaily = findViewById(R.id.progressDaily)

        glassContainers = listOf(
            findViewById(R.id.glass1),
            findViewById(R.id.glass2),
            findViewById(R.id.glass3),
            findViewById(R.id.glass4),
            findViewById(R.id.glass5)
        )
        glassFills = listOf(
            findViewById(R.id.viewGlassFill1),
            findViewById(R.id.viewGlassFill2),
            findViewById(R.id.viewGlassFill3),
            findViewById(R.id.viewGlassFill4),
            findViewById(R.id.viewGlassFill5)
        )
    }

    private fun setupRecycler() {
        val rvAiApps = findViewById<RecyclerView>(R.id.rvAiApps)
        adapter = AiUsageAdapter()
        rvAiApps.layoutManager = LinearLayoutManager(this)
        rvAiApps.adapter = adapter
    }

    private fun setupActions() {
        btnGrantAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        bottomNav.selectedItemId = R.id.nav_dashboard
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> true
                R.id.nav_cooldown -> {
                    val dailyLimit = prefs.getInt(
                        SettingsActivity.KEY_DAILY_LIMIT_MINUTES,
                        defaultDailyLimitMinutes.toInt()
                    ).toLong()
                    val today = if (tracker.hasUsagePermission()) tracker.getTotalAiMinutesToday() else 0L
                    val weekly = if (tracker.hasUsagePermission()) tracker.getTotalAiMinutesThisWeek() else 0L
                    openCooldown(today, dailyLimit, weekly)
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun refreshDashboard() {
        val dailyLimitMinutes = prefs.getInt(SettingsActivity.KEY_DAILY_LIMIT_MINUTES, defaultDailyLimitMinutes.toInt()).toLong()
            .coerceIn(10L, 120L)
        val bonusMinutes = prefs.getInt(SettingsActivity.KEY_BONUS_MINUTES_EARNED, 0).toLong()
        val effectiveLimit = (dailyLimitMinutes + bonusMinutes).coerceAtLeast(1L)
        val streakDays = prefs.getInt(SettingsActivity.KEY_STREAK_DAYS, 0)
        val wordsSolved = prefs.getInt(SettingsActivity.KEY_WORDS_SOLVED, 0)

        tvStreakDays.text = getString(R.string.streak_days_format, streakDays)
        tvWordsSolved.text = getString(R.string.words_solved_format, wordsSolved)
        tvBonusMinutes.text = getString(R.string.bonus_minutes_format, bonusMinutes)

        progressDaily.max = effectiveLimit.toInt()

        if (!tracker.hasUsagePermission()) {
            showPermissionRequiredState(effectiveLimit)
            return
        }

        showUsageState()

        val usageList = tracker.getAiUsage()
        val items = usageList.map {
            AiUsageItem(
                appName = it.appName,
                packageName = it.packageName,
                minutes = it.minutes
            )
        }

        val totalToday = tracker.getTotalAiMinutesToday().coerceAtLeast(0L)
        val remaining = (effectiveLimit - totalToday).coerceAtLeast(0L)

        tvTodayUsage.text = getString(R.string.minutes_value, totalToday)
        tvRemainingMinutes.text = getString(R.string.remaining_minutes_value, remaining)
        progressDaily.progress = totalToday.coerceAtMost(effectiveLimit).toInt()

        updateWaterFill(totalToday, effectiveLimit)
        adapter.submitList(items)

        val shouldAlertLimit = prefs.getBoolean(SettingsActivity.KEY_LIMIT_ALERT, true)
        if (shouldAlertLimit && totalToday >= effectiveLimit) {
            openCooldown(totalToday, effectiveLimit, tracker.getTotalAiMinutesThisWeek())
        }
    }

    private fun showPermissionRequiredState(dailyLimitMinutes: Long) {
        tvPermissionHint.visibility = View.VISIBLE
        btnGrantAccess.visibility = View.VISIBLE

        tvTodayUsage.text = getString(R.string.minutes_value, 0)
        tvRemainingMinutes.text = getString(R.string.remaining_minutes_value, dailyLimitMinutes)
        progressDaily.progress = 0
        tvWaterPercent.text = getString(R.string.percentage_value, 0)
        updateWaterFill(0, dailyLimitMinutes)
        adapter.submitList(emptyList())
    }

    private fun showUsageState() {
        tvPermissionHint.visibility = View.GONE
        btnGrantAccess.visibility = View.GONE
    }

    private fun updateWaterFill(totalMinutes: Long, dailyLimitMinutes: Long) {
        val bounded = totalMinutes.coerceAtMost(dailyLimitMinutes)
        val fillPercent = if (dailyLimitMinutes == 0L) 0f else bounded.toFloat() / dailyLimitMinutes.toFloat()
        tvWaterPercent.text = getString(R.string.percentage_value, (fillPercent * 100).toInt())

        // Fill each of the five glasses based on the overall daily usage percentage.
        glassContainers.zip(glassFills).forEachIndexed { index, (container, fillView) ->
            container.post {
                val segmentStart = index / 5f
                val segmentFill = ((fillPercent - segmentStart) * 5f).coerceIn(0f, 1f)
                val availableHeight = container.height - container.paddingTop - container.paddingBottom
                val newHeight = (availableHeight * segmentFill).toInt().coerceAtLeast(0)
                fillView.updateLayoutParams<FrameLayout.LayoutParams> {
                    height = newHeight
                }
            }
        }
    }

    private fun openCooldown(totalToday: Long, dailyLimitMinutes: Long, weeklyUsage: Long) {
        val intent = Intent(this, CooldownActivity::class.java).apply {
            putExtra(CooldownActivity.EXTRA_TODAY_USAGE_MINUTES, totalToday)
            putExtra(CooldownActivity.EXTRA_DAILY_LIMIT_MINUTES, dailyLimitMinutes)
            putExtra(CooldownActivity.EXTRA_WEEKLY_USAGE_MINUTES, weeklyUsage)
        }
        startActivity(intent)
    }
}
