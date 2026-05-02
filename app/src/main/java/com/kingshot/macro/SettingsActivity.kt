package com.kingshot.macro

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView

class SettingsActivity : Activity() {

    private lateinit var tvTapDelay: TextView
    private lateinit var tvScrollInterval: TextView
    private lateinit var tvCol1: TextView
    private lateinit var tvCol2: TextView
    private lateinit var tvRowStart: TextView
    private lateinit var tvRowHeight: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Macro Settings"
        setContentView(buildLayout())
    }

    private fun buildLayout(): ScrollView {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val header = TextView(this).apply {
            text = "Macro Settings"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#1565C0"))
            setPadding(0, 0, 0, 24)
        }
        root.addView(header)

        // Tap delay 1000-3000ms
        val tapDelay = MacroController.getTapDelayMs(this)
        tvTapDelay = addSeekBarSetting(
            root,
            label = "Tap delay",
            min = 1000,
            max = 3000,
            current = tapDelay.toInt(),
            format = { "${it}ms" }
        ) { value ->
            MacroController.setTapDelayMs(this, value.toLong())
            tvTapDelay.text = "Tap delay: ${value}ms"
        }

        // Taps before scroll 2-20
        val tapsBeforeScroll = MacroController.getTapsBeforeScroll(this)
        tvScrollInterval = addSeekBarSetting(
            root,
            label = "Taps before scroll",
            min = 2,
            max = 20,
            current = tapsBeforeScroll,
            format = { "${it} taps" }
        ) { value ->
            MacroController.setTapsBeforeScroll(this, value)
            tvScrollInterval.text = "Taps before scroll: $value"
        }

        addDivider(root)
        root.addView(buildLabel("Grid Position (% of screen)"))

        // Column 1 X position 10-50%
        tvCol1 = addSeekBarSetting(
            root,
            label = "Column 1 X",
            min = 10,
            max = 50,
            current = (MacroController.getCol1XPct(this) * 100).toInt(),
            format = { "${it}%" }
        ) { value ->
            MacroController.setCol1XPct(this, value / 100f)
            tvCol1.text = "Column 1 X: ${value}%"
        }

        // Column 2 X position 50-90%
        tvCol2 = addSeekBarSetting(
            root,
            label = "Column 2 X",
            min = 50,
            max = 90,
            current = (MacroController.getCol2XPct(this) * 100).toInt(),
            format = { "${it}%" }
        ) { value ->
            MacroController.setCol2XPct(this, value / 100f)
            tvCol2.text = "Column 2 X: ${value}%"
        }

        // Row start Y 10-50%
        tvRowStart = addSeekBarSetting(
            root,
            label = "First row Y",
            min = 10,
            max = 50,
            current = (MacroController.getRowStartYPct(this) * 100).toInt(),
            format = { "${it}%" }
        ) { value ->
            MacroController.setRowStartYPct(this, value / 100f)
            tvRowStart.text = "First row Y: ${value}%"
        }

        // Row height 5-25%
        tvRowHeight = addSeekBarSetting(
            root,
            label = "Row height",
            min = 5,
            max = 25,
            current = (MacroController.getRowHeightPct(this) * 100).toInt(),
            format = { "${it}%" }
        ) { value ->
            MacroController.setRowHeightPct(this, value / 100f)
            tvRowHeight.text = "Row height: ${value}%"
        }

        val btnReset = Button(this).apply {
            text = "Reset to defaults"
            setOnClickListener { resetDefaults() }
        }
        root.addView(btnReset, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 24, 0, 0) })

        scroll.addView(root)
        return scroll
    }

    private fun buildLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.parseColor("#1565C0"))
        setPadding(0, 16, 0, 8)
    }

    private fun addDivider(parent: LinearLayout) {
        val divider = android.view.View(this).apply {
            setBackgroundColor(Color.LTGRAY)
        }
        parent.addView(divider, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        ).apply { setMargins(0, 16, 0, 8) })
    }

    private fun addSeekBarSetting(
        parent: LinearLayout,
        label: String,
        min: Int,
        max: Int,
        current: Int,
        format: (Int) -> String,
        onChange: (Int) -> Unit
    ): TextView {
        val tv = TextView(this).apply {
            text = "$label: ${format(current)}"
            textSize = 14f
        }
        parent.addView(tv)

        val seekBar = SeekBar(this).apply {
            this.max = max - min
            progress = (current - min).coerceIn(0, this.max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + min
                    tv.text = "$label: ${format(value)}"
                    onChange(value)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        parent.addView(seekBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 16) })

        return tv
    }

    private fun resetDefaults() {
        MacroController.setTapDelayMs(this, 1500L)
        MacroController.setTapsBeforeScroll(this, 8)
        MacroController.setCol1XPct(this, 0.25f)
        MacroController.setCol2XPct(this, 0.75f)
        MacroController.setRowStartYPct(this, 0.30f)
        MacroController.setRowHeightPct(this, 0.14f)
        recreate()
    }
}
