package com.kingshot.macro

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Activity de prueba MINIMA — no usa companion objects, fields, ni nada
 * más allá de los APIs más básicos de Android. Si esta abre, sabemos
 * que la build/install pipeline funciona y el bug está en MainActivity
 * o en KingshotApp.
 */
class BareTestActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "Kingshot Macro v4 BARE TEST\n\nSi ves esto, la app instala bien.\nEl bug estaba en MainActivity / KingshotApp."
        tv.setTextColor(Color.WHITE)
        tv.textSize = 18f
        tv.gravity = Gravity.CENTER
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setBackgroundColor(Color.parseColor("#0D1117"))
        layout.setGravity(Gravity.CENTER)
        layout.addView(tv)
        setContentView(layout)
    }
}
