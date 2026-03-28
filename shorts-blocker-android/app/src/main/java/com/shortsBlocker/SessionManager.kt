package com.shortsBlocker

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper

object SessionManager {

    private const val PREFS_NAME = "session_prefs"
    private const val KEY_SESSION_COUNT = "session_count"
    private const val KEY_LAST_RESET_DATE = "last_reset_date"
    private const val KEY_SESSION_ACTIVE = "session_active"
    private const val KEY_SESSION_END_TIME = "session_end_time"
    private const val KEY_BLOCKING_DISABLED_PERMANENT = "blocking_disabled_permanent"

    const val MAX_SESSIONS_PER_DAY = 5
    const val SESSION_DURATION_MS = 10 * 60 * 1000L // 10 minutes

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun todayString(): String {
        val cal = java.util.Calendar.getInstance()
        return "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH)}-${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
    }

    private fun resetIfNewDay(context: Context) {
        val prefs = getPrefs(context)
        val lastReset = prefs.getString(KEY_LAST_RESET_DATE, "")
        val today = todayString()
        if (lastReset != today) {
            prefs.edit()
                .putInt(KEY_SESSION_COUNT, 0)
                .putString(KEY_LAST_RESET_DATE, today)
                .putBoolean(KEY_SESSION_ACTIVE, false)
                .putLong(KEY_SESSION_END_TIME, 0L)
                .apply()
        }
    }

    fun getSessionsUsedToday(context: Context): Int {
        resetIfNewDay(context)
        return getPrefs(context).getInt(KEY_SESSION_COUNT, 0)
    }

    fun getSessionsRemainingToday(context: Context): Int {
        return MAX_SESSIONS_PER_DAY - getSessionsUsedToday(context)
    }

    fun canStartSession(context: Context): Boolean {
        if (isBlockingPermanentlyDisabled(context)) return true
        resetIfNewDay(context)
        return getSessionsUsedToday(context) < MAX_SESSIONS_PER_DAY
    }

    fun isSessionActive(context: Context): Boolean {
        resetIfNewDay(context)
        val prefs = getPrefs(context)
        val active = prefs.getBoolean(KEY_SESSION_ACTIVE, false)
        if (active) {
            val endTime = prefs.getLong(KEY_SESSION_END_TIME, 0L)
            if (System.currentTimeMillis() >= endTime) {
                endSession(context)
                return false
            }
        }
        return active
    }

    fun getRemainingSessionTimeMs(context: Context): Long {
        if (!isSessionActive(context)) return 0L
        val endTime = getPrefs(context).getLong(KEY_SESSION_END_TIME, 0L)
        return maxOf(0L, endTime - System.currentTimeMillis())
    }

    fun startSession(context: Context) {
        resetIfNewDay(context)
        val prefs = getPrefs(context)
        val count = prefs.getInt(KEY_SESSION_COUNT, 0)
        val endTime = System.currentTimeMillis() + SESSION_DURATION_MS
        prefs.edit()
            .putInt(KEY_SESSION_COUNT, count + 1)
            .putBoolean(KEY_SESSION_ACTIVE, true)
            .putLong(KEY_SESSION_END_TIME, endTime)
            .apply()
    }

    fun endSession(context: Context) {
        getPrefs(context).edit()
            .putBoolean(KEY_SESSION_ACTIVE, false)
            .putLong(KEY_SESSION_END_TIME, 0L)
            .apply()
    }

    fun setBlockingPermanentlyDisabled(context: Context, disabled: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_BLOCKING_DISABLED_PERMANENT, disabled)
            .apply()
    }

    fun isBlockingPermanentlyDisabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BLOCKING_DISABLED_PERMANENT, false)
    }

    fun shouldBlockShorts(context: Context): Boolean {
        if (isBlockingPermanentlyDisabled(context)) return false
        if (isSessionActive(context)) return false
        return true
    }
}
