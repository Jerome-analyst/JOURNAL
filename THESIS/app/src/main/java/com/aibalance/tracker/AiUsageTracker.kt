package com.aibalance.tracker

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import java.util.Calendar

data class DayUsage(val dayName: String, val minutes: Long, val isToday: Boolean)

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
        "com.perplexity.app" to "Perplexity",
        "com.dola.ai" to "Dola",
        "com.ginie.ai" to "Ginie",
        "com.chat.ai" to "Chat AI"
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

    /**
     * Uses UsageEvents to calculate precise foreground time.
     * This is more accurate than UsageStats which is often delayed by the system.
     */
    private fun queryMinutesByPackage(startTime: Long, endTime: Long): Map<String, Long> {
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        
        val usageMap = mutableMapOf<String, Long>() // Package -> Total Millis
        val lastEventTime = mutableMapOf<String, Long>() // Package -> Start Time

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName
            
            if (!trackedApps.containsKey(pkg)) continue

            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    lastEventTime[pkg] = event.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val start = lastEventTime[pkg]
                    if (start != null) {
                        val duration = event.timeStamp - start
                        usageMap[pkg] = (usageMap[pkg] ?: 0L) + duration
                        lastEventTime.remove(pkg)
                    }
                }
            }
        }

        // Handle apps still in foreground at the time of query
        lastEventTime.forEach { (pkg, start) ->
            val duration = endTime - start
            usageMap[pkg] = (usageMap[pkg] ?: 0L) + duration
        }

        // Convert millis to minutes, rounding correctly
        return usageMap.mapValues { (_, millis) ->
            // Use Math.round to be more accurate (e.g. 50 seconds = 1 minute)
            (millis + 30000L) / 60000L
        }
    }

    private fun getStartOfDayMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** Returns usage minutes per day for the current Mon–Sun week. */
    fun getDailyUsageForWeek(): List<DayUsage> {
        if (!hasUsagePermission()) {
            return listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                .map { DayUsage(it, 0L, false) }
        }
        val now = System.currentTimeMillis()
        val todayDoy = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val monday = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return buildList {
            for (i in 0..6) {
                val startTime = monday.timeInMillis
                val dayOfYear = monday.get(Calendar.DAY_OF_YEAR)
                monday.add(Calendar.DAY_OF_YEAR, 1)
                if (startTime >= now) { add(DayUsage(dayNames[i], 0L, dayOfYear == todayDoy)); continue }
                val endTime = monday.timeInMillis.coerceAtMost(now)
                val minutesByPackage = queryMinutesByPackage(startTime, endTime)
                val total = trackedApps.keys.sumOf { pkg -> minutesByPackage[pkg] ?: 0L }
                add(DayUsage(dayNames[i], total, dayOfYear == todayDoy))
            }
        }
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
