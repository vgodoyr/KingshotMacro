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
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
        registerReceiver(commandReceiver, filter)
        Log.d(TAG, "AccessibilityService connected. Screen: ${screenWidth}x${screenHeight}")
        try {
            android.widget.Toast.makeText(
                this,
                "Accesibilidad OK ${screenWidth}x${screenHeight}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } catch (_: Throwable) {}
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

    // ── Overlay management ───────────────────────────────────────────────
    // Permite a otros servicios añadir overlays de TYPE_ACCESSIBILITY_OVERLAY,
    // un tipo de ventana que SOLO puede ser creado por servicios de
    // accesibilidad y que NO marca los touch events del SO como "obscured".
    // Esto evita el anti-tapjacking de juegos que rechazan synthetic touches
    // pasando por encima de overlays normales (TYPE_APPLICATION_OVERLAY).

    fun attachOverlay(view: View, params: WindowManager.LayoutParams): Boolean {
        return try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.addView(view, params)
            Log.d(TAG, "Overlay attached as TYPE_ACCESSIBILITY_OVERLAY")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "attachOverlay failed: ${e.message}", e)
            false
        }
    }

    fun detachOverlay(view: View) {
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(view)
        } catch (e: Throwable) {
            Log.e(TAG, "detachOverlay failed: ${e.message}", e)
        }
    }

    fun updateOverlayLayout(view: View, params: WindowManager.LayoutParams) {
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.updateViewLayout(view, params)
        } catch (e: Throwable) {
            Log.e(TAG, "updateOverlayLayout failed: ${e.message}", e)
        }
    }

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
        Log.d(TAG, "Macro $macroId started, screen=${screenWidth}x${screenHeight}")
        try {
            android.widget.Toast.makeText(
                this,
                "Macro '$macroId' iniciado",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } catch (_: Throwable) {}
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
    // Flujo real del juego: tap en jugador → popup (Ver/Chat/Invitar) → tap "Invitar".
    // Usamos el árbol de accesibilidad para encontrar y tocar el botón "Invitar"
    // por texto, así no dependemos de coordenadas fijas — el popup aparece en
    // posiciones distintas según el jugador.

    private fun executeClanInvitationStep() {
        val tapsBeforeScroll = MacroController.getTapsBeforeScroll(this)

        // Paso 1: si hay popup con "Invitar" en pantalla, lo tocamos primero.
        if (clickInvitarIfVisible()) {
            scheduleNextStep(MacroController.getTapDelayMs(this))
            return
        }

        // Paso 2: scroll cada N invitaciones para cargar más jugadores.
        if (clanTapCount > 0 && clanTapCount % tapsBeforeScroll == 0) {
            val sx = (screenWidth * 0.5f).toInt()
            val sy1 = (screenHeight * 0.7f).toInt()
            val sy2 = (screenHeight * 0.3f).toInt()
            swipe(sx, sy1, sx, sy2, 300)
            clanTapCount++
            clanCurrentCol = 0
            scheduleNextStep(1200)
            return
        }

        // Paso 3: tap en la siguiente tarjeta de jugador.
        val col1 = (screenWidth * MacroController.getCol1XPct(this)).toInt()
        val col2 = (screenWidth * MacroController.getCol2XPct(this)).toInt()
        val rowStart = (screenHeight * MacroController.getRowStartYPct(this)).toInt()
        val rowH = (screenHeight * MacroController.getRowHeightPct(this)).toInt()

        val localTap = clanTapCount % tapsBeforeScroll
        val row = localTap / 2
        val x = if (clanCurrentCol == 0) col1 else col2
        val y = rowStart + row * rowH

        tap(x, y, "player#$clanTapCount c=$clanCurrentCol r=$row")
        clanCurrentCol = (clanCurrentCol + 1) % 2
        clanTapCount++

        // Damos un poco más de tiempo para que aparezca el popup antes del próximo paso.
        scheduleNextStep(MacroController.getTapDelayMs(this) + 300)
    }

    /**
     * Busca un nodo en el árbol de accesibilidad cuyo texto contenga "Invitar"
     * (mayúsculas/minúsculas y acentos ignorados). Si lo encuentra, lo toca y
     * devuelve true. Útil para confirmar la invitación tras tap en jugador.
     */
    private fun clickInvitarIfVisible(): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            val target = findClickableByText(root, "invitar")
                ?: findClickableByText(root, "invite")
                ?: return false
            tapNodeCenter(target)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "clickInvitarIfVisible error: ${e.message}")
            false
        }
    }

    private fun findClickableByText(root: AccessibilityNodeInfo, needle: String): AccessibilityNodeInfo? {
        // Recorremos el árbol buscando el primer nodo cuyo text/contentDescription
        // empiece con "needle" (case/acento insensible) y sea clickable o tenga
        // ancestro clickable.
        val n = needle.lowercase()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val text = (node.text?.toString() ?: "").lowercase()
            val desc = (node.contentDescription?.toString() ?: "").lowercase()
            if (text.startsWith(n) || desc.startsWith(n) || text.contains(n) || desc.contains(n)) {
                // Buscar ancestro clickable si este no lo es
                var clickable: AccessibilityNodeInfo? = node
                while (clickable != null && !clickable.isClickable) {
                    clickable = clickable.parent
                }
                if (clickable != null) return clickable
                return node // mejor algo que nada
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return null
    }

    private fun tapNodeCenter(node: AccessibilityNodeInfo) {
        try {
            // Primero intentar performAction(CLICK) — más fiable que coordenadas.
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "Clicked '${node.text}' via ACTION_CLICK")
                broadcastTap(-1, -1, "ACTION_CLICK: ${node.text}")
                return
            }
            // Fallback: gesto en el centro del bounding box.
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                tap(rect.centerX(), rect.centerY(), "node:${node.text}")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "tapNodeCenter error: ${e.message}")
        }
    }

    // ── Gesture dispatch (reflection-based for API 23 compile target) ──

    private var diagnosticTapCount = 0

    @SuppressLint("NewApi")
    private fun tap(x: Int, y: Int, label: String = "tap") {
        if (Build.VERSION.SDK_INT < 24) {
            broadcastTap(x, y, "API<24 unsupported")
            return
        }
        try {
            // Just moveTo. Algunas versiones de ART no aceptan strokes
            // degenerados (moveTo+lineTo al mismo punto). Solo moveTo + duration
            // se trata como un tap puntual y es lo que dispatchGesture espera.
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            // 120ms — dura más que un click humano mínimo, aumenta probabilidad
            // de que juegos con detección estricta lo registren.
            val ok = dispatchPath(path, 0L, 120L)
            broadcastTap(x, y, if (ok) label else "DISPATCH RETURNED FALSE")
            Log.d(TAG, "tap fired at ($x,$y) label=$label dispatchOk=$ok")
        } catch (e: Throwable) {
            Log.e(TAG, "tap failed at ($x,$y): ${e.message}", e)
            broadcastTap(x, y, "FAIL: ${e.javaClass.simpleName}")
        }
    }

    @SuppressLint("NewApi")
    private fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) {
        if (Build.VERSION.SDK_INT < 24) {
            broadcastTap(x1, y1, "swipe API<24")
            return
        }
        try {
            val path = Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }
            val ok = dispatchPath(path, 0L, durationMs)
            broadcastTap(x1, y1, "swipe→${x2},${y2} ok=$ok")
            Log.d(TAG, "swipe fired ($x1,$y1)→($x2,$y2) ok=$ok")
        } catch (e: Throwable) {
            Log.e(TAG, "swipe failed: ${e.message}", e)
            broadcastTap(x1, y1, "swipe FAIL")
        }
    }

    private fun broadcastTap(x: Int, y: Int, label: String) {
        diagnosticTapCount++
        try {
            sendBroadcast(Intent(MacroController.ACTION_TAP_FIRED).apply {
                setPackage(packageName)
                putExtra(MacroController.EXTRA_TAP_X, x)
                putExtra(MacroController.EXTRA_TAP_Y, y)
                putExtra(MacroController.EXTRA_TAP_LABEL, label)
                putExtra(MacroController.EXTRA_TAP_COUNT, diagnosticTapCount)
            })
        } catch (_: Throwable) {}
    }

    private fun dispatchPath(path: Path, startTime: Long, duration: Long): Boolean {
        val strokeClass = Class.forName("android.accessibilityservice.GestureDescription\$StrokeDescription")
        val stroke = strokeClass.getConstructor(
            Path::class.java, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType
        ).newInstance(path, startTime, duration)

        val builderClass = Class.forName("android.accessibilityservice.GestureDescription\$Builder")
        val builder = builderClass.getDeclaredConstructor().newInstance()
        builderClass.getMethod("addStroke", strokeClass).invoke(builder, stroke)
        val gestureDesc = builderClass.getMethod("build").invoke(builder)

        val gestureDescClass = Class.forName("android.accessibilityservice.GestureDescription")
        val callbackClass = Class.forName("android.accessibilityservice.AccessibilityService\$GestureResultCallback")
        val dispatchMethod = AccessibilityService::class.java.getMethod(
            "dispatchGesture", gestureDescClass, callbackClass, Handler::class.java
        )
        val ok = dispatchMethod.invoke(this, gestureDesc, null, null) as? Boolean ?: false
        if (!ok) Log.w(TAG, "dispatchGesture returned false")
        return ok
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
