package com.kingshot.macro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// ── Data classes ──────────────────────────────

data class MacroStep(
    val type: String,         // "tap" | "swipe" | "wait"
    val x1: Int = 0,          // % of screen width (0-100) for tap/swipe start
    val y1: Int = 0,          // % of screen height for tap/swipe start
    val x2: Int = 0,          // % for swipe end
    val y2: Int = 0,          // % for swipe end
    val durationMs: Long = 80,
    val waitAfterMs: Long = 800
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        put("x1", x1); put("y1", y1)
        put("x2", x2); put("y2", y2)
        put("durationMs", durationMs)
        put("waitAfterMs", waitAfterMs)
    }

    companion object {
        fun fromJson(j: JSONObject) = MacroStep(
            type = j.getString("type"),
            x1 = j.optInt("x1"), y1 = j.optInt("y1"),
            x2 = j.optInt("x2"), y2 = j.optInt("y2"),
            durationMs = j.optLong("durationMs", 80),
            waitAfterMs = j.optLong("waitAfterMs", 800)
        )

        fun tap(xPct: Int, yPct: Int, waitMs: Long = 800) =
            MacroStep("tap", xPct, yPct, waitAfterMs = waitMs)

        fun swipe(x1Pct: Int, y1Pct: Int, x2Pct: Int, y2Pct: Int, durMs: Long = 300, waitMs: Long = 800) =
            MacroStep("swipe", x1Pct, y1Pct, x2Pct, y2Pct, durMs, waitMs)

        fun wait(ms: Long) = MacroStep("wait", waitAfterMs = ms)
    }
}

data class Macro(
    val id: String,
    val name: String,
    val description: String,
    val isBuiltin: Boolean = false,
    val isLooping: Boolean = false,
    val steps: List<MacroStep> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("isBuiltin", isBuiltin)
        put("isLooping", isLooping)
        val arr = JSONArray()
        steps.forEach { arr.put(it.toJson()) }
        put("steps", arr)
    }

    companion object {
        fun fromJson(j: JSONObject): Macro {
            val arr = j.optJSONArray("steps") ?: JSONArray()
            val steps = mutableListOf<MacroStep>()
            for (i in 0 until arr.length()) {
                steps.add(MacroStep.fromJson(arr.getJSONObject(i)))
            }
            return Macro(
                id = j.getString("id"),
                name = j.getString("name"),
                description = j.optString("description"),
                isBuiltin = j.optBoolean("isBuiltin"),
                isLooping = j.optBoolean("isLooping"),
                steps = steps
            )
        }
    }
}

// ── Macro library ─────────────────────────────

object MacroLibrary {

    private const val PREFS = "kingshot_macros"
    private const val KEY_CUSTOM = "custom_macros_json"

    fun getBuiltinMacros(): List<Macro> = listOf(
        Macro(
            id = "clan_invitation",
            name = "Invitar al Clan (2 columnas)",
            description = "Toca cuadrícula 2 columnas, scroll cada 8 toques. Usa los % de la pantalla de Ajustes.",
            isBuiltin = true,
            isLooping = true
        ),
        Macro(
            id = "tap_center",
            name = "Toque Central",
            description = "Toca repetidamente el centro de la pantalla",
            isBuiltin = true,
            isLooping = true,
            steps = listOf(MacroStep.tap(50, 50, waitMs = 1000))
        ),
        Macro(
            id = "auto_collect",
            name = "Recolectar Recursos",
            description = "Toca esquinas y centro para recoger recursos visibles",
            isBuiltin = true,
            isLooping = true,
            steps = listOf(
                MacroStep.tap(20, 30, 600),
                MacroStep.tap(80, 30, 600),
                MacroStep.tap(20, 60, 600),
                MacroStep.tap(80, 60, 600),
                MacroStep.tap(50, 50, 600)
            )
        ),
        Macro(
            id = "scroll_down",
            name = "Scroll hacia abajo",
            description = "Hace scroll abajo continuamente",
            isBuiltin = true,
            isLooping = true,
            steps = listOf(MacroStep.swipe(50, 70, 50, 30, durMs = 400, waitMs = 1500))
        )
    )

    fun getCustomMacros(context: Context): List<Macro> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CUSTOM, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Macro.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    fun getAllMacros(context: Context): List<Macro> =
        getBuiltinMacros() + getCustomMacros(context)

    fun getById(context: Context, id: String): Macro? =
        getAllMacros(context).firstOrNull { it.id == id }

    fun saveMacro(context: Context, macro: Macro) {
        val current = getCustomMacros(context).toMutableList()
        val idx = current.indexOfFirst { it.id == macro.id }
        if (idx >= 0) current[idx] = macro else current.add(macro)
        persist(context, current)
    }

    fun deleteMacro(context: Context, id: String) {
        val current = getCustomMacros(context).filter { it.id != id }
        persist(context, current)
    }

    fun newId(): String = "custom_${UUID.randomUUID().toString().take(8)}"

    private fun persist(context: Context, list: List<Macro>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_CUSTOM, arr.toString()).apply()
    }
}
