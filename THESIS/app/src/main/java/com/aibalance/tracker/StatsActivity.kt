package com.aibalance.tracker

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.concurrent.Executors

class StatsActivity : AppCompatActivity() {

    private val tracker by lazy { AiUsageTracker(this) }
    private val prefs by lazy { getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE) }
    private val ioExecutor = Executors.newSingleThreadExecutor()

    // Bar chart views (Mon–Sun)
    private val barIds = listOf(
        R.id.barMon, R.id.barTue, R.id.barWed, R.id.barThu,
        R.id.barFri, R.id.barSat, R.id.barSun
    )
    private val labelIds = listOf(
        R.id.tvBarMon, R.id.tvBarTue, R.id.tvBarWed, R.id.tvBarThu,
        R.id.tvBarFri, R.id.tvBarSat, R.id.tvBarSun
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        setupBottomNav()
        loadStats()
    }

    private fun loadStats() {
        ioExecutor.execute {
            val dailyLimit = prefs.getInt(SettingsActivity.KEY_DAILY_LIMIT_MINUTES, 60).toLong()
            val weekData = tracker.getDailyUsageForWeek()
            val appData = tracker.getAiUsage()

            val weekTotal = weekData.sumOf { it.minutes }
            val activeDays = weekData.count { it.minutes > 0 }
            val dailyAvg = if (activeDays > 0) weekTotal / activeDays else 0L

            val chatGptWeek = appData.filter { it.packageName == "com.openai.chatgpt" }.sumOf { it.minutes }
            val claudeWeek = appData.filter { it.packageName == "com.anthropic.claude" }.sumOf { it.minutes }
            val geminiWeek = appData.filter { it.packageName == "com.google.android.apps.bard" }.sumOf { it.minutes }
            val perplexityWeek = appData.filter { it.packageName == "com.perplexity.app" }.sumOf { it.minutes }
            val dolaWeek = appData.filter { it.packageName == "com.dola.ai" }.sumOf { it.minutes }
            val genieWeek = appData.filter { it.packageName == "com.ginie.ai" }.sumOf { it.minutes }
            val chatAIWeek = appData.filter { it.packageName == "com.chat.ai" }.sumOf { it.minutes }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                // Summary
                findViewById<TextView>(R.id.tvWeekTotal).text = "$weekTotal min"
                findViewById<TextView>(R.id.tvDailyAvg).text = "$dailyAvg min"

                // Bar chart
                updateBarChart(weekData, dailyLimit)

                // Weekly per-app
                val maxApp = maxOf(chatGptWeek, claudeWeek, geminiWeek, perplexityWeek, dolaWeek, genieWeek, chatAIWeek, 1L)
                updateWeekApp(R.id.tvWeekChatGPT, R.id.pbWeekChatGPT, chatGptWeek, maxApp)
                updateWeekApp(R.id.tvWeekClaude, R.id.pbWeekClaude, claudeWeek, maxApp)
                updateWeekApp(R.id.tvWeekGemini, R.id.pbWeekGemini, geminiWeek, maxApp)
                updateWeekApp(R.id.tvWeekPerplexity, R.id.pbWeekPerplexity, perplexityWeek, maxApp)
                updateWeekApp(R.id.tvWeekDola, R.id.pbWeekDola, dolaWeek, maxApp)
                updateWeekApp(R.id.tvWeekGenie, R.id.pbWeekGenie, genieWeek, maxApp)
                updateWeekApp(R.id.tvWeekChatAI, R.id.pbWeekChatAI, chatAIWeek, maxApp)

                // Daily limit list
                buildDailyLimitList(weekData, dailyLimit)

                // Hide placeholder
                findViewById<TextView>(R.id.tvDailyListPlaceholder).visibility = View.GONE
            }
        }
    }

    private fun updateBarChart(weekData: List<DayUsage>, dailyLimit: Long) {
        val maxMinutes = maxOf(weekData.maxOfOrNull { it.minutes } ?: 1L, dailyLimit)
        val chartMaxPx = resources.displayMetrics.density * 130 // 130dp in px

        weekData.forEachIndexed { i, day ->
            val barView = findViewById<View>(barIds[i])
            val labelView = findViewById<TextView>(labelIds[i])

            // Color: purple = today, red = over limit, blue = normal, dark = no data
            val barColor = when {
                day.isToday -> Color.parseColor("#534AB7")
                day.minutes > dailyLimit -> Color.parseColor("#A32D2D")
                day.minutes > 0 -> Color.parseColor("#378ADD")
                else -> Color.parseColor("#2A2A45")
            }
            barView.setBackgroundColor(barColor)

            // Label: bold + purple for today
            if (day.isToday) {
                labelView.setTextColor(Color.parseColor("#534AB7"))
                labelView.textSize = 11f
            }

            // Animate bar height
            val targetPx = if (maxMinutes > 0)
                ((day.minutes.toFloat() / maxMinutes) * chartMaxPx).toInt().coerceAtLeast(4)
            else 4

            ValueAnimator.ofInt(4, targetPx).apply {
                duration = 600L
                startDelay = (i * 80).toLong()
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    barView.layoutParams = barView.layoutParams.also {
                        it.height = anim.animatedValue as Int
                    }
                    barView.requestLayout()
                }
            }.start()
        }
    }

    private fun updateWeekApp(tvId: Int, pbId: Int, minutes: Long, maxMinutes: Long) {
        val suffix = if (minutes == 1L) "min" else "min"
        findViewById<TextView>(tvId).text = "$minutes $suffix"
        val pct = ((minutes.toFloat() / maxMinutes) * 100).toInt()
        animateProgressBar(findViewById(pbId), pct)
    }

    private fun animateProgressBar(pb: ProgressBar, target: Int) {
        ValueAnimator.ofInt(0, target).apply {
            duration = 700L
            interpolator = DecelerateInterpolator()
            addUpdateListener { pb.progress = it.animatedValue as Int }
        }.start()
    }

    private fun buildDailyLimitList(weekData: List<DayUsage>, dailyLimit: Long) {
        val container = findViewById<LinearLayout>(R.id.dailyLimitList)
        // Remove existing child views except the title and placeholder
        // Add a row per day that has usage data
        weekData.filter { it.minutes > 0 || it.isToday }.forEach { day ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, (12 * resources.displayMetrics.density).toInt())
            }
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val dayLabel = TextView(this).apply {
                text = day.dayName + if (day.isToday) " (Today)" else ""
                setTextColor(if (day.isToday) Color.parseColor("#534AB7") else Color.parseColor("#E8E8F8"))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val minLabel = TextView(this).apply {
                text = "${day.minutes} / $dailyLimit min"
                setTextColor(
                    if (day.minutes >= dailyLimit) Color.parseColor("#A32D2D")
                    else Color.parseColor("#555577")
                )
                textSize = 12f
            }
            header.addView(dayLabel)
            header.addView(minLabel)

            val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = dailyLimit.toInt().coerceAtLeast(1)
                progress = day.minutes.coerceAtMost(dailyLimit).toInt()
                progressDrawable = getDrawable(R.drawable.custom_progress_bar)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (8 * resources.displayMetrics.density).toInt()
                ).apply { topMargin = (4 * resources.displayMetrics.density).toInt() }
            }
            row.addView(header)
            row.addView(pb)
            container.addView(row)
        }
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

    private fun setupBottomNav() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.selectedItemId = R.id.nav_stats
        nav.setOnItemSelectedListener { item ->
            animateNavIcon(nav, item.itemId)
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    })
                    true
                }
                R.id.nav_stats -> true
                R.id.nav_cooldown -> {
                    startActivity(Intent(this, CooldownActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    })
                    true
                }
                R.id.nav_game -> {
                    startActivity(Intent(this, GameActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }
}
