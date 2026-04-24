package com.aibalance.tracker

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import java.util.Calendar

data class AiAppUsage(
    val packageName: String,
    val appName: String,
    val minutes: Long
)

class AiUsageTracker(private val context: Context) {

    private val usageStatsManager: UsageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    private val trackedApps = linkedMapOf(
        "com.openai.chatgpt" to "ChatGPT",
        "com.anthropic.claude" to "Claude",
        "com.google.android.apps.bard" to "Gemini",
        "com.microsoft.copilot" to "Copilot",
        "com.perplexity.app" to "Perplexity"
    )

    fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getAiUsage(): List<AiAppUsage> {
        if (!hasUsagePermission()) {
            return trackedApps.map { (packageName, appName) ->
                AiAppUsage(packageName, appName, 0L)
            }
        }

        val now = System.currentTimeMillis()
        val startToday = getStartOfDayMillis()
        val minutesByPackage = queryMinutesByPackage(startToday, now)

        return trackedApps.map { (packageName, appName) ->
            AiAppUsage(
                packageName = packageName,
                appName = appName,
                minutes = minutesByPackage[packageName] ?: 0L
            )
        }
    }

    fun getTotalAiMinutesToday(): Long {
        return getAiUsage().sumOf { it.minutes }
    }

    fun getTotalAiMinutesThisWeek(): Long {
        if (!hasUsagePermission()) return 0L

        val now = System.currentTimeMillis()
        val startWeek = getStartOfWeekMillis()
        val minutesByPackage = queryMinutesByPackage(startWeek, now)

        return trackedApps.keys.sumOf { packageName ->
            minutesByPackage[packageName] ?: 0L
        }
    }

    fun isLimitReached(dailyLimitMinutes: Long): Boolean {
        return getTotalAiMinutesToday() >= dailyLimitMinutes
    }

    fun getRemainingMinutes(dailyLimitMinutes: Long): Long {
        val remaining = dailyLimitMinutes - getTotalAiMinutesToday()
        return remaining.coerceAtLeast(0L)
    }

    private fun queryMinutesByPackage(startTime: Long, endTime: Long): Map<String, Long> {
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (usageStats.isNullOrEmpty()) {
            return emptyMap()
        }

        val result = mutableMapOf<String, Long>()
        usageStats.forEach { stat ->
            val pkg = stat.packageName
            if (!trackedApps.containsKey(pkg)) return@forEach

            val minutes = stat.totalTimeInForeground / 60000L
            val existing = result[pkg] ?: 0L
            result[pkg] = existing + minutes
        }

        return result
    }

    private fun getStartOfDayMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun getStartOfWeekMillis(): Long {
        return Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}