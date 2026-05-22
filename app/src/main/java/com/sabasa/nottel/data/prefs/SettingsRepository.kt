package com.sabasa.nottel.data.prefs

import android.content.Context

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("nottel_settings", Context.MODE_PRIVATE)

    // --- Forwarding state ---

    var forwardingEnabled: Boolean
        get() = prefs.getBoolean(KEY_FORWARDING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_FORWARDING_ENABLED, value).apply()

    // --- Quiet hours ---

    var quietHoursEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUIET_HOURS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_QUIET_HOURS_ENABLED, value).apply()

    var quietHoursStart: Int  // 0–23
        get() = prefs.getInt(KEY_QUIET_HOURS_START, 23)
        set(value) = prefs.edit().putInt(KEY_QUIET_HOURS_START, value).apply()

    var quietHoursEnd: Int  // 0–23
        get() = prefs.getInt(KEY_QUIET_HOURS_END, 7)
        set(value) = prefs.edit().putInt(KEY_QUIET_HOURS_END, value).apply()

    // --- Batch settings ---

    var batchMaxCount: Int  // flush early if this many items queued
        get() = prefs.getInt(KEY_BATCH_MAX_COUNT, 5)
        set(value) = prefs.edit().putInt(KEY_BATCH_MAX_COUNT, value).apply()

    var batchMaxWaitMs: Long  // flush after this many ms since first item
        get() = prefs.getLong(KEY_BATCH_MAX_WAIT_MS, 3_000L)
        set(value) = prefs.edit().putLong(KEY_BATCH_MAX_WAIT_MS, value).apply()

    // --- Message formatting ---

    var includeAppName: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_APP_NAME, true)
        set(value) = prefs.edit().putBoolean(KEY_INCLUDE_APP_NAME, value).apply()

    // --- App filter ---

    // Empty set = allow all apps
    var allowedApps: Set<String>
        get() = prefs.getStringSet(KEY_ALLOWED_APPS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_ALLOWED_APPS, value).apply()

    fun isAppAllowed(packageName: String): Boolean {
        if (allowedApps.isEmpty()) return true
        return packageName in allowedApps
    }

    // --- Quiet hours helper ---

    fun isQuietHoursActive(): Boolean {
        if (!quietHoursEnabled) return false
        val now = java.util.Calendar.getInstance()
        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
        return if (quietHoursStart <= quietHoursEnd) {
            // same-day range e.g. 09:00 → 17:00
            hour in quietHoursStart until quietHoursEnd
        } else {
            // overnight range e.g. 23:00 → 07:00
            hour >= quietHoursStart || hour < quietHoursEnd
        }
    }

    companion object {
        private const val KEY_FORWARDING_ENABLED   = "forwarding_enabled"
        private const val KEY_QUIET_HOURS_ENABLED  = "quiet_hours_enabled"
        private const val KEY_QUIET_HOURS_START    = "quiet_hours_start"
        private const val KEY_QUIET_HOURS_END      = "quiet_hours_end"
        private const val KEY_BATCH_MAX_COUNT      = "batch_max_count"
        private const val KEY_BATCH_MAX_WAIT_MS    = "batch_max_wait_ms"
        private const val KEY_INCLUDE_APP_NAME     = "include_app_name"
        private const val KEY_ALLOWED_APPS         = "allowed_apps"
    }
}