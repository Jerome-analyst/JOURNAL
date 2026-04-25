package com.aibalance.tracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    private const val CHANNEL_REMINDERS = "ai_reminders"
    private const val CHANNEL_ALERTS = "ai_alerts"
    private const val CHANNEL_STREAK = "ai_streak"
    private const val CHANNEL_WEEKLY = "ai_weekly"

    const val NOTIF_GENTLE = 1001
    const val NOTIF_LIMIT = 1002
    const val NOTIF_STREAK = 1003
    const val NOTIF_WEEKLY = 1004

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            listOf(
                Triple(CHANNEL_REMINDERS, "Gentle Reminders", NotificationManager.IMPORTANCE_DEFAULT),
                Triple(CHANNEL_ALERTS, "Limit Alerts", NotificationManager.IMPORTANCE_HIGH),
                Triple(CHANNEL_STREAK, "Streak Reminders", NotificationManager.IMPORTANCE_DEFAULT),
                Triple(CHANNEL_WEEKLY, "Weekly Summary", NotificationManager.IMPORTANCE_LOW)
            ).forEach { (id, name, importance) ->
                nm.createNotificationChannel(NotificationChannel(id, name, importance))
            }
        }
    }

    fun sendGentleReminder(context: Context, used: Long, limit: Long) {
        val pct = (used * 100 / limit.coerceAtLeast(1)).toInt()
        notify(
            context, CHANNEL_REMINDERS, NOTIF_GENTLE,
            "You're at $pct% of your AI limit",
            "Used $used min of $limit min today. Stay mindful and balanced!"
        )
    }

    fun sendLimitAlert(context: Context, used: Long) {
        notify(
            context, CHANNEL_ALERTS, NOTIF_LIMIT,
            "Daily AI limit reached!",
            "You've used $used min today. Time for a cooldown break — you've got this!"
        )
    }

    fun sendStreakReminder(context: Context, streak: Int) {
        val title = if (streak > 0) "Keep your $streak-day streak alive!" else "Start your streak today!"
        val body = "Stay within your AI limit today to stay on track."
        notify(context, CHANNEL_STREAK, NOTIF_STREAK, title, body)
    }

    fun sendWeeklySummary(context: Context, weekly: Long, weeklyLimit: Long) {
        val pct = (weekly * 100 / weeklyLimit.coerceAtLeast(1)).toInt()
        notify(
            context, CHANNEL_WEEKLY, NOTIF_WEEKLY,
            "Your weekly AI summary is ready",
            "This week: $weekly min used ($pct% of $weeklyLimit min limit). Great job staying balanced!"
        )
    }

    private fun notify(context: Context, channel: String, id: Int, title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notif)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted on Android 13+
        }
    }
}
