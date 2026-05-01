package com.kingshot.macro

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class KingshotAccessibilityService : AccessibilityService() {

    private val TAG = "KingshotMacro"
    private val handler = Handler(Looper.getMainLooper())
    private var tapCount = 0
    private var currentCol = 0
    private var screenWidth = 0
    private var screenHeight = 0

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MacroController.ACTION_START -> startMacro()
                MacroController.ACTION_PAUSE -> pauseMacro()
                MacroController.ACTION_STOP -> stopMacro()
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
        }
        registerReceiver(commandReceiver, filter)
        Log.d(TAG, "AccessibilityService connected. Screen: ${screenWidth}x${screenHeight}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMacro()
        try { unregisterReceiver(commandReceiver) } catch (e: Exception) { /* ignored */ }
        MacroController.instance = null
    }

    private fun loadScreenMetrics() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    private fun startMacro() {
        if (MacroController.currentStatus == MacroController.STATUS_RUNNING) return
        tapCount = 0
        currentCol = 0
        MacroController.currentStatus = MacroController.STATUS_RUNNING
        broadcastStatus(MacroController.STATUS_RUNNING)
        Log.d(TAG, "Macro started")
        scheduleNextTap()
    }

    private fun pauseMacro() {
        if (MacroController.currentStatus == MacroController.STATUS_PAUSED) {
            MacroController.currentStatus = MacroController.STATUS_RUNNING
            broadcastStatus(MacroController.STATUS_RUNNING)
            scheduleNextTap()
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
        tapCount = 0
        currentCol = 0
        Log.d(TAG, "Macro stopped")
    }

    private fun scheduleNextTap() {
        val delay = MacroController.getTapDelayMs(this)
        handler.postDelayed({ performNextTap() }, delay)
    }

    private fun performNextTap() {
        if (MacroController.currentStatus != MacroController.STATUS_RUNNING) return

        val tapsBeforeScroll = MacroController.getTapsBeforeScroll(this)

        // Check if it's time to scroll
        if (tapCount > 0 && tapCount % tapsBeforeScroll == 0) {
            performScroll()
            handler.postDelayed({ scheduleNextTap() }, 800)
            return
        }

        val col1X = (screenWidth * MacroController.getCol1XPct(this)).toInt()
        val col2X = (screenWidth * MacroController.getCol2XPct(this)).toInt()
        val rowStartY = (screenHeight * MacroController.getRowStartYPct(this)).toInt()
        val rowHeight = (screenHeight * MacroController.getRowHeightPct(this)).toInt()

        // Which row are we on within the current scroll window?
        val localTap = tapCount % tapsBeforeScroll
        val row = localTap / 2

        val x = if (currentCol == 0) col1X else col2X
        val y = rowStartY + row * rowHeight

        Log.d(TAG, "Tapping col=$currentCol row=$row at ($x, $y) tapCount=$tapCount")
        dispatchTapGesture(x.toFloat(), y.toFloat())

        currentCol = (currentCol + 1) % 2
        tapCount++
        scheduleNextTap()
    }

    @SuppressLint("NewApi")
    private fun dispatchTapGesture(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < 24) {
            Log.w(TAG, "dispatchGesture requires API 24+, skipping")
            return
        }
        try {
            val path = Path()
            path.moveTo(x, y)
            path.lineTo(x, y)

            val strokeClass = Class.forName("android.accessibilityservice.GestureDescription\$StrokeDescription")
            val stroke = strokeClass.getConstructor(
                Path::class.java,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType
            ).newInstance(path, 0L, 50L)

            val builderClass = Class.forName("android.accessibilityservice.GestureDescription\$Builder")
            val builder = builderClass.newInstance()
            builderClass.getMethod("addStroke", strokeClass).invoke(builder, stroke)
            val gestureDesc = builderClass.getMethod("build").invoke(builder)

            val gestureDescClass = Class.forName("android.accessibilityservice.GestureDescription")
            val callbackClass = Class.forName("android.accessibilityservice.GestureDescription\$GestureResultCallback")
            val dispatchMethod = AccessibilityService::class.java.getMethod(
                "dispatchGesture",
                gestureDescClass,
                callbackClass,
                Handler::class.java
            )
            dispatchMethod.invoke(this, gestureDesc, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch tap gesture: ${e.message}")
        }
    }

    @SuppressLint("NewApi")
    private fun performScroll() {
        if (Build.VERSION.SDK_INT < 24) return
        try {
            val startX = (screenWidth * 0.5f)
            val startY = (screenHeight * 0.7f)
            val endY = (screenHeight * 0.3f)

            val path = Path()
            path.moveTo(startX, startY)
            path.lineTo(startX, endY)

            val strokeClass = Class.forName("android.accessibilityservice.GestureDescription\$StrokeDescription")
            val stroke = strokeClass.getConstructor(
                Path::class.java,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType
            ).newInstance(path, 0L, 300L)

            val builderClass = Class.forName("android.accessibilityservice.GestureDescription\$Builder")
            val builder = builderClass.newInstance()
            builderClass.getMethod("addStroke", strokeClass).invoke(builder, stroke)
            val gestureDesc = builderClass.getMethod("build").invoke(builder)

            val gestureDescClass = Class.forName("android.accessibilityservice.GestureDescription")
            val callbackClass = Class.forName("android.accessibilityservice.GestureDescription\$GestureResultCallback")
            val dispatchMethod = AccessibilityService::class.java.getMethod(
                "dispatchGesture", gestureDescClass, callbackClass, Handler::class.java
            )
            dispatchMethod.invoke(this, gestureDesc, null, null)
            Log.d(TAG, "Scroll performed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch scroll gesture: ${e.message}")
        }
    }

    private fun broadcastStatus(status: Int) {
        val intent = Intent(MacroController.ACTION_STATUS).apply {
            putExtra(MacroController.EXTRA_STATUS, status)
        }
        sendBroadcast(intent)
    }
}
