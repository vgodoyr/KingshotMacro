package com.kingshot.macro

import android.content.Context
import android.content.SharedPreferences

object MacroController {

    const val ACTION_START = "com.kingshot.macro.START"
    const val ACTION_PAUSE = "com.kingshot.macro.PAUSE"
    const val ACTION_STOP = "com.kingshot.macro.STOP"
    const val ACTION_STATUS = "com.kingshot.macro.STATUS"
    const val EXTRA_STATUS = "status"

    const val STATUS_IDLE = 0
    const val STATUS_RUNNING = 1
    const val STATUS_PAUSED = 2

    private const val PREFS = "kingshot_macro_prefs"
    private const val KEY_TAP_DELAY = "tap_delay_ms"
    private const val KEY_TAPS_BEFORE_SCROLL = "taps_before_scroll"
    private const val KEY_COL1_X_PCT = "col1_x_pct"
    private const val KEY_COL2_X_PCT = "col2_x_pct"
    private const val KEY_ROW_START_Y_PCT = "row_start_y_pct"
    private const val KEY_ROW_HEIGHT_PCT = "row_height_pct"

    @Volatile
    var instance: KingshotAccessibilityService? = null

    @Volatile
    var currentStatus: Int = STATUS_IDLE

    fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getTapDelayMs(context: Context): Long =
        getPrefs(context).getLong(KEY_TAP_DELAY, 1500L)

    fun setTapDelayMs(context: Context, ms: Long) =
        getPrefs(context).edit().putLong(KEY_TAP_DELAY, ms).apply()

    fun getTapsBeforeScroll(context: Context): Int =
        getPrefs(context).getInt(KEY_TAPS_BEFORE_SCROLL, 8)

    fun setTapsBeforeScroll(context: Context, count: Int) =
        getPrefs(context).edit().putInt(KEY_TAPS_BEFORE_SCROLL, count).apply()

    fun getCol1XPct(context: Context): Float =
        getPrefs(context).getFloat(KEY_COL1_X_PCT, 0.25f)

    fun setCol1XPct(context: Context, pct: Float) =
        getPrefs(context).edit().putFloat(KEY_COL1_X_PCT, pct).apply()

    fun getCol2XPct(context: Context): Float =
        getPrefs(context).getFloat(KEY_COL2_X_PCT, 0.75f)

    fun setCol2XPct(context: Context, pct: Float) =
        getPrefs(context).edit().putFloat(KEY_COL2_X_PCT, pct).apply()

    fun getRowStartYPct(context: Context): Float =
        getPrefs(context).getFloat(KEY_ROW_START_Y_PCT, 0.30f)

    fun setRowStartYPct(context: Context, pct: Float) =
        getPrefs(context).edit().putFloat(KEY_ROW_START_Y_PCT, pct).apply()

    fun getRowHeightPct(context: Context): Float =
        getPrefs(context).getFloat(KEY_ROW_HEIGHT_PCT, 0.14f)

    fun setRowHeightPct(context: Context, pct: Float) =
        getPrefs(context).edit().putFloat(KEY_ROW_HEIGHT_PCT, pct).apply()
}
