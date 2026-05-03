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
import android.widget.ScrollView
import android.widget.TextView

class FloatingOverlayService : Service() {

    private val TAG = "FloatingOverlay"
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams

    // Collapsed state UI
    private lateinit var collapsedView: LinearLayout
    private lateinit var statusDot: View

    // Expanded state UI
    private lateinit var expandedView: LinearLayout
    private lateinit var macroNameText: TextView
    private lateinit var geminiLogText: TextView
    private lateinit var statusText: TextView
    private lateinit var btnStartPause: Button
    private lateinit var btnMacro: Button

    private var isExpanded = false
    private var macroPopupView: View? = null

    // Drag state
    private var dragInitialX = 0
    private var dragInitialY = 0
    private var dragTouchX = 0f
    private var dragTouchY = 0f
    private var dragMoved = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MacroController.ACTION_STATUS -> {
                    val status = intent.getIntExtra(MacroController.EXTRA_STATUS, MacroController.STATUS_IDLE)
                    updateUI(status)
                }
                MacroController.ACTION_GEMINI_LOG -> {
                    val text = intent.getStringExtra(MacroController.EXTRA_LOG_TEXT) ?: return
                    if (::geminiLogText.isInitialized) {
                        geminiLogText.text = text
                        geminiLogText.visibility = View.VISIBLE
                    }
                }
                MacroController.ACTION_TAP_FIRED -> {
                    val n = intent.getIntExtra(MacroController.EXTRA_TAP_COUNT, 0)
                    val x = intent.getIntExtra(MacroController.EXTRA_TAP_X, 0)
                    val y = intent.getIntExtra(MacroController.EXTRA_TAP_Y, 0)
                    val label = intent.getStringExtra(MacroController.EXTRA_TAP_LABEL) ?: ""
                    if (::statusText.isInitialized) {
                        statusText.text = "Taps: $n · ($x,$y)\n$label"
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createFloatingView()

        val filter = IntentFilter().apply {
            addAction(MacroController.ACTION_STATUS)
            addAction(MacroController.ACTION_GEMINI_LOG)
            addAction(MacroController.ACTION_TAP_FIRED)
        }
        registerReceiver(statusReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(statusReceiver) } catch (e: Exception) { /* ignored */ }
        dismissMacroPopup()
        if (::floatingView.isInitialized) {
            try { windowManager.removeView(floatingView) } catch (e: Exception) { /* ignored */ }
        }
    }

    /**
     * Cuando el usuario cierra la app desde recientes, paramos el macro,
     * cerramos el panel flotante y nos auto-destruimos. Así "cerrar la app
     * cierra todo".
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        try {
            sendBroadcast(Intent(MacroController.ACTION_STOP).apply { setPackage(packageName) })
            sendBroadcast(Intent(MacroController.ACTION_GEMINI_STOP).apply { setPackage(packageName) })
        } catch (_: Throwable) {}
        stopSelf()
    }

    // ── View construction ─────────────────────────────────────────────────

    private fun createFloatingView() {
        @Suppress("DEPRECATION")
        val overlayType = if (Build.VERSION.SDK_INT >= 26) 2038
        else WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            // NOT_FOCUSABLE: el panel no roba focus al juego.
            // NOT_TOUCH_MODAL: touches FUERA del panel pasan a la app de abajo
            //                  (sin esto el panel puede absorber eventos de toda la pantalla,
            //                  bloqueando el dispatchGesture del macro).
            // LAYOUT_IN_SCREEN: el panel se ubica respecto a la pantalla completa.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 100
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(160, 15, 15, 30))
            setPadding(10, 8, 10, 8)
        }

        collapsedView = buildCollapsedView()
        expandedView = buildExpandedView()
        expandedView.visibility = View.GONE

        root.addView(collapsedView)
        root.addView(expandedView)

        root.setOnTouchListener(dragListener)
        floatingView = root
        windowManager.addView(floatingView, params)
        Log.d(TAG, "Floating overlay created")
    }

    private fun buildCollapsedView(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setGravity(Gravity.CENTER_VERTICAL)
        }

        statusDot = View(this).apply {
            setBackgroundColor(Color.GRAY)
        }
        val dotSize = dp(14)
        val dotParams = LinearLayout.LayoutParams(dotSize, dotSize).apply { setMargins(0, 0, dp(6), 0) }
        row.addView(statusDot, dotParams)

        val expandBtn = TextView(this).apply {
            text = "⊞"
            textSize = 18f
            setTextColor(Color.WHITE)
            setOnClickListener { if (!dragMoved) expandPanel() }
        }
        row.addView(expandBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return row
    }

    private fun buildExpandedView(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumWidth = dp(180)
        }

        // Header: macro name + collapse button
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setGravity(Gravity.CENTER_VERTICAL)
            setPadding(0, 0, 0, dp(4))
        }

        macroNameText = TextView(this).apply {
            text = truncateMacroName()
            setTextColor(Color.parseColor("#58A6FF"))
            textSize = 12f
        }
        headerRow.addView(macroNameText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val collapseBtn = TextView(this).apply {
            text = "⊟"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            setPadding(dp(8), 0, 0, 0)
            setOnClickListener { if (!dragMoved) collapsePanel() }
        }
        headerRow.addView(collapseBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        panel.addView(headerRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // Gemini last action log (1 line, hidden until used)
        geminiLogText = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#8B949E"))
            textSize = 10f
            visibility = View.GONE
            maxLines = 1
            setPadding(0, 0, 0, dp(4))
        }
        panel.addView(geminiLogText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // Status text
        statusText = TextView(this).apply {
            text = "IDLE"
            setTextColor(Color.LTGRAY)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        }
        panel.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // Row 1: Start/Pause + Stop
        val btnRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        btnStartPause = Button(this).apply {
            text = "▶ Start"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(200, 0, 150, 60))
            setOnClickListener { if (!dragMoved) onStartPauseClicked() }
        }
        btnRow1.addView(btnStartPause, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(0, 0, dp(4), 0)
        })

        val btnStop = Button(this).apply {
            text = "⏹ Stop"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(200, 180, 30, 30))
            setOnClickListener { if (!dragMoved) sendBroadcast(Intent(MacroController.ACTION_STOP).apply { setPackage(packageName) }) }
        }
        btnRow1.addView(btnStop, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(btnRow1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp(4)) })

        // Row 2: Macro selector + Settings
        val btnRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        btnMacro = Button(this).apply {
            text = "Macro ▾"
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(180, 50, 70, 120))
            setOnClickListener { if (!dragMoved) showMacroPopup() }
        }
        btnRow2.addView(btnMacro, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(0, 0, dp(4), 0)
        })

        val btnSettings = Button(this).apply {
            text = "⚙"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(180, 50, 70, 120))
            setOnClickListener {
                if (!dragMoved) {
                    startActivity(Intent(this@FloatingOverlayService, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            }
        }
        btnRow2.addView(btnSettings, LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT))
        panel.addView(btnRow2, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return panel
    }

    // ── Drag listener ─────────────────────────────────────────────────────

    private val dragListener = View.OnTouchListener { _, event ->
        event ?: return@OnTouchListener false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragInitialX = params.x
                dragInitialY = params.y
                dragTouchX = event.rawX
                dragTouchY = event.rawY
                dragMoved = false
                false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - dragTouchX).toInt()
                val dy = (event.rawY - dragTouchY).toInt()
                if (!dragMoved && (Math.abs(dx) > 8 || Math.abs(dy) > 8)) dragMoved = true
                if (dragMoved) {
                    params.x = dragInitialX + dx
                    params.y = dragInitialY + dy
                    windowManager.updateViewLayout(floatingView, params)
                }
                dragMoved
            }
            else -> false
        }
    }

    // ── Collapse / Expand ─────────────────────────────────────────────────

    private fun expandPanel() {
        isExpanded = true
        collapsedView.visibility = View.GONE
        expandedView.visibility = View.VISIBLE
        updateMacroName()
    }

    private fun collapsePanel() {
        isExpanded = false
        expandedView.visibility = View.GONE
        collapsedView.visibility = View.VISIBLE
        dismissMacroPopup()
    }

    // ── Macro popup ───────────────────────────────────────────────────────

    private fun showMacroPopup() {
        dismissMacroPopup()
        val macros = MacroLibrary.getAllMacros(this)

        @Suppress("DEPRECATION")
        val overlayType = if (Build.VERSION.SDK_INT >= 26) 2038 else WindowManager.LayoutParams.TYPE_PHONE

        val popupParams = WindowManager.LayoutParams(
            dp(220),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = params.x
            y = params.y + dp(if (isExpanded) 180 else 50)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(245, 15, 15, 30))
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        val header = TextView(this).apply {
            text = "Seleccionar Macro"
            setTextColor(Color.parseColor("#58A6FF"))
            textSize = 12f
            setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        container.addView(header)

        macros.forEach { macro ->
            val active = macro.id == MacroController.activeMacroId
            val item = TextView(this).apply {
                text = if (active) "✓ ${macro.name}" else "  ${macro.name}"
                setTextColor(if (active) Color.parseColor("#58A6FF") else Color.WHITE)
                textSize = 12f
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setOnClickListener {
                    MacroController.setActiveMacroId(this@FloatingOverlayService, macro.id)
                    updateMacroName()
                    dismissMacroPopup()
                }
            }
            container.addView(item)
        }

        val closeItem = TextView(this).apply {
            text = "✕ Cancelar"
            setTextColor(Color.LTGRAY)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(4))
            setOnClickListener { dismissMacroPopup() }
        }
        container.addView(closeItem)

        macroPopupView = container
        windowManager.addView(container, popupParams)
    }

    private fun dismissMacroPopup() {
        macroPopupView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { /* ignored */ }
            macroPopupView = null
        }
    }

    // ── UI updates ────────────────────────────────────────────────────────

    private fun onStartPauseClicked() {
        val intent = when (MacroController.currentStatus) {
            MacroController.STATUS_RUNNING -> Intent(MacroController.ACTION_PAUSE)
            MacroController.STATUS_PAUSED  -> Intent(MacroController.ACTION_PAUSE)
            else                           -> Intent(MacroController.ACTION_START)
        }
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun updateUI(status: Int) {
        val dotColor: Int
        when (status) {
            MacroController.STATUS_RUNNING -> {
                dotColor = Color.parseColor("#3FB950")
                if (::statusText.isInitialized) {
                    statusText.text = "RUNNING"
                    statusText.setTextColor(Color.parseColor("#3FB950"))
                }
                if (::btnStartPause.isInitialized) {
                    btnStartPause.text = "⏸ Pause"
                    btnStartPause.setBackgroundColor(Color.argb(200, 200, 140, 0))
                }
                // Auto-contraer el panel cuando el macro corre, para minimizar
                // el área que el panel cubre. Algunos juegos tienen anti-tapjacking
                // que rechaza synthetic touches si hay overlay encima.
                if (isExpanded) collapsePanel()
            }
            MacroController.STATUS_PAUSED -> {
                dotColor = Color.YELLOW
                if (::statusText.isInitialized) {
                    statusText.text = "PAUSED"
                    statusText.setTextColor(Color.YELLOW)
                }
                if (::btnStartPause.isInitialized) {
                    btnStartPause.text = "▶ Reanudar"
                    btnStartPause.setBackgroundColor(Color.argb(200, 0, 150, 60))
                }
            }
            else -> {
                dotColor = Color.GRAY
                if (::statusText.isInitialized) {
                    statusText.text = "IDLE"
                    statusText.setTextColor(Color.LTGRAY)
                }
                if (::btnStartPause.isInitialized) {
                    btnStartPause.text = "▶ Start"
                    btnStartPause.setBackgroundColor(Color.argb(200, 0, 150, 60))
                }
            }
        }
        if (::statusDot.isInitialized) statusDot.setBackgroundColor(dotColor)
    }

    private fun updateMacroName() {
        if (::macroNameText.isInitialized) {
            macroNameText.text = truncateMacroName()
        }
    }

    private fun truncateMacroName(): String {
        val macro = MacroLibrary.getById(this, MacroController.activeMacroId)
        val name = macro?.name ?: MacroController.activeMacroId
        return if (name.length > 16) name.take(14) + "…" else name
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
