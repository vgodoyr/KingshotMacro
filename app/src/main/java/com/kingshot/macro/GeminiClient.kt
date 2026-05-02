package com.kingshot.macro

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class GeminiAction(
    val action: String,     // "tap" | "swipe" | "wait" | "done" | "error"
    val x: Int = 50,        // % of screen width
    val y: Int = 50,        // % of screen height
    val x2: Int = 0,        // % for swipe end X
    val y2: Int = 0,        // % for swipe end Y
    val durationMs: Long = 200,
    val reason: String = ""
)

object GeminiClient {

    private const val TAG = "GeminiClient"
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key="

    private val SYSTEM_PROMPT = """
Eres un asistente de juego para Kingshot (juego de estrategia medieval móvil).
Tu tarea es analizar la captura de pantalla del juego y decidir la SIGUIENTE ACCIÓN ÓPTIMA.

REGLAS IMPORTANTES:
- Responde ÚNICAMENTE con un objeto JSON válido, sin texto extra ni markdown.
- Usa coordenadas como PORCENTAJE de la pantalla (0-100), donde (0,0) es la esquina superior izquierda y (100,100) la inferior derecha.
- Si hay un popup/diálogo visible, primero ciérralo o interactúa con él.
- Si hay recursos listos para recoger en edificios (ícono de cofre/saco encima del edificio), recógelos.
- Si hay una cola de construcción vacía y hay recursos, inicia una mejora.
- Si el entrenamiento de tropas terminó, reinícialo.
- Prioriza acciones urgentes sobre rutinarias.

FORMATO DE RESPUESTA (JSON estricto):
{"action":"tap","x":50,"y":70,"reason":"Tocando botón de reclutar héroe gratis"}
{"action":"swipe","x":50,"y":70,"x2":50,"y2":30,"reason":"Haciendo scroll hacia arriba"}
{"action":"wait","reason":"Esperando que cargue la pantalla"}
{"action":"done","reason":"No hay acciones disponibles en este momento"}

MODOS DE JUEGO DISPONIBLES:
- "all": cualquier acción útil
- "train": solo entrenar tropas (Cuartel/Establo/Campo de tiro)
- "research": solo investigar tecnologías
- "recruit": solo reclutar héroes gratis
- "collect": solo recolectar recursos de edificios
- "hunt": solo cazar bestias en misiones de información
- "build": solo mejorar edificios

El modo activo actual es: %MODE%
""".trim()

    fun analyzeScreen(
        apiKey: String,
        screenshot: Bitmap,
        gameMode: String,
        callback: (GeminiAction?) -> Unit
    ) {
        Thread {
            val result = runCatching {
                val jpegBytes = compressBitmap(screenshot)
                val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                val prompt = SYSTEM_PROMPT.replace("%MODE%", gameMode)
                val action = callGemini(apiKey, b64, prompt)
                action
            }.onFailure { Log.e(TAG, "analyzeScreen error", it) }.getOrNull()

            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }

    private fun compressBitmap(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        // Scale down if too large (max 512px wide for API efficiency)
        val scaled = if (bitmap.width > 512) {
            val ratio = 512f / bitmap.width
            Bitmap.createScaledBitmap(bitmap, 512, (bitmap.height * ratio).toInt(), true)
        } else bitmap
        scaled.compress(Bitmap.CompressFormat.JPEG, 55, out)
        return out.toByteArray()
    }

    private fun callGemini(apiKey: String, imageBase64: String, prompt: String): GeminiAction {
        val url = URL(ENDPOINT + apiKey)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000

            val body = buildRequestJson(imageBase64, prompt)
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body) }

            val responseCode = conn.responseCode
            val responseText = if (responseCode == 200) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                Log.e(TAG, "HTTP $responseCode: $err")
                return GeminiAction("error", reason = "HTTP $responseCode")
            }

            return parseResponse(responseText)
        } finally {
            conn.disconnect()
        }
    }

    private fun buildRequestJson(imageBase64: String, prompt: String): String {
        val imagePart = JSONObject().apply {
            put("inline_data", JSONObject().apply {
                put("mime_type", "image/jpeg")
                put("data", imageBase64)
            })
        }
        val textPart = JSONObject().apply { put("text", prompt) }
        val parts = JSONArray().apply { put(imagePart); put(textPart) }
        val content = JSONObject().apply { put("parts", parts) }
        val contents = JSONArray().apply { put(content) }
        val genConfig = JSONObject().apply {
            put("temperature", 0.1)
            put("maxOutputTokens", 256)
        }
        return JSONObject().apply {
            put("contents", contents)
            put("generationConfig", genConfig)
        }.toString()
    }

    private fun parseResponse(responseText: String): GeminiAction {
        return try {
            val root = JSONObject(responseText)
            val candidates = root.getJSONArray("candidates")
            val content = candidates.getJSONObject(0).getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val text = parts.getJSONObject(0).getString("text").trim()

            // Extract JSON from response (may be wrapped in markdown code block)
            val jsonStr = extractJson(text)
            val json = JSONObject(jsonStr)

            GeminiAction(
                action = json.optString("action", "wait"),
                x = json.optInt("x", 50),
                y = json.optInt("y", 50),
                x2 = json.optInt("x2", 0),
                y2 = json.optInt("y2", 0),
                durationMs = json.optLong("durationMs", 200),
                reason = json.optString("reason", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseResponse failed: $responseText", e)
            GeminiAction("error", reason = "Parse error: ${e.message}")
        }
    }

    private fun extractJson(text: String): String {
        // Try to extract JSON object from text (handle markdown code blocks)
        val trimmed = text.trim()
        if (trimmed.startsWith("{")) return trimmed
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1)
        return "{\"action\":\"wait\",\"reason\":\"Could not parse: $trimmed\"}"
    }
}
