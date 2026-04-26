package com.aibalance.tracker

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class DayUsage(val dayName: String, val minutes: Long, val isToday: Boolean)

data class AiAppUsage(
    val packageName: String,
    val appName: String,
    val minutes: Long,
    val isInstalled: Boolean = true
)

class AiUsageTracker(private val context: Context) {

    companion object {
        private const val TAG = "AiUsageTracker"
        private const val RUNNING_WINDOW_MS = 5 * 60 * 1000L

        // All 8 AI apps to track. Gemini ships under two package names depending on the device.
        // Verify Dola AI / Chat AI packages with: adb shell pm list packages | grep -i dola
        val AI_APPS = linkedMapOf(
            "com.openai.chatgpt"             to "ChatGPT",
            "com.anthropic.claude"           to "Claude",
            "com.google.android.apps.bard"   to "Gemini",
            "com.google.android.apps.gemini" to "Gemini (alt)",
            "com.microsoft.copilot"          to "Copilot",
            "com.perplexity.app"             to "Perplexity",
            "ai.dola"                        to "Dola AI",
            "com.chatai.android"             to "Chat AI"
        )
    }

    private val usageStatsManager: UsageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    // ── Permission ──────────────────────────────────────────────────────────────

    /** Returns true if the user has granted Usage Access in device Settings. */
    fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    // ── Time helpers ────────────────────────────────────────────────────────────

    /** Returns today's midnight as a Unix timestamp. Every today-query window starts here. */
    fun getTodayStartTime(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun getStartOfWeekMillis(): Long = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    // ── Installed-app filter ────────────────────────────────────────────────────

    /**
     * Returns package names of AI apps actually installed on this device.
     * Uses getApplicationInfo() for a lightweight presence check.
     * Run `adb logcat -s AiUsageTracker` to see found vs. missing packages.
     */
    fun getInstalledAiApps(): List<String> {
        val installed = mutableListOf<String>()
        val missing = mutableListOf<String>()
        AI_APPS.forEach { (pkg, name) ->
            val found = try { context.packageManager.getApplicationInfo(pkg, 0); true }
                        catch (_: PackageManager.NameNotFoundException) { false }
            if (found) installed.add(pkg) else missing.add("$name ($pkg)")
        }
        Log.d(TAG, "Installed: ${installed.joinToString { AI_APPS[it] ?: it }}")
        if (missing.isNotEmpty()) Log.d(TAG, "Not installed: ${missing.joinToString()}")
        return installed
    }

    // ── Core per-app engine ─────────────────────────────────────────────────────

    /**
     * Measures foreground time for a single [packageName] between [startTime] and [endTime].
     *
     * - Calls queryEvents() for this package's time window.
     * - Pairs MOVE_TO_FOREGROUND with MOVE_TO_BACKGROUND to calculate each session's duration.
     * - If no BACKGROUND event arrives before [endTime], the app is still open: count up to [endTime].
     * - Uses TimeUnit floor division — 59 seconds → 0 minutes (no phantom minute inflation).
     */
    fun getUsageForApp(packageName: String, startTime: Long, endTime: Long): Long {
        val usageEvents = try {
            usageStatsManager.queryEvents(startTime, endTime)
        } catch (e: Exception) {
            Log.e(TAG, "queryEvents failed for $packageName: ${e.message}")
            return 0L
        } ?: return 0L

        var sessionStart = 0L
        var totalMs = 0L
        val event = UsageEvents.Event()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.packageName != packageName) continue

            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    // App came into view — record session start
                    sessionStart = event.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    // App went away — accumulate completed session
                    if (sessionStart > 0) {
                        totalMs += event.timeStamp - sessionStart
                        sessionStart = 0L
                    }
                }
            }
        }

        // App is still open at query time — count the open session up to endTime
        if (sessionStart > 0) {
            totalMs += endTime - sessionStart
        }

        return TimeUnit.MILLISECONDS.toMinutes(totalMs)
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Returns today's foreground usage for every tracked AI app.
     * - Installed apps get a real per-app query via getUsageForApp().
     * - Uninstalled apps return 0 without querying.
     * - Result is sorted by minutes descending (most-used first).
     * - Always re-queries live data — never returns cached values.
     */
    fun getAiUsage(): List<AiAppUsage> {
        if (!hasUsagePermission()) {
            Log.w(TAG, "Usage permission not granted — returning zeros")
            return AI_APPS.map { (pkg, name) ->
                AiAppUsage(pkg, name, 0L, isPackageInstalled(pkg))
            }
        }

        val startTime = getTodayStartTime()
        val endTime = System.currentTimeMillis()
        val installed = getInstalledAiApps().toSet()

        return AI_APPS.map { (pkg, name) ->
            val isInstalled = pkg in installed
            val mins = if (isInstalled) getUsageForApp(pkg, startTime, endTime) else 0L
            Log.d(TAG, "$name: $mins min | installed=$isInstalled")
            AiAppUsage(packageName = pkg, appName = name, minutes = mins, isInstalled = isInstalled)
        }.sortedByDescending { it.minutes }
    }

    /** Explicit no-cache entry point — always returns fresh data. */
    fun refreshUsage(): List<AiAppUsage> = getAiUsage()

    /** Returns the sum of all AI app minutes used today. */
    fun getTotalAiMinutesToday(): Long = getAiUsage().sumOf { it.minutes }

    /** Returns the sum of all installed AI app minutes used since Monday midnight. */
    fun getTotalAiMinutesThisWeek(): Long {
        if (!hasUsagePermission()) return 0L
        val weekStart = getStartOfWeekMillis()
        val now = System.currentTimeMillis()
        return getInstalledAiApps().sumOf { getUsageForApp(it, weekStart, now) }
    }

    /** Returns true if today's total usage has reached or exceeded [dailyLimitMinutes]. */
    fun isLimitReached(dailyLimitMinutes: Long): Boolean =
        getTotalAiMinutesToday() >= dailyLimitMinutes

    /** Returns minutes left before [dailyLimitMinutes] is hit, floored at 0. */
    fun getRemainingMinutes(dailyLimitMinutes: Long): Long =
        (dailyLimitMinutes - getTotalAiMinutesToday()).coerceAtLeast(0L)

    /**
     * Returns true if [packageName] is currently in the foreground —
     * defined as having a MOVE_TO_FOREGROUND event in the last 5 minutes
     * with no subsequent MOVE_TO_BACKGROUND.
     */
    fun isAppCurrentlyRunning(packageName: String): Boolean {
        if (!hasUsagePermission()) return false
        val now = System.currentTimeMillis()
        val events = try {
            usageStatsManager.queryEvents(now - RUNNING_WINDOW_MS, now)
        } catch (e: Exception) { return false } ?: return false

        val event = UsageEvents.Event()
        var lastType = -1
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != packageName) continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> lastType = UsageEvents.Event.MOVE_TO_FOREGROUND
                UsageEvents.Event.MOVE_TO_BACKGROUND -> lastType = UsageEvents.Event.MOVE_TO_BACKGROUND
            }
        }
        return lastType == UsageEvents.Event.MOVE_TO_FOREGROUND
    }

    /** Call at midnight to signal a day boundary. No in-memory state to clear currently. */
    fun clearTodayCache() {
        Log.d(TAG, "clearTodayCache() — day boundary crossed, next call queries fresh")
    }

    // ── Weekly bar-chart data ───────────────────────────────────────────────────

    /**
     * Returns one [DayUsage] per day of the current Mon–Sun week.
     * Future days return 0. Today is marked with isToday=true.
     */
    fun getDailyUsageForWeek(): List<DayUsage> {
        if (!hasUsagePermission()) {
            return listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                .map { DayUsage(it, 0L, false) }
        }

        val now = System.currentTimeMillis()
        val todayDoy = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val installed = getInstalledAiApps()

        val monday = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }

        return buildList {
            for (i in 0..6) {
                val startTime = monday.timeInMillis
                val dayOfYear = monday.get(Calendar.DAY_OF_YEAR)
                monday.add(Calendar.DAY_OF_YEAR, 1)

                if (startTime >= now) {
                    add(DayUsage(dayNames[i], 0L, dayOfYear == todayDoy))
                    continue
                }

                val endTime = monday.timeInMillis.coerceAtMost(now)
                val total = installed.sumOf { getUsageForApp(it, startTime, endTime) }
                add(DayUsage(dayNames[i], total, dayOfYear == todayDoy))
            }
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }
}
