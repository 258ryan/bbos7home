package com.example.slatelauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View

/**
 * Skeuomorphic, gamepad-first keyboard: glossy dark keycaps, solid-blue focus, a word-suggestion strip on top.
 * The host (input method service) feeds D-pad / button events into [move] and [pressFocused];
 * touch works too.
 */
class BbKeyboardView(ctx: Context) : View(ctx) {

    enum class Act { CHAR, SHIFT, DEL, ENTER, SPACE, LAYER_SYM, LAYER_SYM2, LAYER_ABC, LAYER_EMOJI }
    enum class Layer { LETTERS, SYM1, SYM2, EMOJI }
    class Key(val label: String, val act: Act, val weight: Float = 1f)

    interface Listener {
        fun onChar(text: String)
        fun onAction(a: Act)
        fun onSuggestion(word: String)
    }

    var listener: Listener? = null
    var enterLabel = "↵"
    private var layer = Layer.LETTERS
    private var shifted = false
    private var caps = false
    private var suggestions: List<String> = emptyList()

    private var rows: List<List<Key>> = emptyList()
    private val rects = ArrayList<List<RectF>>()
    private var fr = 0      // focused row; -1 = suggestion strip
    private var fc = 0      // focused column
    private var pressed: Pair<Int, Int>? = null

    private val sd = context.resources.displayMetrics.scaledDensity
    private val stripH = context.dp(42)
    private val keyH = context.dp(46)
    private val gap = context.dp(5)
    private val pad = context.dp(5)

    private val bgPaint = Paint()
    private val linePaint = Paint().apply { color = Color.parseColor("#44FFFFFF"); strokeWidth = 1f }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
        setShadowLayer(2f, 0f, 1f, Color.BLACK)
    }

    private fun keyShape(top: Int, bottom: Int) =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
            cornerRadius = context.dpf(6f)
            setStroke(1, Color.parseColor("#80868E"))
        }

    private val keyNormal = keyShape(Color.parseColor("#5B5F66"), Color.parseColor("#1E2023"))
    private val keySpecial = keyShape(Color.parseColor("#3C3F44"), Color.parseColor("#141517"))
    private val keyFocus = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.parseColor("#4DA3F5"), Color.parseColor("#1668C6"))
    ).apply { cornerRadius = context.dpf(6f); setStroke(context.dp(2), Color.parseColor("#BFE0FF")) }
    private val gloss = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.parseColor("#55FFFFFF"), Color.parseColor("#05FFFFFF"))
    ).apply { cornerRadius = context.dpf(6f) }

    init { isFocusable = false; rebuild() }

    // ───────────── model ─────────────

    private fun cps(s: String): List<String> =
        s.codePoints().toArray().map { String(Character.toChars(it)) }

    private fun chars(s: String) = cps(s).map { Key(it, Act.CHAR) }

    private fun buildRows(): List<List<Key>> = when (layer) {
        Layer.LETTERS -> listOf(
            chars("qwertyuiop"),
            chars("asdfghjkl"),
            listOf(Key("⇧", Act.SHIFT, 1.5f)) + chars("zxcvbnm") + Key("⌫", Act.DEL, 1.5f),
            listOf(
                Key("?123", Act.LAYER_SYM, 1.5f), Key(",", Act.CHAR), Key("", Act.SPACE, 4f),
                Key(".", Act.CHAR), Key("☺", Act.LAYER_EMOJI), Key(enterLabel, Act.ENTER, 1.5f)
            )
        )
        Layer.SYM1 -> listOf(
            chars("1234567890"),
            chars("@#$%&-+()"),
            listOf(Key("1/2", Act.LAYER_SYM2, 1.5f)) + chars("*\"':;!?") + Key("⌫", Act.DEL, 1.5f),
            symBottom()
        )
        Layer.SYM2 -> listOf(
            chars("~`|•√π÷×¶∆"),
            chars("£¢€¥^°={}"),
            listOf(Key("2/2", Act.LAYER_SYM, 1.5f)) + chars("\\/<>[]_") + Key("⌫", Act.DEL, 1.5f),
            symBottom()
        )
        Layer.EMOJI -> listOf(
            chars("😀😁😂😅😊😍😘😎🤔😢"),
            chars("😭😡👍👎👏🙏💪🎉🔥❤"),
            chars("🎮🕹📱💻📷🎵🍕☕🚀⭐"),
            listOf(
                Key("ABC", Act.LAYER_ABC, 1.5f), Key("", Act.SPACE, 5.5f),
                Key("⌫", Act.DEL, 1.5f), Key(enterLabel, Act.ENTER, 1.5f)
            )
        )
    }

    private fun symBottom() = listOf(
        Key("ABC", Act.LAYER_ABC, 1.5f), Key(",", Act.CHAR), Key("", Act.SPACE, 5f),
        Key(".", Act.CHAR), Key(enterLabel, Act.ENTER, 1.5f)
    )

    private fun rebuild() {
        rows = buildRows()
        fr = fr.coerceIn(-1, rows.size - 1)
        fc = fc.coerceIn(0, maxOf(0, rowSize(fr) - 1))
        rects.clear()
        requestLayout()
        invalidate()
    }

    private fun rowSize(r: Int) = if (r < 0) suggestions.size else rows[r].size

    // ───────────── public API for the service ─────────────

    fun refresh() = rebuild()

    fun setLayer(l: Layer) {
        layer = l
        fr = rows.size.coerceAtLeast(4) - 1; fc = 0   // focus the layer key on the bottom row
        rebuild()
    }

    fun currentLayer() = layer

    fun cycleLayer(delta: Int) {
        val all = Layer.values()
        setLayer(all[(layer.ordinal + delta + all.size) % all.size])
    }

    fun setShift(s: Boolean, c: Boolean) { shifted = s; caps = c; invalidate() }

    fun setSuggestions(list: List<String>) {
        suggestions = list
        if (fr == -1 && list.isEmpty()) { fr = 0; fc = 0 }
        if (fr == -1) fc = fc.coerceAtMost(list.size - 1)
        invalidate()
    }

    fun move(dx: Int, dy: Int) {
        ensureRects()
        if (dy != 0) {
            val target = (fr + dy).coerceIn(-1, rows.size - 1)
            if (target == fr || (target == -1 && suggestions.isEmpty())) return
            val cx = centerX(fr, fc)
            var best = 0; var bd = Float.MAX_VALUE
            for (c in 0 until rowSize(target)) {
                val d = Math.abs(centerX(target, c) - cx)
                if (d < bd) { bd = d; best = c }
            }
            fr = target; fc = best
        } else {
            val n = rowSize(fr)
            if (n > 0) fc = (fc + dx + n) % n
        }
        invalidate()
    }

    fun pressFocused() {
        playSoundEffect(SoundEffectConstants.CLICK)
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        pressed = fr to fc
        postDelayed({ pressed = null; invalidate() }, 90)
        invalidate()
        if (fr < 0) {
            suggestions.getOrNull(fc)?.let { listener?.onSuggestion(it) }
            return
        }
        val k = rows[fr][fc]
        if (k.act == Act.CHAR) listener?.onChar(display(k)) else listener?.onAction(k.act)
    }

    // ───────────── geometry ─────────────

    private fun ensureRects() {
        if (rects.isNotEmpty() || width == 0) return
        val unit = (width - 2f * pad) / 10f
        rows.forEachIndexed { r, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            var x = pad + (10f - total) * unit / 2f
            val y = stripH + pad + r * (keyH + gap)
            val out = ArrayList<RectF>()
            row.forEach { k ->
                val w = k.weight * unit
                out.add(RectF(x + gap / 2f, y.toFloat(), x + w - gap / 2f, (y + keyH).toFloat()))
                x += w
            }
            rects.add(out)
        }
    }

    private fun centerX(r: Int, c: Int): Float {
        ensureRects()
        return if (r < 0) (c + 0.5f) * width / 3f else rects[r][c].centerX()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { rects.clear() }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        setMeasuredDimension(w, stripH + pad * 2 + rows.size * keyH + (rows.size - 1) * gap)
    }

    // ───────────── drawing ─────────────

    private fun display(k: Key): String =
        if (k.act == Act.CHAR && (shifted || caps) && k.label.length == 1 && k.label[0].isLetter()) k.label.uppercase() else k.label

    private fun centeredText(c: Canvas, text: String, r: RectF, size: Float, color: Int = Color.WHITE) {
        textPaint.textSize = size * sd
        textPaint.color = color
        val fm = textPaint.fontMetrics
        c.drawText(text, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, textPaint)
    }

    override fun onDraw(c: Canvas) {
        ensureRects()
        bgPaint.shader = android.graphics.LinearGradient(
            0f, 0f, 0f, height.toFloat(), Color.parseColor("#26292E"), Color.parseColor("#08090A"), android.graphics.Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        c.drawLine(0f, 0f, width.toFloat(), 0f, linePaint)

        // Suggestion strip
        val cell = width / 3f
        for (i in 0 until 3) {
            val r = RectF(i * cell + 3, 3f, (i + 1) * cell - 3, stripH - 3f)
            val word = suggestions.getOrNull(i)
            if (fr == -1 && fc == i && word != null) {
                keyFocus.setBounds(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()); keyFocus.draw(c)
            }
            if (word != null) centeredText(c, word, r, 17f)
            if (i > 0) c.drawLine(i * cell, 10f, i * cell, stripH - 10f, linePaint)
        }
        c.drawLine(0f, stripH.toFloat(), width.toFloat(), stripH.toFloat(), linePaint)

        // Keys
        rows.forEachIndexed { ri, row ->
            row.forEachIndexed { ci, k ->
                val r = rects[ri][ci]
                val focused = fr == ri && fc == ci
                val d = when {
                    focused -> keyFocus
                    k.act == Act.CHAR || k.act == Act.SPACE -> keyNormal
                    else -> keySpecial
                }
                d.setBounds(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
                d.draw(c)
                if (!focused) {
                    gloss.setBounds(r.left.toInt() + 1, r.top.toInt() + 1, r.right.toInt() - 1, (r.top + r.height() / 2f).toInt())
                    gloss.draw(c)
                }
                val isPressed = pressed == (ri to ci)
                val lit = k.act == Act.SHIFT && (shifted || caps)
                val color = if (lit && !focused) Theme.BLUE_LIGHT else Color.WHITE
                val label = display(k)
                val size = when {
                    k.act == Act.CHAR && k.label.codePointCount(0, k.label.length) == 1 && k.label[0].isLetterOrDigit() -> 22f
                    k.act == Act.CHAR -> 22f
                    k.act == Act.ENTER && label.length > 1 -> 13f
                    else -> 16f
                }
                if (label.isNotEmpty()) centeredText(c, label, r, size, color)
                if (k.act == Act.SHIFT && caps) c.drawLine(r.centerX() - 10, r.bottom - 8, r.centerX() + 10, r.bottom - 8, textPaint)
                if (isPressed) {
                    textPaint.color = Color.parseColor("#33FFFFFF")
                    c.drawRoundRect(r, 6f, 6f, textPaint.also { it.style = Paint.Style.FILL })
                    textPaint.color = Color.WHITE
                }
            }
        }
    }

    // ───────────── touch ─────────────

    private fun hit(x: Float, y: Float): Pair<Int, Int>? {
        ensureRects()
        if (y < stripH) {
            val i = (x / (width / 3f)).toInt()
            return if (i in suggestions.indices) -1 to i else null
        }
        rects.forEachIndexed { r, row -> row.forEachIndexed { c, rc -> if (rc.contains(x, y)) return r to c } }
        return null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> hit(e.x, e.y)?.let { fr = it.first; fc = it.second; invalidate() }
            MotionEvent.ACTION_UP -> {
                val h = hit(e.x, e.y)
                if (h != null) { fr = h.first; fc = h.second; pressFocused() }
            }
        }
        return true
    }
}
