package com.skyler.caffeinate

import android.content.Context

object CaffeinateState {

    private const val PREFS_NAME = "caffeinate_prefs"
    private const val KEY_IS_ACTIVE = "is_active"
    private const val KEY_ORIGINAL_TIMEOUT = "original_timeout"
    private const val DEFAULT_TIMEOUT = 60_000

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IS_ACTIVE, false)

    fun setActive(context: Context, active: Boolean) {
        prefs(context).edit().putBoolean(KEY_IS_ACTIVE, active).apply()
    }

    fun saveOriginalTimeout(context: Context, timeout: Int) {
        prefs(context).edit().putInt(KEY_ORIGINAL_TIMEOUT, timeout).apply()
    }

    fun getOriginalTimeout(context: Context): Int =
        prefs(context).getInt(KEY_ORIGINAL_TIMEOUT, DEFAULT_TIMEOUT)
}
