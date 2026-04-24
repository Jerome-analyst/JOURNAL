package com.aibalance.tracker

import android.app.AppOpsManager
import android.content.Intent
import android.os.Build
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
import com.aibalance.tracker.AiUsageAdapter
import com.aibalance.tracker.AiUsageItem

cl ass MainActivity : AppCompatActivity() {

    private lateinit var tracker: AiUsageTracker
    private lateinit var adapter: AiUsageAdapter

    private lateinit var tvTodayUsage: TextView
    private lateinit var tvRemainingMinutes: TextView
    private lateinit var tvWaterPercent: TextView
    private lateinit var tvPermissionHint: TextView
    private lateinit var btnGrantAccess: Button
    private lateinit var progressDaily: ProgressBar
    private lateinit var glassContainer: FrameLayout
    private lateinit var viewWaterFill: View

    private val dailyLimitMinutes = 60

    private val trackedApps = linkedMapOf(
        "com.openai.chatgpt" to "ChatGPT",
        "com.anthropic.claude" to "Claude",
        "com.google.android.apps.bard" to "Gemini",
        "com.microsoft.copilot" to "Copilot",
        "com.perplexity.app" to "Perplexity"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tracker = AiUsageTracker(this)

        bindViews()
        setupRecycler()
        setupActions()

        refreshDashboard()
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    private fun bindViews() {
        tvTodayUsage = findViewById(R.id.tvTodayUsage)
        tvRemainingMinutes = findViewById(R.id.tvRemainingMinutes)
        tvWaterPercent = findViewById(R.id.tvWaterPercent)
        tvPermissionHint = findViewById(R.id.tvPermissionHint)
        btnGrantAccess = findViewById(R.id.btnGrantAccess)
        progressDaily = findViewById(R.id.progressDaily)
        glassContainer = findViewById(R.id.glassContainer)
        viewWaterFill = findViewById(R.id.viewWaterFill)

        progressDaily.max = dailyLimitMinutes
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
    }

    private fun refreshDashboard() {
        if (!hasUsageStatsPermission()) {
            showPermissionRequiredState()
            return
        }

        showUsageState()

        val usageMap = tracker.getAiUsage()
        val items = trackedApps.map { (packageName, displayName) ->
            AiUsageItem(
                appName = displayName,
                packageName = packageName,
                minutes = usageMap[packageName] ?: 0L
            )
        }

        val totalToday = tracker.getTotalAiMinutesToday().coerceAtLeast(0L)
        val remaining = tracker.getRemainingMinutes().coerceAtLeast(0L)

        tvTodayUsage.text = getString(R.string.minutes_value, totalToday)
        tvRemainingMinutes.text = getString(R.string.remaining_minutes_value, remaining)

        progressDaily.progress = totalToday.coerceAtMost(dailyLimitMinutes.toLong()).toInt()

        updateWaterFill(totalToday)
        adapter.submitList(items)
    }

    private fun showPermissionRequiredState() {
        tvPermissionHint.visibility = View.VISIBLE
        btnGrantAccess.visibility = View.VISIBLE

        tvTodayUsage.text = getString(R.string.minutes_value, 0)
        tvRemainingMinutes.text = getString(R.string.remaining_minutes_value, dailyLimitMinutes)
        progressDaily.progress = 0
        tvWaterPercent.text = getString(R.string.percentage_value, 0)
        updateWaterFill(0)

        adapter.submitList(
            trackedApps.map { (packageName, displayName) ->
                AiUsageItem(displayName, packageName, 0)
            }
        )
    }

    private fun showUsageState() {
        tvPermissionHint.visibility = View.GONE
        btnGrantAccess.visibility = View.GONE
    }

    private fun updateWaterFill(totalMinutes: Long) {
        val bounded = totalMinutes.coerceAtMost(dailyLimitMinutes.toLong())
        val fillPercent = if (dailyLimitMinutes == 0) 0f else bounded.toFloat() / dailyLimitMinutes.toFloat()
        val percentInt = (fillPercent * 100).toInt()

        tvWaterPercent.text = getString(R.string.percentage_value, percentInt)

        glassContainer.post {
            val maxHeight = glassContainer.height - glassContainer.paddingTop - glassContainer.paddingBottom
            val newHeight = (maxHeight * fillPercent).toInt().coerceAtLeast(0)

            viewWaterFill.updateLayoutParams<FrameLayout.LayoutParams> {
                height = newHeight
            }
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }

        return mode == AppOpsManager.MODE_ALLOWED
    }
}
