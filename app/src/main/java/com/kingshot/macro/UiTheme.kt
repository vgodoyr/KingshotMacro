package com.kingshot.macro

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Paleta y helpers de estilo "premium" para la app.
 * Sin AndroidX, sin Material — solo drawables construidos a mano.
 */
object UiTheme {

    // Paleta — fondos profundos azulados, acentos vibrantes
    const val BG_DEEP        = 0xFF0A0E1A.toInt()    // fondo principal
    const val BG_SURFACE     = 0xFF131826.toInt()    // tarjetas
    const val BG_SURFACE_HI  = 0xFF1B2233.toInt()    // elementos elevados
    const val BG_INPUT       = 0xFF1F2738.toInt()    // campos de input

    const val PRIMARY        = 0xFF6366F1.toInt()    // indigo (acción primaria)
    const val PRIMARY_DARK   = 0xFF4F46E5.toInt()
    const val ACCENT         = 0xFF06B6D4.toInt()    // cyan (links, headers)
    const val SUCCESS        = 0xFF10B981.toInt()    // verde
    const val SUCCESS_DARK   = 0xFF059669.toInt()
    const val WARNING        = 0xFFF59E0B.toInt()    // ámbar
    const val DANGER         = 0xFFEF4444.toInt()    // rojo
    const val DANGER_DARK    = 0xFFDC2626.toInt()

    const val TEXT_PRIMARY   = 0xFFF1F5F9.toInt()
    const val TEXT_SECONDARY = 0xFF94A3B8.toInt()
    const val TEXT_MUTED     = 0xFF64748B.toInt()
    const val DIVIDER        = 0xFF1E293B.toInt()

    fun dp(ctx: Context, dp: Int): Int = (dp * ctx.resources.displayMetrics.density).toInt()

    // ── Drawables ────────────────────────────────────────────────────────

    fun rounded(color: Int, radiusDp: Int = 14): GradientDrawable =
        GradientDrawable().apply {
            setShape(GradientDrawable.RECTANGLE)
            setCornerRadius(radiusDp * 2.5f)
            setColor(color)
        }

    fun roundedDp(ctx: Context, color: Int, radiusDp: Int = 14): GradientDrawable =
        GradientDrawable().apply {
            setShape(GradientDrawable.RECTANGLE)
            setCornerRadius(dp(ctx, radiusDp).toFloat())
            setColor(color)
        }

    fun roundedStroke(ctx: Context, fill: Int, stroke: Int, radiusDp: Int = 14, strokeDp: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            setShape(GradientDrawable.RECTANGLE)
            setCornerRadius(dp(ctx, radiusDp).toFloat())
            setColor(fill)
            setStroke(dp(ctx, strokeDp), stroke)
        }

    fun gradient(ctx: Context, c1: Int, c2: Int, radiusDp: Int = 14, vertical: Boolean = false): GradientDrawable =
        GradientDrawable(
            if (vertical) GradientDrawable.Orientation.TOP_BOTTOM else GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(c1, c2)
        ).apply {
            setShape(GradientDrawable.RECTANGLE)
            setCornerRadius(dp(ctx, radiusDp).toFloat())
        }

    fun ripple(content: GradientDrawable, rippleColor: Int = 0x33FFFFFF): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(rippleColor), content, content)

    // ── View helpers ─────────────────────────────────────────────────────

    fun card(ctx: Context, radiusDp: Int = 16): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedStroke(ctx, BG_SURFACE, 0xFF252D40.toInt(), radiusDp, 1)
        val pad = dp(ctx, 16)
        setPadding(pad, pad, pad, pad)
    }

    // PILL_RADIUS = ridiculously high → siempre cápsula independientemente de la altura.
    private const val PILL_RADIUS = 999

    fun primaryButton(ctx: Context, label: String, onClick: () -> Unit): Button = Button(ctx).apply {
        text = label
        textSize = 14f
        setTextColor(TEXT_PRIMARY)
        typeface = Typeface.DEFAULT_BOLD
        background = ripple(gradient(ctx, PRIMARY, PRIMARY_DARK, PILL_RADIUS))
        setPadding(dp(ctx, 26), dp(ctx, 14), dp(ctx, 26), dp(ctx, 14))
        stateListAnimator = null
        setAllCaps(false)
        setOnClickListener { onClick() }
    }

    fun successButton(ctx: Context, label: String, onClick: () -> Unit): Button = Button(ctx).apply {
        text = label
        textSize = 14f
        setTextColor(TEXT_PRIMARY)
        typeface = Typeface.DEFAULT_BOLD
        background = ripple(gradient(ctx, SUCCESS, SUCCESS_DARK, PILL_RADIUS))
        setPadding(dp(ctx, 26), dp(ctx, 14), dp(ctx, 26), dp(ctx, 14))
        stateListAnimator = null
        setAllCaps(false)
        setOnClickListener { onClick() }
    }

    fun secondaryButton(ctx: Context, label: String, onClick: () -> Unit): Button = Button(ctx).apply {
        text = label
        textSize = 13f
        setTextColor(TEXT_PRIMARY)
        background = ripple(roundedStroke(ctx, BG_SURFACE_HI, 0xFF334155.toInt(), PILL_RADIUS, 1))
        setPadding(dp(ctx, 22), dp(ctx, 10), dp(ctx, 22), dp(ctx, 10))
        stateListAnimator = null
        setAllCaps(false)
        setOnClickListener { onClick() }
    }

    fun dangerButton(ctx: Context, label: String, onClick: () -> Unit): Button = Button(ctx).apply {
        text = label
        textSize = 13f
        setTextColor(TEXT_PRIMARY)
        typeface = Typeface.DEFAULT_BOLD
        background = ripple(gradient(ctx, DANGER, DANGER_DARK, PILL_RADIUS))
        setPadding(dp(ctx, 22), dp(ctx, 10), dp(ctx, 22), dp(ctx, 10))
        stateListAnimator = null
        setAllCaps(false)
        setOnClickListener { onClick() }
    }

    fun sectionTitle(ctx: Context, text: String): TextView = TextView(ctx).apply {
        this.text = text
        setTextColor(TEXT_PRIMARY)
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(ctx, 4), 0, dp(ctx, 8))
    }

    fun caption(ctx: Context, text: String): TextView = TextView(ctx).apply {
        this.text = text
        setTextColor(TEXT_SECONDARY)
        textSize = 12f
    }

    fun pill(ctx: Context, label: String, color: Int): TextView = TextView(ctx).apply {
        text = label
        setTextColor(TEXT_PRIMARY)
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        background = roundedDp(ctx, color, 999)
        setPadding(dp(ctx, 10), dp(ctx, 4), dp(ctx, 10), dp(ctx, 4))
        gravity = Gravity.CENTER
    }

    fun divider(ctx: Context): View = View(ctx).apply {
        setBackgroundColor(DIVIDER)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 1)
        ).apply { setMargins(0, dp(ctx, 12), 0, dp(ctx, 12)) }
    }
}
