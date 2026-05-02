package com.kingshot.macro

import android.content.Context
import android.content.SharedPreferences

object MacroController {

    // ── Broadcast actions ────────────────────────────────────────────────
    const val ACTION_START              = "com.kingshot.macro.START"
    const val ACTION_PAUSE              = "com.kingshot.macro.PAUSE"
    const val ACTION_STOP               = "com.kingshot.macro.STOP"
    const val ACTION_STATUS             = "com.kingshot.macro.STATUS"
    const val ACTION_RUN_GEMINI_ACTION  = "com.kingshot.macro.GEMINI_ACTION"
    const val ACTION_GEMINI_START       = "com.kingshot.macro.GEMINI_START"
    const val ACTION_GEMINI_STOP        = "com.kingshot.macro.GEMINI_STOP"
    const val ACTION_GEMINI_LOG         = "com.kingshot.macro.GEMINI_LOG"

    // ── Extras ───────────────────────────────────────────────────────────
    const val EXTRA_STATUS              = "status"
    const val EXTRA_MACRO_ID            = "macro_id"
    const val EXTRA_LOG_TEXT            = "log_text"

    // ── Status constants ─────────────────────────────────────────────────
    const val STATUS_IDLE               = 0
    const val STATUS_RUNNING            = 1
    const val STATUS_PAUSED             = 2

    // ── Runtime state ─────────────────────────────────────────────────────
    @Volatile var instance: KingshotAccessibilityService? = null
    @Volatile var currentStatus: Int = STATUS_IDLE
    @Volatile var activeMacroId: String = "clan_invitation"
    @Volatile var geminiRunning: Boolean = false

    // ── SharedPreferences ─────────────────────────────────────────────────
    private const val PREFS = "kingshot_macro_prefs"

    fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Tap/grid settings
    fun getTapDelayMs(ctx: Context): Long =
        getPrefs(ctx).getLong("tap_delay_ms", 1500L)
    fun setTapDelayMs(ctx: Context, ms: Long) =
        getPrefs(ctx).edit().putLong("tap_delay_ms", ms).apply()

    fun getTapsBeforeScroll(ctx: Context): Int =
        getPrefs(ctx).getInt("taps_before_scroll", 8)
    fun setTapsBeforeScroll(ctx: Context, v: Int) =
        getPrefs(ctx).edit().putInt("taps_before_scroll", v).apply()

    fun getCol1XPct(ctx: Context): Float =
        getPrefs(ctx).getFloat("col1_x_pct", 0.25f)
    fun setCol1XPct(ctx: Context, v: Float) =
        getPrefs(ctx).edit().putFloat("col1_x_pct", v).apply()

    fun getCol2XPct(ctx: Context): Float =
        getPrefs(ctx).getFloat("col2_x_pct", 0.75f)
    fun setCol2XPct(ctx: Context, v: Float) =
        getPrefs(ctx).edit().putFloat("col2_x_pct", v).apply()

    fun getRowStartYPct(ctx: Context): Float =
        getPrefs(ctx).getFloat("row_start_y_pct", 0.30f)
    fun setRowStartYPct(ctx: Context, v: Float) =
        getPrefs(ctx).edit().putFloat("row_start_y_pct", v).apply()

    fun getRowHeightPct(ctx: Context): Float =
        getPrefs(ctx).getFloat("row_height_pct", 0.14f)
    fun setRowHeightPct(ctx: Context, v: Float) =
        getPrefs(ctx).edit().putFloat("row_height_pct", v).apply()

    // Gemini settings
    fun getGeminiApiKey(ctx: Context): String =
        getPrefs(ctx).getString("gemini_api_key", "AIzaSyAbSeozeGz5ArBv7658D4AejdchfXjzhXs") ?: ""
    fun setGeminiApiKey(ctx: Context, key: String) =
        getPrefs(ctx).edit().putString("gemini_api_key", key).apply()

    fun getAutoPlayMode(ctx: Context): String =
        getPrefs(ctx).getString("auto_play_mode", "all") ?: "all"
    fun setAutoPlayMode(ctx: Context, mode: String) =
        getPrefs(ctx).edit().putString("auto_play_mode", mode).apply()

    fun getAutoPlayIntervalS(ctx: Context): Int =
        getPrefs(ctx).getInt("auto_play_interval_s", 5)
    fun setAutoPlayIntervalS(ctx: Context, s: Int) =
        getPrefs(ctx).edit().putInt("auto_play_interval_s", s).apply()

    fun getActiveMacroId(ctx: Context): String =
        getPrefs(ctx).getString("active_macro_id", "clan_invitation") ?: "clan_invitation"
    fun setActiveMacroId(ctx: Context, id: String) {
        activeMacroId = id
        getPrefs(ctx).edit().putString("active_macro_id", id).apply()
    }
}
