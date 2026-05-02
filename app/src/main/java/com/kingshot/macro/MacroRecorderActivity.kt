package com.kingshot.macro

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MacroRecorderActivity : Activity() {

    private val steps = mutableListOf<MacroStep>()
    private var reticleXPct = 50f
    private var reticleYPct = 50f
    private var isDraggingReticle = false

    private lateinit var reticleView: ReticleView
    private lateinit var stepsText: TextView
    private lateinit var posText: TextView
    private lateinit var btnAddTap: Button
    private lateinit var btnUndo: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        setContentView(buildLayout())
        updateStepsList()
    }

    private fun buildLayout(): FrameLayout {
        val root = FrameLayout(this)

        // Dark semi-transparent background
        val bg = View(this).apply {
            setBackgroundColor(Color.argb(200, 0, 0, 0))
        }
        root.addView(bg, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Reticle layer (full-screen draggable)
        reticleView = ReticleView(this)
        val reticleParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        root.addView(reticleView, reticleParams)

        reticleView.setOnTouchListener { _, event ->
            handleReticleTouch(event)
            true
        }

        // Bottom control panel (fixed height)
        val panel = buildBottomPanel()
        val panelParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        root.addView(panel, panelParams)

        return root
    }

    private fun buildBottomPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(230, 15, 15, 40))
            setPadding(16, 12, 16, 24)
        }

        // Title
        val title = TextView(this).apply {
            text = "Grabador de Macro  (arrastra la mira ⊕)"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        panel.addView(title)

        // Position display
        posText = TextView(this).apply {
            text = "Posición: 50% × 50%"
            setTextColor(Color.LTGRAY)
            textSize = 12f
            gravity = Gravity.CENTER
        }
        panel.addView(posText)

        // Steps list
        val stepsScroll = ScrollView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(80)
            )
            lp.setMargins(0, 8, 0, 8)
            layoutParams = lp
        }
        stepsText = TextView(this).apply {
            text = "Sin pasos aún"
            setTextColor(Color.argb(255, 200, 200, 200))
            textSize = 11f
            setPadding(8, 4, 8, 4)
        }
        stepsScroll.addView(stepsText)
        panel.addView(stepsScroll)

        // Button row 1
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        btnAddTap = makeBtn("+ Toque", Color.argb(220, 0, 140, 60)) { addTap() }
        row1.addView(btnAddTap, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(0, 0, 4, 0)
        })

        val btnAddSwipe = makeBtn("+ Deslizar", Color.argb(220, 0, 90, 160)) { addSwipe() }
        row1.addView(btnAddSwipe, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(0, 0, 4, 0)
        })

        val btnAddWait = makeBtn("+ Espera 1s", Color.argb(220, 100, 80, 0)) { addWait() }
        row1.addView(btnAddWait, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        panel.addView(row1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 6) })

        // Button row 2
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        btnUndo = makeBtn("↩ Deshacer", Color.argb(220, 120, 60, 0)) { undo() }
        row2.addView(btnUndo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(0, 0, 4, 0)
        })

        val btnSave = makeBtn("💾 Guardar", Color.argb(220, 30, 100, 180)) { promptSave() }
        row2.addView(btnSave, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(0, 0, 4, 0)
        })

        val btnCancel = makeBtn("✕ Cancelar", Color.argb(220, 150, 30, 30)) { finish() }
        row2.addView(btnCancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        panel.addView(row2, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return panel
    }

    private fun makeBtn(label: String, color: Int, action: () -> Unit): Button =
        Button(this).apply {
            text = label; textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(color)
            setPadding(4, 8, 4, 8)
            setOnClickListener { action() }
        }

    // ── Touch handling for reticle drag ───────────────────────────────────

    private fun handleReticleTouch(event: MotionEvent) {
        val w = reticleView.width.toFloat()
        val h = reticleView.height.toFloat()
        if (w == 0f || h == 0f) return

        when (event.action) {
            MotionEvent.ACTION_DOWN -> isDraggingReticle = true
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                reticleXPct = ((event.x / w) * 100f).coerceIn(0f, 100f)
                reticleYPct = ((event.y / h) * 100f).coerceIn(0f, 100f)
                reticleView.setPosition(reticleXPct, reticleYPct)
                updatePosDisplay()
                if (event.action == MotionEvent.ACTION_UP) isDraggingReticle = false
            }
        }
    }

    private fun updatePosDisplay() {
        posText.text = "Posición: ${reticleXPct.toInt()}% × ${reticleYPct.toInt()}%"
    }

    // ── Macro step actions ────────────────────────────────────────────────

    private fun addTap() {
        steps.add(MacroStep.tap(reticleXPct.toInt(), reticleYPct.toInt(), 800))
        updateStepsList()
        toast("Toque añadido en ${reticleXPct.toInt()}% × ${reticleYPct.toInt()}%")
    }

    private fun addSwipe() {
        // Use current position as start; user should move reticle to end and tap again
        if (steps.lastOrNull()?.type == "swipe_start") {
            // Complete the swipe
            val prev = steps.removeLast()
            steps.add(MacroStep.swipe(prev.x1, prev.y1, reticleXPct.toInt(), reticleYPct.toInt(), 300, 800))
            updateStepsList()
            toast("Deslizar añadido")
        } else {
            // Mark start point
            steps.add(MacroStep("swipe_start", reticleXPct.toInt(), reticleYPct.toInt()))
            updateStepsList()
            toast("Punto inicio marcado. Mueve la mira y pulsa '+ Deslizar' de nuevo")
        }
    }

    private fun addWait() {
        steps.add(MacroStep.wait(1000))
        updateStepsList()
        toast("Espera 1s añadida")
    }

    private fun undo() {
        if (steps.isEmpty()) return
        steps.removeLast()
        updateStepsList()
    }

    private fun updateStepsList() {
        btnUndo.isEnabled = steps.isNotEmpty()
        if (steps.isEmpty()) {
            stepsText.text = "Sin pasos aún — arrastra la mira y añade acciones"
            return
        }
        stepsText.text = steps.mapIndexed { i, s ->
            val n = i + 1
            when (s.type) {
                "tap"        -> "$n. Toque (${s.x1}%×${s.y1}%) +${s.waitAfterMs}ms"
                "swipe"      -> "$n. Deslizar (${s.x1}%×${s.y1}%)→(${s.x2}%×${s.y2}%) +${s.waitAfterMs}ms"
                "swipe_start"-> "$n. [inicio deslizar ${s.x1}%×${s.y1}%] ← mueve mira y pulsa de nuevo"
                "wait"       -> "$n. Espera ${s.waitAfterMs}ms"
                else         -> "$n. ${s.type}"
            }
        }.joinToString("\n")
    }

    private fun promptSave() {
        val validSteps = steps.filter { it.type != "swipe_start" }
        if (validSteps.isEmpty()) {
            toast("Añade al menos un paso antes de guardar"); return
        }
        val input = EditText(this).apply {
            hint = "Nombre del macro"
            setPadding(16, 8, 16, 8)
        }
        AlertDialog.Builder(this)
            .setTitle("Guardar macro")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) { toast("Introduce un nombre"); return@setPositiveButton }
                val macro = Macro(
                    id = MacroLibrary.newId(),
                    name = name,
                    description = "Macro personalizado — ${validSteps.size} pasos",
                    isBuiltin = false,
                    isLooping = false,
                    steps = validSteps
                )
                MacroLibrary.saveMacro(this, macro)
                toast("Macro '$name' guardado")
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}

// ── Reticle custom view ───────────────────────────────────────────────────

class ReticleView(context: Context) : View(context) {

    private var xPct = 50f
    private var yPct = 50f

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = 2f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
    }

    fun setPosition(xPct: Float, yPct: Float) {
        this.xPct = xPct; this.yPct = yPct
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width * xPct / 100f
        val cy = height * yPct / 100f
        val r = 40f

        canvas.drawCircle(cx, cy, r, circlePaint)
        canvas.drawLine(cx - r - 10, cy, cx + r + 10, cy, crossPaint)
        canvas.drawLine(cx, cy - r - 10, cx, cy + r + 10, crossPaint)

        val label = "${xPct.toInt()}% × ${yPct.toInt()}%"
        canvas.drawText(label, cx + r + 6, cy - 8, labelPaint)
    }
}
