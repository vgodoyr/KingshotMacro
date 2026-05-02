package com.kingshot.macro

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.lang.reflect.Proxy
import java.util.concurrent.Executors

class KingshotAccessibilityService : AccessibilityService() {

    private val TAG = "KingshotMacro"
    private val handler = Handler(Looper.getMainLooper())

    private var screenWidth = 0
    private var screenHeight = 0

    // Loop state
    private var stepIndex = 0
    private var loopCount = 0
    private var currentMacroId: String = "clan_invitation"

    // Clan-invitation state (special built-in)
    private var clanTapCount = 0
    private var clanCurrentCol = 0

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MacroController.ACTION_START -> {
                    val id = intent.getStringExtra(MacroController.EXTRA_MACRO_ID)
                    startMacro(id ?: "clan_invitation")
                }
                MacroController.ACTION_PAUSE -> pauseMacro()
                MacroController.ACTION_STOP -> stopMacro()
                MacroController.ACTION_RUN_GEMINI_ACTION -> {
                    val type = intent.getStringExtra("type") ?: return
                    val x1 = intent.getIntExtra("x1", 0)
                    val y1 = intent.getIntExtra("y1", 0)
                    val x2 = intent.getIntExtra("x2", 0)
                    val y2 = intent.getIntExtra("y2", 0)
                    val dur = intent.getLongExtra("dur", 80L)
                    runSingleAction(type, x1, y1, x2, y2, dur)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        MacroController.instance = this
        loadScreenMetrics()

        val filter = IntentFilter().apply {
            addAction(MacroController.ACTION_START)
            addAction(MacroController.ACTION_PAUSE)
            addAction(MacroController.ACTION_STOP)
            addAction(MacroController.ACTION_RUN_GEMINI_ACTION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            // RECEIVER_NOT_EXPORTED = 4
            registerReceiver(commandReceiver, filter, 4)
        } else {
            registerReceiver(commandReceiver, filter)
        }
        Log.d(TAG, "AccessibilityService connected. Screen: ${screenWidth}x${screenHeight}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { handler.removeCallbacksAndMessages(null) }

    override fun onDestroy() {
        super.onDestroy()
        stopMacro()
        try { unregisterReceiver(commandReceiver) } catch (e: Exception) { /* ignored */ }
        MacroController.instance = null
    }

    private fun loadScreenMetrics() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    fun getScreenWidth() = screenWidth
    fun getScreenHeight() = screenHeight

    // ── Macro lifecycle ───────────────────────

    private fun startMacro(macroId: String) {
        if (MacroController.currentStatus == MacroController.STATUS_RUNNING) return
        currentMacroId = macroId
        stepIndex = 0
        loopCount = 0
        clanTapCount = 0
        clanCurrentCol = 0
        MacroController.currentStatus = MacroController.STATUS_RUNNING
        broadcastStatus(MacroController.STATUS_RUNNING)
        Log.d(TAG, "Macro $macroId started")
        scheduleNextStep(MacroController.getTapDelayMs(this))
    }

    private fun pauseMacro() {
        if (MacroController.currentStatus == MacroController.STATUS_PAUSED) {
            MacroController.currentStatus = MacroController.STATUS_RUNNING
            broadcastStatus(MacroController.STATUS_RUNNING)
            scheduleNextStep(MacroController.getTapDelayMs(this))
        } else {
            MacroController.currentStatus = MacroController.STATUS_PAUSED
            broadcastStatus(MacroController.STATUS_PAUSED)
            handler.removeCallbacksAndMessages(null)
        }
    }

    private fun stopMacro() {
        MacroController.currentStatus = MacroController.STATUS_IDLE
        broadcastStatus(MacroController.STATUS_IDLE)
        handler.removeCallbacksAndMessages(null)
        stepIndex = 0
        loopCount = 0
        clanTapCount = 0
        clanCurrentCol = 0
        Log.d(TAG, "Macro stopped")
    }

    private fun scheduleNextStep(delayMs: Long) {
        handler.postDelayed({ executeNextStep() }, delayMs)
    }

    private fun executeNextStep() {
        if (MacroController.currentStatus != MacroController.STATUS_RUNNING) return

        if (currentMacroId == "clan_invitation") {
            executeClanInvitationStep()
            return
        }

        val macro = MacroLibrary.getById(this, currentMacroId) ?: run {
            stopMacro(); return
        }
        if (macro.steps.isEmpty()) { stopMacro(); return }

        val step = macro.steps[stepIndex]
        runStep(step)

        stepIndex++
        if (stepIndex >= macro.steps.size) {
            stepIndex = 0
            loopCount++
            if (!macro.isLooping) { stopMacro(); return }
        }
        scheduleNextStep(step.waitAfterMs)
    }

    private fun runStep(step: MacroStep) {
        when (step.type) {
            "tap" -> tap(pctX(step.x1), pctY(step.y1))
            "swipe" -> swipe(pctX(step.x1), pctY(step.y1), pctX(step.x2), pctY(step.y2), step.durationMs)
            "wait" -> { /* nothing */ }
        }
    }

    private fun runSingleAction(type: String, x1: Int, y1: Int, x2: Int, y2: Int, dur: Long) {
        when (type) {
            "tap" -> tap(pctX(x1), pctY(y1))
            "swipe" -> swipe(pctX(x1), pctY(y1), pctX(x2), pctY(y2), dur)
        }
    }

    private fun pctX(p: Int) = (screenWidth * (p / 100f)).toInt().coerceIn(0, screenWidth - 1)
    private fun pctY(p: Int) = (screenHeight * (p / 100f)).toInt().coerceIn(0, screenHeight - 1)

    // ── Built-in clan invitation logic ────────

    private fun executeClanInvitationStep() {
        val tapsBeforeScroll = MacroController.getTapsBeforeScroll(this)

        if (clanTapCount > 0 && clanTapCount % tapsBeforeScroll == 0) {
            // scroll up to load more
            val sx = (screenWidth * 0.5f).toInt()
            val sy1 = (screenHeight * 0.7f).toInt()
            val sy2 = (screenHeight * 0.3f).toInt()
            swipe(sx, sy1, sx, sy2, 300)
            clanTapCount++          // skip a "step"; next iteration starts fresh column 0
            clanCurrentCol = 0
            scheduleNextStep(1000)
            return
        }

        val col1 = (screenWidth * MacroController.getCol1XPct(this)).toInt()
        val col2 = (screenWidth * MacroController.getCol2XPct(this)).toInt()
        val rowStart = (screenHeight * MacroController.getRowStartYPct(this)).toInt()
        val rowH = (screenHeight * MacroController.getRowHeightPct(this)).toInt()

        val localTap = clanTapCount % tapsBeforeScroll
        val row = localTap / 2
        val x = if (clanCurrentCol == 0) col1 else col2
        val y = rowStart + row * rowH

        tap(x, y)
        clanCurrentCol = (clanCurrentCol + 1) % 2
        clanTapCount++
        scheduleNextStep(MacroController.getTapDelayMs(this))
    }

    // ── Gesture dispatch (reflection-based for API 23 compile target) ──

    @SuppressLint("NewApi")
    private fun tap(x: Int, y: Int) {
        if (Build.VERSION.SDK_INT < 24) return
        try {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()); lineTo(x.toFloat(), y.toFloat()) }
            dispatchPath(path, 0L, 50L)
        } catch (e: Exception) { Log.e(TAG, "tap failed: ${e.message}") }
    }

    @SuppressLint("NewApi")
    private fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) {
        if (Build.VERSION.SDK_INT < 24) return
        try {
            val path = Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }
            dispatchPath(path, 0L, durationMs)
        } catch (e: Exception) { Log.e(TAG, "swipe failed: ${e.message}") }
    }

    private fun dispatchPath(path: Path, startTime: Long, duration: Long) {
        val strokeClass = Class.forName("android.accessibilityservice.GestureDescription\$StrokeDescription")
        val stroke = strokeClass.getConstructor(
            Path::class.java, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType
        ).newInstance(path, startTime, duration)

        val builderClass = Class.forName("android.accessibilityservice.GestureDescription\$Builder")
        val builder = builderClass.getDeclaredConstructor().newInstance()
        builderClass.getMethod("addStroke", strokeClass).invoke(builder, stroke)
        val gestureDesc = builderClass.getMethod("build").invoke(builder)

        val gestureDescClass = Class.forName("android.accessibilityservice.GestureDescription")
        val callbackClass = Class.forName("android.accessibilityservice.GestureDescription\$GestureResultCallback")
        val dispatchMethod = AccessibilityService::class.java.getMethod(
            "dispatchGesture", gestureDescClass, callbackClass, Handler::class.java
        )
        dispatchMethod.invoke(this, gestureDesc, null, null)
    }

    // ── Screenshot capture (API 30+ via reflection) ──

    fun takeScreenshotBitmap(callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < 30) {
            Log.w(TAG, "takeScreenshot requires API 30+")
            callback(null); return
        }
        try {
            val callbackClass = Class.forName("android.accessibilityservice.AccessibilityService\$TakeScreenshotCallback")
            val proxy = Proxy.newProxyInstance(
                callbackClass.classLoader, arrayOf(callbackClass)
            ) { _, method, args ->
                when (method.name) {
                    "onSuccess" -> {
                        try {
                            val result = args[0]
                            val resultClass = result.javaClass
                            val hwBuffer = resultClass.getMethod("getHardwareBuffer").invoke(result)
                            val colorSpace = resultClass.getMethod("getColorSpace").invoke(result)
                            val wrapMethod = Bitmap::class.java.getMethod(
                                "wrapHardwareBuffer",
                                Class.forName("android.hardware.HardwareBuffer"),
                                Class.forName("android.graphics.ColorSpace")
                            )
                            val hwBitmap = wrapMethod.invoke(null, hwBuffer, colorSpace) as? Bitmap
                            // Copy to software bitmap so it can be used by JPEG compress
                            val swBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            callback(swBitmap)
                        } catch (e: Exception) {
                            Log.e(TAG, "screenshot onSuccess parse failed", e); callback(null)
                        }
                    }
                    "onFailure" -> { Log.w(TAG, "screenshot failed code=${args[0]}"); callback(null) }
                }
                null
            }
            val executor = Executors.newSingleThreadExecutor()
            val takeMethod = AccessibilityService::class.java.getMethod(
                "takeScreenshot",
                Int::class.javaPrimitiveType,
                java.util.concurrent.Executor::class.java,
                callbackClass
            )
            takeMethod.invoke(this, 0, executor, proxy)
        } catch (e: Exception) {
            Log.e(TAG, "takeScreenshot reflection failed", e); callback(null)
        }
    }

    private fun broadcastStatus(status: Int) {
        sendBroadcast(Intent(MacroController.ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(MacroController.EXTRA_STATUS, status)
        })
    }
}
