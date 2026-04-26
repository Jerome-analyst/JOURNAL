package com.aibalance.tracker

import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import com.google.android.material.button.MaterialButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var tracker: AiUsageTracker
    private lateinit var streakManager: StreakManager

    // Header
    private lateinit var tvGreeting: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvStreakBadge: TextView

    // Usage card
    private lateinit var tvTodayUsage: TextView
    private lateinit var tvLimitSuffix: TextView
    private lateinit var tvRemainingMinutes: TextView
    private lateinit var tvWaterPercent: TextView
    private lateinit var progressDaily: ProgressBar
    private lateinit var glassContainers: List<FrameLayout>
    private lateinit var glassFills: List<View>

    // Per-app breakdown
    private lateinit var pbChatGPT: ProgressBar
    private lateinit var tvChatGPTPct: TextView
    private lateinit var tvChatGPTMins: TextView
    private lateinit var pbClaude: ProgressBar
    private lateinit var tvClaudePct: TextView
    private lateinit var tvClaudeMins: TextView
    private lateinit var pbGemini: ProgressBar
    private lateinit var tvGeminiPct: TextView
    private lateinit var tvGeminiMins: TextView
    private lateinit var pbCopilot: ProgressBar
    private lateinit var tvCopilotPct: TextView
    private lateinit var tvCopilotMins: TextView
    private lateinit var pbPerplexity: ProgressBar
    private lateinit var tvPerplexityPct: TextView
    private lateinit var tvPerplexityMins: TextView

    // Expandable apps list state
    private var appsExpanded = false

    // Stats chips
    private lateinit var tvStreakDays: TextView
    private lateinit var tvWordsSolved: TextView
    private lateinit var tvBonusMinutes: TextView

    // Permission
    private lateinit var cvPermission: MaterialCardView
    private lateinit var tvPermissionHint: TextView
    private lateinit var btnGrantAccess: MaterialButton

    // Navigation
    private lateinit var btnSettings: ImageButton
    private lateinit var bottomNav: BottomNavigationView

    private val prefs by lazy {
        getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
    }

    private val defaultDailyLimit = 60L
    private val ioExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var refreshInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tracker = AiUsageTracker(this)
        streakManager = StreakManager(this)
        streakManager.checkAndUpdateStreak()
        bindViews()
        setupActions()
        requestNotificationPermissionIfNeeded()
        UsageCheckWorker.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        updateGreeting()
        refreshDashboard()
    }

    private fun bindViews() {
        tvGreeting = findViewById(R.id.tvGreeting)
        tvDate = findViewById(R.id.tvDate)
        tvStreakBadge = findViewById(R.id.tvStreakBadge)
        tvTodayUsage = findViewById(R.id.tvTodayUsage)
        tvLimitSuffix = findViewById(R.id.tvLimitSuffix)
        tvRemainingMinutes = findViewById(R.id.tvRemainingMinutes)
        tvWaterPercent = findViewById(R.id.tvWaterPercent)
        progressDaily = findViewById(R.id.progressDaily)

        glassContainers = listOf(
            findViewById(R.id.glass1), findViewById(R.id.glass2), findViewById(R.id.glass3),
            findViewById(R.id.glass4), findViewById(R.id.glass5)
        )
        glassFills = listOf(
            findViewById(R.id.viewGlassFill1), findViewById(R.id.viewGlassFill2),
            findViewById(R.id.viewGlassFill3), findViewById(R.id.viewGlassFill4),
            findViewById(R.id.viewGlassFill5)
        )

        pbChatGPT = findViewById(R.id.pbChatGPT)
        tvChatGPTPct = findViewById(R.id.tvChatGPTPct)
        tvChatGPTMins = findViewById(R.id.tvChatGPTMins)
        pbClaude = findViewById(R.id.pbClaude)
        tvClaudePct = findViewById(R.id.tvClaudePct)
        tvClaudeMins = findViewById(R.id.tvClaudeMins)
        pbGemini = findViewById(R.id.pbGemini)
        tvGeminiPct = findViewById(R.id.tvGeminiPct)
        tvGeminiMins = findViewById(R.id.tvGeminiMins)
        pbCopilot = findViewById(R.id.pbCopilot)
        tvCopilotPct = findViewById(R.id.tvCopilotPct)
        tvCopilotMins = findViewById(R.id.tvCopilotMins)
        pbPerplexity = findViewById(R.id.pbPerplexity)
        tvPerplexityPct = findViewById(R.id.tvPerplexityPct)
        tvPerplexityMins = findViewById(R.id.tvPerplexityMins)

        tvStreakDays = findViewById(R.id.tvStreakDays)
        tvWordsSolved = findViewById(R.id.tvWordsSolved)
        tvBonusMinutes = findViewById(R.id.tvBonusMinutes)

        cvPermission = findViewById(R.id.cvPermission)
        tvPermissionHint = findViewById(R.id.tvPermissionHint)
        btnGrantAccess = findViewById(R.id.btnGrantAccess)
        btnSettings = findViewById(R.id.btnSettings)
        bottomNav = findViewById(R.id.bottomNav)
    }

    private fun setupActions() {
        btnGrantAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<TextView>(R.id.tvViewAllApps).setOnClickListener { toggleAppsList() }

        bottomNav.selectedItemId = R.id.nav_dashboard
        bottomNav.setOnItemSelectedListener { item ->
            animateNavIcon(item.itemId)
            when (item.itemId) {
                R.id.nav_dashboard -> true
                R.id.nav_stats -> {
                    startActivity(Intent(this, StatsActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    })
                    true
                }
                R.id.nav_cooldown -> {
                    val limit = prefs.getInt(SettingsActivity.KEY_DAILY_LIMIT_MINUTES, defaultDailyLimit.toInt()).toLong()
                    val today = if (tracker.hasUsagePermission()) tracker.getTotalAiMinutesToday() else 0L
                    val weekly = if (tracker.hasUsagePermission()) tracker.getTotalAiMinutesThisWeek() else 0L
                    openCooldown(today, limit, weekly)
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

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val word = when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }
        val name = FirebaseAuth.getInstance().currentUser?.displayName?.split(" ")?.firstOrNull()
        tvGreeting.text = if (!name.isNullOrBlank()) "$word, $name!" else "$word!"
        tvDate.text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Calendar.getInstance().time)
    }

    private fun refreshDashboard() {
        if (refreshInProgress) return

        val dailyLimitMinutes = prefs.getInt(SettingsActivity.KEY_DAILY_LIMIT_MINUTES, defaultDailyLimit.toInt()).toLong().coerceIn(10L, 120L)
        val bonusMinutes = prefs.getInt(SettingsActivity.KEY_BONUS_MINUTES_EARNED, 0).toLong()
        val effectiveLimit = (dailyLimitMinutes + bonusMinutes).coerceAtLeast(1L)
        val wordsSolved = prefs.getInt(SettingsActivity.KEY_WORDS_SOLVED, 0)

        tvLimitSuffix.text = "/ $effectiveLimit min"

        val todayKey = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

        // Streak is managed by StreakManager — updated once per day in onCreate()
        val streakDays = streakManager.getCurrentStreak()
        tvStreakBadge.text = "🔥 $streakDays Day Streak"
        tvStreakDays.text = "$streakDays"
        tvWordsSolved.text = "$wordsSolved"
        tvBonusMinutes.text = "+$bonusMinutes"

        // Animate stat cards: fade in + slide up
        animateStatCards()

        progressDaily.max = effectiveLimit.toInt()

        if (!tracker.hasUsagePermission()) {
            showPermissionState(effectiveLimit)
            return
        }
        cvPermission.visibility = View.GONE

        refreshInProgress = true
        ioExecutor.execute {
            try {
                val usageList = tracker.getAiUsage()
                val totalToday = usageList.sumOf { it.minutes }.coerceAtLeast(0L)
                val remaining = (effectiveLimit - totalToday).coerceAtLeast(0L)

                val chatGpt = usageList.find { it.packageName == "com.openai.chatgpt" }?.minutes ?: 0L
                val claude = usageList.find { it.packageName == "com.anthropic.claude" }?.minutes ?: 0L
                val gemini = usageList.find { it.packageName == "com.google.android.apps.bard" }?.minutes ?: 0L
                val copilot = usageList.find { it.packageName == "com.microsoft.copilot" }?.minutes ?: 0L
                val perplexity = usageList.find { it.packageName == "com.perplexity.app" }?.minutes ?: 0L

                val shouldAlert = prefs.getBoolean(SettingsActivity.KEY_LIMIT_ALERT, true)
                val lastCooldown = prefs.getInt(SettingsActivity.KEY_LAST_COOLDOWN_DAY, -1)
                var weeklyForCooldown: Long? = null
                if (shouldAlert && totalToday >= effectiveLimit && lastCooldown != todayKey) {
                    weeklyForCooldown = tracker.getTotalAiMinutesThisWeek()
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) { refreshInProgress = false; return@runOnUiThread }

                    tvTodayUsage.text = "$totalToday"
                    tvRemainingMinutes.text = "$remaining min remaining"
                    progressDaily.progress = totalToday.coerceAtMost(effectiveLimit).toInt()
                    updateWaterFillAnimated(totalToday, effectiveLimit)
                    updatePerAppBreakdown(chatGpt, claude, gemini, copilot, perplexity, effectiveLimit)

                    if (weeklyForCooldown != null) {
                        prefs.edit().putInt(SettingsActivity.KEY_LAST_COOLDOWN_DAY, todayKey).apply()
                        openCooldown(totalToday, effectiveLimit, weeklyForCooldown)
                    }
                    refreshInProgress = false
                }
            } catch (_: Exception) {
                runOnUiThread { refreshInProgress = false }
            }
        }
    }

    private fun showPermissionState(limit: Long) {
        cvPermission.visibility = View.VISIBLE
        tvTodayUsage.text = "0"
        tvLimitSuffix.text = "/ $limit min"
        tvRemainingMinutes.text = "$limit min remaining"
        progressDaily.progress = 0
        tvWaterPercent.text = "0%"
        updateWaterFillAnimated(0, limit)
    }

    private fun updatePerAppBreakdown(chatGpt: Long, claude: Long, gemini: Long, copilot: Long, perplexity: Long, limit: Long) {
        fun pct(mins: Long) = ((mins.toFloat() / limit) * 100).toInt().coerceIn(0, 100)

        animatePb(pbChatGPT, pct(chatGpt)); tvChatGPTPct.text = "${pct(chatGpt)}%"; tvChatGPTMins.text = "$chatGpt min"
        animatePb(pbClaude, pct(claude)); tvClaudePct.text = "${pct(claude)}%"; tvClaudeMins.text = "$claude min"
        animatePb(pbGemini, pct(gemini)); tvGeminiPct.text = "${pct(gemini)}%"; tvGeminiMins.text = "$gemini min"
        animatePb(pbCopilot, pct(copilot)); tvCopilotPct.text = "${pct(copilot)}%"; tvCopilotMins.text = "$copilot min"
        animatePb(pbPerplexity, pct(perplexity)); tvPerplexityPct.text = "${pct(perplexity)}%"; tvPerplexityMins.text = "$perplexity min"
    }

    private fun toggleAppsList() {
        val container = findViewById<LinearLayout>(R.id.llExpandableApps)
        val tvViewAll = findViewById<TextView>(R.id.tvViewAllApps)
        val chevron = findViewById<TextView>(R.id.tvChevron)

        if (!appsExpanded) {
            container.visibility = View.VISIBLE
            container.measure(
                View.MeasureSpec.makeMeasureSpec(container.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val targetH = container.measuredHeight
            container.layoutParams.height = 0
            container.requestLayout()

            ValueAnimator.ofInt(0, targetH).apply {
                duration = 300
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    container.layoutParams.height = it.animatedValue as Int
                    container.requestLayout()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        container.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                })
            }.start()

            ObjectAnimator.ofFloat(chevron, "rotation", 0f, 180f).setDuration(300).start()
            tvViewAll.text = "Show less"
            appsExpanded = true
        } else {
            val startH = container.height
            ValueAnimator.ofInt(startH, 0).apply {
                duration = 300
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    container.layoutParams.height = it.animatedValue as Int
                    container.requestLayout()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        container.visibility = View.GONE
                        container.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                })
            }.start()

            ObjectAnimator.ofFloat(chevron, "rotation", 180f, 0f).setDuration(300).start()
            tvViewAll.text = "View all"
            appsExpanded = false
        }
    }

    private fun animatePb(pb: ProgressBar, target: Int) {
        ValueAnimator.ofInt(0, target).apply {
            duration = 600L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { pb.progress = it.animatedValue as Int }
        }.start()
    }

    private fun animateStatCards() {
        val statsLayout = findViewById<LinearLayout>(R.id.layoutStats) ?: return
        for (i in 0 until statsLayout.childCount) {
            val child = statsLayout.getChildAt(i)
            child.alpha = 0f
            child.translationY = 40f * resources.displayMetrics.density
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setStartDelay((i * 80).toLong())
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }

    private fun animateNavIcon(itemId: Int) {
        val menuView = bottomNav.getChildAt(0) as? ViewGroup ?: return
        val order = listOf(R.id.nav_dashboard, R.id.nav_stats, R.id.nav_cooldown, R.id.nav_game)
        val idx = order.indexOf(itemId)
        if (idx < 0 || idx >= menuView.childCount) return
        menuView.getChildAt(idx).run {
            animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }.start()
        }
    }

    private fun updateWaterFillAnimated(totalMinutes: Long, limit: Long) {
        val bounded = totalMinutes.coerceAtMost(limit)
        val fillPercent = if (limit == 0L) 0f else bounded.toFloat() / limit.toFloat()
        tvWaterPercent.text = "${(fillPercent * 100).toInt()}%"

        val fillColor = when {
            fillPercent < 0.5f -> android.graphics.Color.parseColor("#378ADD")
            fillPercent < 0.8f -> android.graphics.Color.parseColor("#ED8936")
            else -> android.graphics.Color.parseColor("#A32D2D")
        }

        glassContainers.zip(glassFills).forEachIndexed { index, (container, fill) ->
            container.post {
                val segStart = index / 5f
                val segFill = ((fillPercent - segStart) * 5f).coerceIn(0f, 1f)
                val available = container.height - container.paddingTop - container.paddingBottom
                val targetH = (available * segFill).toInt().coerceAtLeast(0)
                val currentH = fill.layoutParams.height.coerceAtLeast(0)
                fill.setBackgroundColor(fillColor)
                ValueAnimator.ofInt(currentH, targetH).apply {
                    duration = 800L
                     interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener { anim ->
                        fill.updateLayoutParams<FrameLayout.LayoutParams> {
                            height = anim.animatedValue as Int
                        }
                    }
                }.start()
            }
        }
    }

    private fun openCooldown(today: Long, limit: Long, weekly: Long) {
        startActivity(Intent(this, CooldownActivity::class.java).apply {
            putExtra(CooldownActivity.EXTRA_TODAY_USAGE_MINUTES, today)
            putExtra(CooldownActivity.EXTRA_DAILY_LIMIT_MINUTES, limit)
            putExtra(CooldownActivity.EXTRA_WEEKLY_USAGE_MINUTES, weekly)
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        })
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }
}
