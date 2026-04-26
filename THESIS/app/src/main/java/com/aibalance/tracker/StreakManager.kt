package com.aibalance.tracker

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StreakManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("streak_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_STREAK = "streak_count"
        const val KEY_LAST_LOGIN = "last_login_date"
    }

    /**
     * Compares today's date with the last recorded login and updates the streak accordingly:
     *
     *  - First ever open  → streak = 1, record today
     *  - Same day again   → no change (already counted this calendar day)
     *  - Day after last   → streak + 1, record today
     *  - Gap of 2+ days   → streak resets to 1 (missed at least one day)
     *
     * Call this once in MainActivity.onCreate() so it fires on every app open.
     */
    fun checkAndUpdateStreak() {
        val today = getTodayDateString()
        val lastLogin = prefs.getString(KEY_LAST_LOGIN, null)

        when {
            lastLogin == null -> {
                // First time the app has ever been opened
                prefs.edit()
                    .putInt(KEY_STREAK, 1)
                    .putString(KEY_LAST_LOGIN, today)
                    .apply()
            }
            lastLogin == today -> {
                // App was already opened today — streak unchanged
                return
            }
            isYesterday(lastLogin) -> {
                // Consecutive day — extend the streak
                val newStreak = prefs.getInt(KEY_STREAK, 1) + 1
                prefs.edit()
                    .putInt(KEY_STREAK, newStreak)
                    .putString(KEY_LAST_LOGIN, today)
                    .apply()
            }
            else -> {
                // Skipped one or more days — reset streak to 1
                prefs.edit()
                    .putInt(KEY_STREAK, 1)
                    .putString(KEY_LAST_LOGIN, today)
                    .apply()
            }
        }
    }

    /** Returns the current streak count. Returns 0 before the first ever open. */
    fun getCurrentStreak(): Int = prefs.getInt(KEY_STREAK, 0)

    /** Returns today's date as "yyyy-MM-dd". Used for stable daily comparisons. */
    private fun getTodayDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /** Returns true if [dateStr] (formatted "yyyy-MM-dd") represents yesterday. */
    private fun isYesterday(dateStr: String): Boolean {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }.time
        return dateStr == sdf.format(yesterday)
    }
}
