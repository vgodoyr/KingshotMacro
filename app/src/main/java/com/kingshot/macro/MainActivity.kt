package com.kingshot.macro

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {

    private val REQUEST_OVERLAY = 1001
    private var currentTab = 0
    private lateinit var tabContents: Array<View>
    private lateinit var tabButtons: Array<Button>

    // Pestaña 3 – Auto IA
    private lateinit var geminiLogText: TextView
    private lateinit var btnGeminiToggle: Button
    private lateinit var tvGeminiStatus: TextView

    // Inicializado en onCreate (no en field init) para que cualquier fallo
    // sea capturado por el try-catch de onCreate y no mate el constructor.
    private var geminiLogReceiver: BroadcastReceiver? = null

    private fun makeGeminiLogReceiver() = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            try {
                val line = intent?.getStringExtra(MacroController.EXTRA_LOG_TEXT) ?: return
                if (!::geminiLogText.isInitialized) return
                val current = geminiLogText.text.toString()
                val lines = current.split("\n").takeLast(19)
                geminiLogText.text = (lines + line).joinToString("\n")
            } catch (_: Throwable) { /* nunca dejes que un broadcast mate el activity */ }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Outer try-catch: si TODO falla, mostramos error en pantalla en vez de
        // dejar que el sistema cierre la app silenciosamente.
        try {
            super.onCreate(savedInstanceState)
            geminiLogReceiver = makeGeminiLogReceiver()
            setContentView(buildRootLayout())
            try {
                MacroController.setActiveMacroId(this, MacroController.getActiveMacroId(this))
            } catch (_: Throwable) { /* prefs no críticas en arranque */ }
        } catch (e: Throwable) {
            showCrashScreen(savedInstanceState, e)
        }
    }

    private fun showCrashScreen(savedInstanceState: Bundle?, e: Throwable) {
        try { super.onCreate(savedInstanceState) } catch (_: Throwable) {}
        val msg = try {
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            "CRASH en MainActivity:\n\n${e.javaClass.name}: ${e.message}\n\n$sw"
        } catch (_: Throwable) { "CRASH (sin detalles): ${e.javaClass.name}" }
        android.util.Log.e("KingshotMacro", msg, e)
        try {
            val tv = android.widget.TextView(this).apply {
                text = msg
                setTextColor(android.graphics.Color.RED)
                textSize = 11f
                setPadding(16, 16, 16, 16)
                setBackgroundColor(android.graphics.Color.BLACK)
            }
            val sv = android.widget.ScrollView(this).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                addView(tv)
            }
            setContentView(sv)
        } catch (_: Throwable) {
            try { Toast.makeText(applicationContext, "CRASH: ${e.message}", Toast.LENGTH_LONG).show() } catch (_: Throwable) {}
        }
    }

    override fun onResume() {
        super.onResume()
        try { refreshTabContent() } catch (_: Throwable) {}
        try {
            geminiLogReceiver?.let { registerReceiver(it, IntentFilter(MacroController.ACTION_GEMINI_LOG)) }
        } catch (_: Throwable) { /* registerReceiver puede fallar en API 34+ sin flag */ }
    }

    override fun onPause() {
        super.onPause()
        try { geminiLogReceiver?.let { unregisterReceiver(it) } } catch (_: Throwable) { /* ignored */ }
    }

    /**
     * Cuando el usuario cierra la app (back button o desde recientes), paramos
     * todos los servicios y broadcasts. Así "cerrar la app" significa
     * realmente cerrar todo, no dejar el panel flotante ni Auto-IA corriendo.
     */
    override fun onDestroy() {
        if (isFinishing) {
            try {
                sendBroadcast(Intent(MacroController.ACTION_STOP).apply { setPackage(packageName) })
                sendBroadcast(Intent(MacroController.ACTION_GEMINI_STOP).apply { setPackage(packageName) })
                stopService(Intent(this, FloatingOverlayService::class.java))
                stopService(Intent(this, GeminiAutoPlayService::class.java))
            } catch (_: Throwable) {}
        }
        super.onDestroy()
    }

    // ── Root layout: header + content area + bottom nav ──────────────────

    private fun buildRootLayout(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiTheme.BG_DEEP)
        }

        // Header con gradiente y subtítulo
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiTheme.gradient(
                this@MainActivity,
                0xFF1A1F36.toInt(),
                0xFF0F1422.toInt(),
                0,
                vertical = true
            )
            setPadding(dpToPx(20), dpToPx(18), dpToPx(20), dpToPx(16))
        }
        val headerTitle = TextView(this).apply {
            text = "⚔  Kingshot Macro"
            textSize = 22f
            setTextColor(UiTheme.TEXT_PRIMARY)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.02f
        }
        val headerSub = TextView(this).apply {
            text = "Automatización inteligente para Kingshot"
            textSize = 12f
            setTextColor(UiTheme.TEXT_SECONDARY)
            setPadding(0, dpToPx(2), 0, 0)
        }
        header.addView(headerTitle)
        header.addView(headerSub)
        root.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Línea de acento bajo el header
        val accentLine = View(this).apply {
            background = UiTheme.gradient(
                this@MainActivity,
                UiTheme.PRIMARY,
                UiTheme.ACCENT,
                0
            )
        }
        root.addView(accentLine, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(2)))

        // Content area
        val contentFrame = FrameLayout(this).apply {
            setBackgroundColor(UiTheme.BG_DEEP)
        }
        tabContents = arrayOf(buildTabHome(), buildTabMacros(), buildTabAutoIA(), buildTabSettings())
        tabContents.forEach { v ->
            contentFrame.addView(v, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            v.visibility = View.GONE
        }
        tabContents[0].visibility = View.VISIBLE
        root.addView(contentFrame, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // Bottom nav bar premium con indicador
        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = UiTheme.gradient(
                this@MainActivity,
                0xFF131826.toInt(),
                0xFF0A0E1A.toInt(),
                0,
                vertical = true
            )
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(8))
        }
        val tabLabels = arrayOf("Inicio", "Macros", "Auto IA", "Ajustes")
        val tabIcons  = arrayOf("⌂", "▶", "✦", "⚙")
        tabButtons = Array(4) { i ->
            Button(this).apply {
                text = "${tabIcons[i]}\n${tabLabels[i]}"
                textSize = 11f
                setTextColor(if (i == 0) UiTheme.ACCENT else UiTheme.TEXT_MUTED)
                background = if (i == 0)
                    UiTheme.roundedDp(this@MainActivity, 0x1A06B6D4, 12)
                else
                    UiTheme.roundedDp(this@MainActivity, Color.TRANSPARENT, 12)
                setPadding(0, dpToPx(8), 0, dpToPx(8))
                stateListAnimator = null
                setOnClickListener { switchTab(i) }
            }
        }
        tabButtons.forEach { btn ->
            navBar.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dpToPx(2), 0, dpToPx(2), 0)
            })
        }
        root.addView(navBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        return root
    }

    private fun switchTab(index: Int) {
        currentTab = index
        tabContents.forEachIndexed { i, v -> v.visibility = if (i == index) View.VISIBLE else View.GONE }
        tabButtons.forEachIndexed { i, b ->
            val active = i == index
            b.setTextColor(if (active) UiTheme.ACCENT else UiTheme.TEXT_MUTED)
            b.background = if (active) UiTheme.roundedDp(this, 0x1A06B6D4, 12)
                           else UiTheme.roundedDp(this, Color.TRANSPARENT, 12)
        }
        refreshTabContent()
    }

    private fun refreshTabContent() {
        when (currentTab) {
            0 -> refreshHomeTab()
            1 -> refreshMacrosTab()
            2 -> refreshAutoIATab()
        }
    }

    // ── Tab 1: Inicio ─────────────────────────────────────────────────────

    private lateinit var tvPermOverlay: TextView
    private lateinit var tvPermAccess: TextView
    private lateinit var tvCurrentMacro: TextView
    private lateinit var btnLaunchOverlay: Button
    private lateinit var btnGrantOverlay: Button
    private lateinit var btnGrantAccess: Button

    private fun buildTabHome(): ScrollView {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24)
        }

        // Permission cards
        root.addView(sectionLabel("Permisos requeridos"))

        val cardOverlay = permCard("Superposición de pantalla", "Para mostrar el panel flotante")
        tvPermOverlay = cardOverlay.findViewWithTag("status") as TextView
        btnGrantOverlay = cardOverlay.findViewWithTag("btn") as Button
        btnGrantOverlay.setOnClickListener { requestOverlayPermission() }
        root.addView(cardOverlay, cardMargin())

        val cardAccess = permCard("Servicio de accesibilidad", "Para ejecutar toques automáticos")
        tvPermAccess = cardAccess.findViewWithTag("status") as TextView
        btnGrantAccess = cardAccess.findViewWithTag("btn") as Button
        btnGrantAccess.setOnClickListener { openAccessibilitySettings() }
        root.addView(cardAccess, cardMargin())

        root.addView(divider())
        root.addView(sectionLabel("Control del macro"))

        tvCurrentMacro = TextView(this).apply {
            text = "Macro activo: cargando…"; setTextColor(Color.LTGRAY); textSize = 13f
            setPadding(0, 0, 0, 12)
        }
        root.addView(tvCurrentMacro)

        btnLaunchOverlay = Button(this).apply {
            text = "▶  Iniciar panel flotante"
            setTextColor(UiTheme.TEXT_PRIMARY)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = UiTheme.gradient(this@MainActivity, UiTheme.SUCCESS, UiTheme.SUCCESS_DARK, 999)
            stateListAnimator = null
            setAllCaps(false)
            textSize = 15f
            setPadding(0, dpToPx(16), 0, dpToPx(16))
            setOnClickListener { launchOverlay() }
        }
        root.addView(btnLaunchOverlay, fullWidthBtn())

        root.addView(divider())
        root.addView(sectionLabel("Instrucciones rápidas"))
        root.addView(infoText(
            "1. Concede ambos permisos de arriba.\n" +
            "2. Pulsa 'Iniciar panel flotante'.\n" +
            "3. Abre Kingshot, ve a la pantalla que quieras automatizar.\n" +
            "4. Usa el panel flotante para Iniciar / Pausar / Parar.\n" +
            "5. Para Auto IA ve a la pestaña 🤖 Auto IA.\n" +
            "6. Para crear macros propios ve a 🎮 Macros."
        ))

        scroll.addView(root); return scroll
    }

    private fun permCard(title: String, subtitle: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = UiTheme.roundedStroke(this@MainActivity,
                UiTheme.BG_SURFACE, 0xFF252D40.toInt(), 14, 1)
            setPadding(dpToPx(16), dpToPx(14), dpToPx(12), dpToPx(14))
            setGravity(Gravity.CENTER_VERTICAL)
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = title; setTextColor(UiTheme.TEXT_PRIMARY); textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        texts.addView(TextView(this).apply {
            text = subtitle; setTextColor(UiTheme.TEXT_SECONDARY); textSize = 11f
            setPadding(0, dpToPx(2), 0, 0)
        })
        val tvStatus = TextView(this).apply {
            text = "…"; textSize = 14f; tag = "status"; gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(dpToPx(50), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val btn = Button(this).apply {
            text = "Conceder"; textSize = 11f; tag = "btn"
            setTextColor(UiTheme.TEXT_PRIMARY)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = UiTheme.gradient(this@MainActivity, UiTheme.PRIMARY, UiTheme.PRIMARY_DARK, 999)
            stateListAnimator = null
            setAllCaps(false)
            layoutParams = LinearLayout.LayoutParams(dpToPx(94), dpToPx(36))
        }
        card.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(tvStatus); card.addView(btn)
        return card
    }

    private fun refreshHomeTab() {
        val overlayOk = hasOverlayPermission()
        val accessOk = isAccessibilityEnabled()
        tvPermOverlay.text = if (overlayOk) "✓" else "✗"
        tvPermOverlay.setTextColor(if (overlayOk) Color.parseColor("#3FB950") else Color.parseColor("#F85149"))
        btnGrantOverlay.isEnabled = !overlayOk

        tvPermAccess.text = if (accessOk) "✓" else "✗"
        tvPermAccess.setTextColor(if (accessOk) Color.parseColor("#3FB950") else Color.parseColor("#F85149"))
        btnGrantAccess.isEnabled = !accessOk

        btnLaunchOverlay.isEnabled = overlayOk && accessOk
        btnLaunchOverlay.setBackgroundColor(
            if (overlayOk && accessOk) Color.parseColor("#238636") else Color.DKGRAY)

        val macroName = MacroLibrary.getById(this, MacroController.getActiveMacroId(this))?.name
            ?: "Invitar al Clan"
        tvCurrentMacro.text = "Macro activo: $macroName"
    }

    // ── Tab 2: Macros ─────────────────────────────────────────────────────

    private lateinit var macroListContainer: LinearLayout

    private fun buildTabMacros(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D1117"))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(20, 16, 16, 12); setGravity(Gravity.CENTER_VERTICAL)
        }
        header.addView(sectionLabel("Biblioteca de macros").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val btnNew = Button(this).apply {
            text = "＋ Nuevo"; textSize = 12f
            setTextColor(UiTheme.TEXT_PRIMARY)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = UiTheme.gradient(this@MainActivity, UiTheme.SUCCESS, UiTheme.SUCCESS_DARK, 999)
            stateListAnimator = null
            setAllCaps(false)
            setPadding(dpToPx(20), dpToPx(8), dpToPx(20), dpToPx(8))
            setOnClickListener { startActivity(Intent(this@MainActivity, MacroRecorderActivity::class.java)) }
        }
        header.addView(btnNew, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(38)))
        root.addView(header)

        root.addView(divider())

        val scroll = ScrollView(this)
        macroListContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 8, 16, 16) }
        scroll.addView(macroListContainer)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun refreshMacrosTab() {
        macroListContainer.removeAllViews()
        val allMacros = MacroLibrary.getAllMacros(this)
        val activeMacroId = MacroController.getActiveMacroId(this)
        allMacros.forEach { macro ->
            val card = buildMacroCard(macro, macro.id == activeMacroId)
            macroListContainer.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) })
        }
    }

    private fun buildMacroCard(macro: Macro, isActive: Boolean): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiTheme.roundedStroke(
                this@MainActivity,
                if (isActive) 0xFF1A2740.toInt() else UiTheme.BG_SURFACE,
                if (isActive) UiTheme.PRIMARY else 0xFF252D40.toInt(),
                14, if (isActive) 2 else 1
            )
            setPadding(dpToPx(16), dpToPx(14), dpToPx(14), dpToPx(14))
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setGravity(Gravity.CENTER_VERTICAL)
        }
        val nameView = TextView(this).apply {
            text = (if (macro.isBuiltin) "⭐ " else "📝 ") + macro.name
            setTextColor(if (isActive) Color.parseColor("#79C0FF") else Color.WHITE)
            textSize = 13f
        }
        topRow.addView(nameView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        if (!macro.isBuiltin) {
            val btnDel = Button(this).apply {
                text = "🗑"; textSize = 11f; setTextColor(Color.parseColor("#F85149"))
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(8, 4, 8, 4)
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage("¿Eliminar macro '${macro.name}'?")
                        .setPositiveButton("Eliminar") { _, _ ->
                            MacroLibrary.deleteMacro(this@MainActivity, macro.id)
                            refreshMacrosTab()
                        }.setNegativeButton("Cancelar", null).show()
                }
            }
            topRow.addView(btnDel, LinearLayout.LayoutParams(dpToPx(40), dpToPx(34)))
        }

        val btnSelect = Button(this).apply {
            text = if (isActive) "✓ Activo" else "▶ Usar"
            textSize = 11f; setTextColor(UiTheme.TEXT_PRIMARY)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = if (isActive)
                UiTheme.gradient(this@MainActivity, UiTheme.SUCCESS, UiTheme.SUCCESS_DARK, 999)
            else
                UiTheme.gradient(this@MainActivity, UiTheme.PRIMARY, UiTheme.PRIMARY_DARK, 999)
            stateListAnimator = null
            setAllCaps(false)
            setOnClickListener {
                MacroController.setActiveMacroId(this@MainActivity, macro.id)
                refreshMacrosTab()
                refreshHomeTab()
                Toast.makeText(this@MainActivity, "'${macro.name}' seleccionado", Toast.LENGTH_SHORT).show()
            }
        }
        topRow.addView(btnSelect, LinearLayout.LayoutParams(dpToPx(86), dpToPx(36)))
        card.addView(topRow)

        val descView = TextView(this).apply {
            text = macro.description; setTextColor(Color.GRAY); textSize = 11f
            setPadding(0, 4, 0, 0)
        }
        card.addView(descView)

        if (!macro.isBuiltin && macro.steps.isNotEmpty()) {
            val stepsInfo = TextView(this).apply {
                text = "${macro.steps.size} pasos · ${if (macro.isLooping) "bucle" else "una vez"}"
                setTextColor(Color.parseColor("#8B949E")); textSize = 10f
            }
            card.addView(stepsInfo)
        }
        return card
    }

    // ── Tab 3: Auto IA ───────────────────────────────────────────────────

    private lateinit var etApiKey: EditText
    private lateinit var tvModeSelector: TextView
    private lateinit var tvIntervalValue: TextView

    private fun buildTabAutoIA(): ScrollView {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 20, 24, 24)
            setBackgroundColor(Color.parseColor("#0D1117"))
        }

        root.addView(sectionLabel("🤖 Auto IA con Gemini"))
        root.addView(infoText("Gemini analiza la pantalla de tu juego y decide qué tocar automáticamente. Necesitas una API Key gratuita de Google AI Studio."))
        root.addView(divider())

        // API Key
        root.addView(fieldLabel("API Key de Gemini"))
        etApiKey = EditText(this).apply {
            hint = "AIza…"; textSize = 13f
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#21262D"))
            transformationMethod = PasswordTransformationMethod.getInstance()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(12, 10, 12, 10)
            setText(MacroController.getGeminiApiKey(this@MainActivity))
        }
        root.addView(etApiKey, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 4, 0, 4) })

        val btnSaveKey = Button(this).apply {
            text = "Guardar clave"; textSize = 12f; setTextColor(UiTheme.TEXT_PRIMARY)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = UiTheme.gradient(this@MainActivity, UiTheme.PRIMARY, UiTheme.PRIMARY_DARK, 999)
            stateListAnimator = null
            setAllCaps(false)
            setPadding(dpToPx(22), dpToPx(10), dpToPx(22), dpToPx(10))
            setOnClickListener {
                MacroController.setGeminiApiKey(this@MainActivity, etApiKey.text.toString().trim())
                Toast.makeText(this@MainActivity, "Clave guardada", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(btnSaveKey, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 4, 0, 16) })

        root.addView(divider())

        // Mode selector
        root.addView(fieldLabel("Modo de juego"))
        val modes = arrayOf("all" to "Todo (recomendado)", "train" to "Entrenar tropas",
            "research" to "Investigar", "recruit" to "Reclutar héroes",
            "collect" to "Recolectar recursos", "hunt" to "Cazar bestias", "build" to "Construir")
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setGravity(Gravity.CENTER_VERTICAL)
        }
        tvModeSelector = TextView(this).apply {
            val current = modes.find { it.first == MacroController.getAutoPlayMode(this@MainActivity) }?.second ?: "Todo"
            text = current; setTextColor(Color.WHITE); textSize = 13f
            setBackgroundColor(Color.parseColor("#21262D")); setPadding(12, 10, 12, 10)
        }
        modeRow.addView(tvModeSelector, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val btnMode = Button(this).apply {
            text = "Cambiar"; textSize = 12f; setTextColor(UiTheme.TEXT_PRIMARY)
            background = UiTheme.roundedStroke(this@MainActivity, UiTheme.BG_SURFACE_HI, 0xFF334155.toInt(), 999, 1)
            stateListAnimator = null
            setAllCaps(false)
            setPadding(dpToPx(18), dpToPx(8), dpToPx(18), dpToPx(8))
            setOnClickListener {
                val labels = modes.map { it.second }.toTypedArray()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Modo de juego")
                    .setItems(labels) { _, which ->
                        val (key, label) = modes[which]
                        MacroController.setAutoPlayMode(this@MainActivity, key)
                        tvModeSelector.text = label
                    }.show()
            }
        }
        modeRow.addView(btnMode, LinearLayout.LayoutParams(dpToPx(90), dpToPx(40)))
        root.addView(modeRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 4, 0, 12) })

        // Interval
        root.addView(fieldLabel("Intervalo entre acciones"))
        val intervalRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setGravity(Gravity.CENTER_VERTICAL) }
        tvIntervalValue = TextView(this).apply {
            text = "${MacroController.getAutoPlayIntervalS(this@MainActivity)}s"
            setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpToPx(50), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val seekInterval = SeekBar(this).apply {
            max = 7 // 3..10
            progress = MacroController.getAutoPlayIntervalS(this@MainActivity) - 3
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) {
                    val v = p + 3
                    MacroController.setAutoPlayIntervalS(this@MainActivity, v)
                    tvIntervalValue.text = "${v}s"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        intervalRow.addView(seekInterval, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        intervalRow.addView(tvIntervalValue)
        root.addView(intervalRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 4, 0, 16) })

        root.addView(divider())

        // Start/Stop button
        tvGeminiStatus = TextView(this).apply {
            text = "Estado: Inactivo"; setTextColor(Color.GRAY); textSize = 12f; gravity = Gravity.CENTER
        }
        root.addView(tvGeminiStatus, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 8, 0, 8) })

        btnGeminiToggle = Button(this).apply {
            text = "▶  Iniciar Auto IA"; textSize = 15f; setTextColor(UiTheme.TEXT_PRIMARY)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = UiTheme.gradient(this@MainActivity, UiTheme.SUCCESS, UiTheme.SUCCESS_DARK, 999)
            stateListAnimator = null
            setAllCaps(false)
            setPadding(0, dpToPx(16), 0, dpToPx(16))
            setOnClickListener { toggleGeminiAutoPlay() }
        }
        root.addView(btnGeminiToggle, fullWidthBtn())
        root.addView(divider())

        // Log
        root.addView(sectionLabel("Registro de acciones"))
        val logScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0D1117"))
        }
        geminiLogText = TextView(this).apply {
            text = "—"; setTextColor(Color.parseColor("#8B949E")); textSize = 11f
            setPadding(8, 8, 8, 8); typeface = android.graphics.Typeface.MONOSPACE
        }
        logScroll.addView(geminiLogText)
        root.addView(logScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(200)
        ).apply { setMargins(0, 4, 0, 0) })

        scroll.addView(root); return scroll
    }

    private fun refreshAutoIATab() {
        val running = MacroController.geminiRunning
        if (::btnGeminiToggle.isInitialized) {
            btnGeminiToggle.text = if (running) "⏹  Detener Auto IA" else "▶  Iniciar Auto IA"
            btnGeminiToggle.background = if (running)
                UiTheme.gradient(this@MainActivity, UiTheme.DANGER, UiTheme.DANGER_DARK, 999)
            else
                UiTheme.gradient(this@MainActivity, UiTheme.SUCCESS, UiTheme.SUCCESS_DARK, 999)
        }
        if (::tvGeminiStatus.isInitialized) {
            tvGeminiStatus.text = if (running) "Estado: ● Activo" else "Estado: Inactivo"
            tvGeminiStatus.setTextColor(if (running) Color.parseColor("#3FB950") else Color.GRAY)
        }
    }

    private fun toggleGeminiAutoPlay() {
        if (MacroController.geminiRunning) {
            sendBroadcast(Intent(MacroController.ACTION_GEMINI_STOP).apply { setPackage(packageName) })
            MacroController.geminiRunning = false
        } else {
            val key = MacroController.getGeminiApiKey(this)
            if (key.isBlank()) {
                Toast.makeText(this, "Primero guarda tu API Key de Gemini", Toast.LENGTH_LONG).show()
                return
            }
            if (!isAccessibilityEnabled()) {
                Toast.makeText(this, "Activa el servicio de accesibilidad primero", Toast.LENGTH_LONG).show()
                return
            }
            startService(Intent(this, GeminiAutoPlayService::class.java).apply {
                action = MacroController.ACTION_GEMINI_START
            })
            MacroController.geminiRunning = true
        }
        refreshAutoIATab()
    }

    // ── Tab 4: Ajustes ───────────────────────────────────────────────────

    private fun buildTabSettings(): ScrollView {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 20, 24, 24)
            setBackgroundColor(Color.parseColor("#0D1117"))
        }
        root.addView(sectionLabel("Parámetros del macro de clan"))

        val tapDelay = MacroController.getTapDelayMs(this)
        addSeekSetting(root, "Delay entre toques", 1000, 3000, tapDelay.toInt(), { "${it}ms" }) {
            MacroController.setTapDelayMs(this, it.toLong())
        }
        val tapsScroll = MacroController.getTapsBeforeScroll(this)
        addSeekSetting(root, "Toques antes de scroll", 2, 20, tapsScroll, { "$it toques" }) {
            MacroController.setTapsBeforeScroll(this, it)
        }
        root.addView(divider())
        root.addView(sectionLabel("Posición de la cuadrícula (% pantalla)"))

        val c1 = (MacroController.getCol1XPct(this) * 100).toInt()
        addSeekSetting(root, "Columna 1 X", 10, 50, c1, { "$it%" }) {
            MacroController.setCol1XPct(this, it / 100f)
        }
        val c2 = (MacroController.getCol2XPct(this) * 100).toInt()
        addSeekSetting(root, "Columna 2 X", 50, 90, c2, { "$it%" }) {
            MacroController.setCol2XPct(this, it / 100f)
        }
        val rowY = (MacroController.getRowStartYPct(this) * 100).toInt()
        addSeekSetting(root, "Primera fila Y", 10, 50, rowY, { "$it%" }) {
            MacroController.setRowStartYPct(this, it / 100f)
        }
        val rowH = (MacroController.getRowHeightPct(this) * 100).toInt()
        addSeekSetting(root, "Altura de fila", 5, 25, rowH, { "$it%" }) {
            MacroController.setRowHeightPct(this, it / 100f)
        }
        root.addView(divider())
        val btnReset = Button(this).apply {
            text = "Restablecer valores por defecto"; textSize = 13f
            setTextColor(UiTheme.TEXT_PRIMARY)
            background = UiTheme.roundedStroke(this@MainActivity, UiTheme.BG_SURFACE_HI, 0xFF334155.toInt(), 999, 1)
            stateListAnimator = null
            setAllCaps(false)
            setPadding(0, dpToPx(14), 0, dpToPx(14))
            setOnClickListener {
                MacroController.setTapDelayMs(this@MainActivity, 1500L)
                MacroController.setTapsBeforeScroll(this@MainActivity, 8)
                MacroController.setCol1XPct(this@MainActivity, 0.25f)
                MacroController.setCol2XPct(this@MainActivity, 0.75f)
                MacroController.setRowStartYPct(this@MainActivity, 0.30f)
                MacroController.setRowHeightPct(this@MainActivity, 0.14f)
                switchTab(3)
                Toast.makeText(this@MainActivity, "Valores restablecidos", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(btnReset, fullWidthBtn())
        scroll.addView(root); return scroll
    }

    private fun addSeekSetting(parent: LinearLayout, label: String, min: Int, max: Int,
                                current: Int, fmt: (Int) -> String, onChange: (Int) -> Unit) {
        val tv = TextView(this).apply {
            text = "$label: ${fmt(current)}"; setTextColor(Color.LTGRAY); textSize = 13f
        }
        parent.addView(tv)
        val sb = SeekBar(this).apply {
            this.max = max - min; progress = (current - min).coerceIn(0, this.max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                    val v = p + min; tv.text = "$label: ${fmt(v)}"; onChange(v)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        parent.addView(sb, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 14) })
    }

    // ── Permissions ───────────────────────────────────────────────────────

    private fun hasOverlayPermission() = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    private fun isAccessibilityEnabled(): Boolean {
        val name = "${packageName}/${KingshotAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabled.contains(name)
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= 23)
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")), REQUEST_OVERLAY)
    }

    private fun openAccessibilitySettings() {
        AlertDialog.Builder(this)
            .setTitle("Activar accesibilidad")
            .setMessage("En la siguiente pantalla:\n\n1. Busca 'Kingshot Macro'\n2. Actívalo\n3. Acepta el diálogo\n\nLuego vuelve aquí.")
            .setPositiveButton("Ir a Ajustes") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun launchOverlay() {
        if (!hasOverlayPermission() || !isAccessibilityEnabled()) return
        startService(Intent(this, FloatingOverlayService::class.java))
        Toast.makeText(this, "Panel flotante iniciado", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        refreshHomeTab()
    }

    // ── UI helpers ────────────────────────────────────────────────────────

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text; textSize = 16f
        setTextColor(UiTheme.TEXT_PRIMARY)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        letterSpacing = 0.01f
        setPadding(0, dpToPx(8), 0, dpToPx(10))
    }
    private fun fieldLabel(text: String) = TextView(this).apply {
        this.text = text; textSize = 12f
        setTextColor(UiTheme.TEXT_SECONDARY)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(0, dpToPx(12), 0, dpToPx(6))
    }
    private fun infoText(text: String) = TextView(this).apply {
        this.text = text; textSize = 12f
        setTextColor(UiTheme.TEXT_SECONDARY)
        setLineSpacing(dpToPx(2).toFloat(), 1f)
        setPadding(0, dpToPx(4), 0, dpToPx(8))
    }
    private fun divider() = View(this).apply {
        setBackgroundColor(UiTheme.DIVIDER)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)).apply {
            setMargins(0, dpToPx(14), 0, dpToPx(14))
        }
    }
    private fun fullWidthBtn() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        setMargins(0, 8, 0, 8)
    }
    private fun cardMargin() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        setMargins(0, 0, 0, 8)
    }
    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
}
