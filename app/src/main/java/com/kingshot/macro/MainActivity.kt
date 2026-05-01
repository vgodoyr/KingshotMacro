package com.kingshot.macro

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.app.Activity
import android.graphics.Color

class MainActivity : Activity() {

    private val REQUEST_OVERLAY = 1001
    private lateinit var statusOverlay: TextView
    private lateinit var statusAccess: TextView
    private lateinit var btnOverlay: Button
    private lateinit var btnAccess: Button
    private lateinit var btnLaunch: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        title = "Kingshot Macro"
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }

    private fun buildLayout(): ScrollView {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 40)
        }

        val header = TextView(this).apply {
            text = "Kingshot Macro"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#1565C0"))
            setPadding(0, 0, 0, 8)
        }
        root.addView(header)

        val subtitle = TextView(this).apply {
            text = "Auto clan invitation tapper"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, 32)
        }
        root.addView(subtitle)

        // Step 1: Overlay permission
        root.addView(buildSectionLabel("Step 1: Overlay Permission"))
        statusOverlay = buildStatusText()
        root.addView(statusOverlay)
        btnOverlay = buildActionButton("Grant Overlay Permission") {
            requestOverlayPermission()
        }
        root.addView(btnOverlay)

        root.addView(buildDivider())

        // Step 2: Accessibility permission
        root.addView(buildSectionLabel("Step 2: Accessibility Service"))
        statusAccess = buildStatusText()
        root.addView(statusAccess)
        btnAccess = buildActionButton("Enable Accessibility Service") {
            openAccessibilitySettings()
        }
        root.addView(btnAccess)

        root.addView(buildDivider())

        // Step 3: Launch
        root.addView(buildSectionLabel("Step 3: Launch Overlay"))
        val launchDesc = TextView(this).apply {
            text = "Once permissions are granted, launch the floating control panel. " +
                    "Open Kingshot and navigate to the Clan Invitation screen, then tap Start."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 12)
        }
        root.addView(launchDesc)
        btnLaunch = buildActionButton("Launch Floating Control") {
            launchOverlay()
        }
        root.addView(btnLaunch)

        root.addView(buildDivider())

        // Settings shortcut
        val btnSettings = buildActionButton("Open Settings") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        root.addView(btnSettings)

        // Instructions
        root.addView(buildSectionLabel("How it works"))
        val instructions = TextView(this).apply {
            text = "1. The macro taps a 2-column grid of player cards automatically.\n" +
                    "2. Every 8 taps it scrolls down to load more players.\n" +
                    "3. Use Start/Pause/Stop from the floating panel.\n" +
                    "4. Tap interval (1–3 s) and scroll frequency can be configured in Settings."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 16)
        }
        root.addView(instructions)

        scroll.addView(root)
        return scroll
    }

    private fun buildSectionLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(Color.parseColor("#1565C0"))
        setPadding(0, 16, 0, 4)
    }

    private fun buildStatusText() = TextView(this).apply {
        text = "Checking..."
        textSize = 13f
        setPadding(0, 0, 0, 8)
    }

    private fun buildDivider(): View = View(this).apply {
        setBackgroundColor(Color.LTGRAY)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        ).apply { setMargins(0, 16, 0, 16) }
    }

    private fun buildActionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 8) }
    }

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    private fun isAccessibilityEnabled(): Boolean {
        val name = "${packageName}/${KingshotAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return enabled.contains(name)
    }

    private fun refreshPermissionStatus() {
        val overlayOk = hasOverlayPermission()
        val accessOk = isAccessibilityEnabled()

        statusOverlay.text = if (overlayOk) "✓ Granted" else "✗ Not granted"
        statusOverlay.setTextColor(if (overlayOk) Color.parseColor("#2E7D32") else Color.RED)
        btnOverlay.isEnabled = !overlayOk

        statusAccess.text = if (accessOk) "✓ Enabled" else "✗ Not enabled"
        statusAccess.setTextColor(if (accessOk) Color.parseColor("#2E7D32") else Color.RED)
        btnAccess.isEnabled = !accessOk

        btnLaunch.isEnabled = overlayOk && accessOk
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_OVERLAY)
        }
    }

    private fun openAccessibilitySettings() {
        AlertDialog.Builder(this)
            .setTitle("Enable Accessibility Service")
            .setMessage(
                "In the next screen:\n\n" +
                "1. Find 'Kingshot Macro'\n" +
                "2. Tap it and enable the service\n" +
                "3. Accept the permission dialog\n\n" +
                "Then return to this app."
            )
            .setPositiveButton("Go to Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchOverlay() {
        if (!hasOverlayPermission() || !isAccessibilityEnabled()) return
        startService(Intent(this, FloatingOverlayService::class.java))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        refreshPermissionStatus()
    }
}
