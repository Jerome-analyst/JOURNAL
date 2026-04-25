package com.aibalance.tracker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

class UsageCheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val tracker = AiUsageTracker(ctx)

        if (!tracker.hasUsagePermission()) return Result.success()

        NotificationHelper.createChannels(ctx)

        val dailyLimit = prefs.getInt(SettingsActivity.KEY_DAILY_LIMIT_MINUTES, 60).toLong()
        val bonus = prefs.getInt(SettingsActivity.KEY_BONUS_MINUTES_EARNED, 0).toLong()
        val effectiveLimit = (dailyLimit + bonus).coerceAtLeast(1L)
        val todayMin = tracker.getTotalAiMinutesToday()
        val streak = prefs.getInt(SettingsActivity.KEY_STREAK_DAYS, 0)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // Gentle reminder at 70–99% of limit (only fire once per day via NOTIF_GENTLE id)
        if (prefs.getBoolean(SettingsActivity.KEY_GENTLE_REMINDERS, true)) {
            val pct = (todayMin * 100 / effectiveLimit).toInt()
            if (pct in 70..99) {
                NotificationHelper.sendGentleReminder(ctx, todayMin, effectiveLimit)
            }
        }

        // Limit-reached alert
        if (prefs.getBoolean(SettingsActivity.KEY_LIMIT_ALERT, true)) {
            if (todayMin >= effectiveLimit) {
                NotificationHelper.sendLimitAlert(ctx, todayMin)
            }
        }

        // Morning streak reminder at 8 AM
        if (prefs.getBoolean(SettingsActivity.KEY_STREAK_NOTIFICATIONS, true) && hour == 8) {
            NotificationHelper.sendStreakReminder(ctx, streak)
        }

        // Weekly summary on Sunday evenings at 8 PM
        if (prefs.getBoolean(SettingsActivity.KEY_WEEKLY_SUMMARY, true)) {
            val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            if (dow == Calendar.SUNDAY && hour == 20) {
                val weekly = tracker.getTotalAiMinutesThisWeek()
                NotificationHelper.sendWeeklySummary(ctx, weekly, dailyLimit * 7)
            }
        }

        return Result.success()
    }

    companion object {
        private const val WORK_TAG = "ai_usage_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageCheckWorker>(30, TimeUnit.MINUTES)
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        }
    }
}
