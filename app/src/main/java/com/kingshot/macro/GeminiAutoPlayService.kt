package com.kingshot.macro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

class GeminiAutoPlayService : Service() {

    private val TAG = "GeminiAutoPlay"
    private val NOTIF_ID = 2001
    private val CHANNEL_ID = "kingshot_gemini"

    private val handler = Handler(Looper.getMainLooper())
    private val actionLog = ArrayDeque<String>(20)
    private var isRunning = false

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MacroController.ACTION_GEMINI_START -> startLoop()
                MacroController.ACTION_GEMINI_STOP  -> stopLoop()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Listo — esperando inicio"))
        val filter = IntentFilter().apply {
            addAction(MacroController.ACTION_GEMINI_START)
            addAction(MacroController.ACTION_GEMINI_STOP)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(commandReceiver, filter, 4)
        } else {
            registerReceiver(commandReceiver, filter)
        }
        Log.d(TAG, "GeminiAutoPlayService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == MacroController.ACTION_GEMINI_START) startLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLoop()
        try { unregisterReceiver(commandReceiver) } catch (e: Exception) { /* ignored */ }
        MacroController.geminiRunning = false
    }

    // ── Loop control ─────────────────────────────────────────────────────

    private fun startLoop() {
        if (isRunning) return
        isRunning = true
        MacroController.geminiRunning = true
        updateNotification("Auto IA activo…")
        scheduleNextCycle(500)
        Log.d(TAG, "Auto-play loop started")
    }

    private fun stopLoop() {
        isRunning = false
        MacroController.geminiRunning = false
        handler.removeCallbacksAndMessages(null)
        updateNotification("Detenido")
        Log.d(TAG, "Auto-play loop stopped")
    }

    private fun scheduleNextCycle(delayMs: Long) {
        if (!isRunning) return
        handler.postDelayed({ runCycle() }, delayMs)
    }

    // ── One auto-play cycle: screenshot → Gemini → action ────────────────

    private fun runCycle() {
        if (!isRunning) return

        val apiKey = MacroController.getGeminiApiKey(this)
        if (apiKey.isBlank()) {
            log("⚠ API key de Gemini no configurada")
            stopLoop()
            return
        }

        val svc = MacroController.instance
        if (svc == null) {
            log("⚠ Servicio de accesibilidad no activo")
            scheduleNextCycle(3_000)
            return
        }

        // Capture screenshot
        svc.takeScreenshotBitmap { bitmap ->
            if (bitmap == null) {
                log("⚠ No se pudo capturar la pantalla (requiere Android 11+)")
                scheduleNextCycle(5_000)
                return@takeScreenshotBitmap
            }

            val mode = MacroController.getAutoPlayMode(this)
            updateNotification("Analizando pantalla…")

            GeminiClient.analyzeScreen(apiKey, bitmap, mode) { action ->
                bitmap.recycle()

                if (action == null || action.action == "error") {
                    log("⚠ Error al contactar Gemini")
                    scheduleNextCycle(8_000)
                    return@analyzeScreen
                }

                when (action.action) {
                    "done" -> {
                        log("✓ ${action.reason.ifBlank { "Sin acciones disponibles" }}")
                        scheduleNextCycle(intervalMs())
                    }
                    "wait" -> {
                        log("⏳ ${action.reason.ifBlank { "Esperando…" }}")
                        scheduleNextCycle(intervalMs())
                    }
                    "tap", "swipe" -> {
                        val desc = if (action.action == "tap")
                            "Toque (${action.x}%, ${action.y}%)"
                        else
                            "Deslizar →(${action.x2}%, ${action.y2}%)"
                        log("▶ $desc — ${action.reason}")
                        updateNotification(action.reason.take(60))
                        executeGeminiAction(action)
                        scheduleNextCycle(intervalMs())
                    }
                    else -> scheduleNextCycle(intervalMs())
                }
            }
        }
    }

    private fun executeGeminiAction(action: GeminiAction) {
        val intent = Intent(MacroController.ACTION_RUN_GEMINI_ACTION).apply {
            setPackage(packageName)
            putExtra("type", action.action)
            putExtra("x1", action.x)
            putExtra("y1", action.y)
            putExtra("x2", action.x2)
            putExtra("y2", action.y2)
            putExtra("dur", action.durationMs)
        }
        sendBroadcast(intent)
    }

    private fun intervalMs(): Long =
        MacroController.getAutoPlayIntervalS(this) * 1000L

    // ── Log management ───────────────────────────────────────────────────

    private fun log(text: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val entry = "[$ts] $text"
        Log.d(TAG, entry)
        if (actionLog.size >= 20) actionLog.removeFirst()
        actionLog.addLast(entry)
        broadcastLog(entry)
    }

    private fun broadcastLog(text: String) {
        sendBroadcast(Intent(MacroController.ACTION_GEMINI_LOG).apply {
            setPackage(packageName)
            putExtra(MacroController.EXTRA_LOG_TEXT, text)
        })
    }

    fun getLog(): List<String> = actionLog.toList()

    // ── Notification ─────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Kingshot Auto IA", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Estado del modo automático con Gemini" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= 23)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getActivity(this, 0, openIntent, flags)

        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Kingshot Auto IA")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentIntent(pi)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Kingshot Auto IA")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentIntent(pi)
                .setOngoing(true)
                .build()
        }
    }

    private fun updateNotification(text: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification(text))
    }
}
