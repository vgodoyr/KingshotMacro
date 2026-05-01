package com.kingshot.macro

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class FloatingOverlayService : Service() {

    private val TAG = "FloatingOverlay"
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var statusText: TextView
    private lateinit var btnStartPause: Button
    private lateinit var btnStop: Button
    private lateinit var btnSettings: Button

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getIntExtra(MacroController.EXTRA_STATUS, MacroController.STATUS_IDLE)
                ?: MacroController.STATUS_IDLE
            updateUI(status)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createFloatingView()
        registerReceiver(statusReceiver, IntentFilter(MacroController.ACTION_STATUS))
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(statusReceiver) } catch (e: Exception) { /* ignored */ }
        if (::floatingView.isInitialized) {
            try { windowManager.removeView(floatingView) } catch (e: Exception) { /* ignored */ }
        }
    }

    private fun createFloatingView() {
        // TYPE_APPLICATION_OVERLAY = 2038 (API 26+); TYPE_PHONE = 2002 (legacy)
        @Suppress("DEPRECATION")
        val overlayType = if (Build.VERSION.SDK_INT >= 26) 2038
        else WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 100
        }

        floatingView = buildOverlayView()

        floatingView.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                event ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatingView, params)
        Log.d(TAG, "Floating overlay created")
    }

    private fun buildOverlayView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(220, 30, 30, 50))
            setPadding(16, 12, 16, 12)
        }

        val title = TextView(this).apply {
            text = "Kingshot Macro"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        statusText = TextView(this).apply {
            text = "IDLE"
            setTextColor(Color.LTGRAY)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 8)
        }
        root.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        btnStartPause = Button(this).apply {
            text = "Start"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(200, 0, 150, 60))
            setOnClickListener { onStartPauseClicked() }
        }
        btnRow.addView(btnStartPause, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(0, 0, 4, 0)
        })

        btnStop = Button(this).apply {
            text = "Stop"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(200, 180, 30, 30))
            setOnClickListener { onStopClicked() }
        }
        btnRow.addView(btnStop, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        root.addView(btnRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        btnSettings = Button(this).apply {
            text = "Settings"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(200, 50, 80, 150))
            setOnClickListener { openSettings() }
        }
        root.addView(btnSettings, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 6, 0, 0)
        })

        return root
    }

    private fun onStartPauseClicked() {
        val intent = when (MacroController.currentStatus) {
            MacroController.STATUS_RUNNING -> Intent(MacroController.ACTION_PAUSE)
            MacroController.STATUS_PAUSED -> Intent(MacroController.ACTION_PAUSE)
            else -> Intent(MacroController.ACTION_START)
        }
        sendBroadcast(intent)
    }

    private fun onStopClicked() {
        sendBroadcast(Intent(MacroController.ACTION_STOP))
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun updateUI(status: Int) {
        when (status) {
            MacroController.STATUS_RUNNING -> {
                statusText.text = "RUNNING"
                statusText.setTextColor(Color.GREEN)
                btnStartPause.text = "Pause"
                btnStartPause.setBackgroundColor(Color.argb(200, 200, 140, 0))
            }
            MacroController.STATUS_PAUSED -> {
                statusText.text = "PAUSED"
                statusText.setTextColor(Color.YELLOW)
                btnStartPause.text = "Resume"
                btnStartPause.setBackgroundColor(Color.argb(200, 0, 150, 60))
            }
            else -> {
                statusText.text = "IDLE"
                statusText.setTextColor(Color.LTGRAY)
                btnStartPause.text = "Start"
                btnStartPause.setBackgroundColor(Color.argb(200, 0, 150, 60))
            }
        }
    }
}
