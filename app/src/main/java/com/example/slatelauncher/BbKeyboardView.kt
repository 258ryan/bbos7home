package com.example.slatelauncher

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.view.animation.OvershootInterpolator
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

    private val keyNormal = ChromeDrawable(context, 6f)
    private val keySpecial = ChromeDrawable(context, 6f, dark = true)
    private val keyFocus = GelDrawable(context, Color.parseColor("#4DA3F5"), Color.parseColor("#1668C6"), 6f, glow = true)
    private val gloss = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)   // gloss is part of the key drawables now
    private val metalPaint = Paint().apply {
        shader = android.graphics.BitmapShader(Materials.brushed(), android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
        alpha = 150
    }

    // ── motion: the highlighted key lifts with a little overshoot, a pressed key sinks and springs back ──
    private var liftValue = 1f
    private var pressDepth = 0f
    private val liftAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 140; interpolator = OvershootInterpolator(2.4f)
        addUpdateListener { liftValue = it.animatedValue as Float; invalidate() }
    }
    private val pressAnim = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
        duration = 200
        addUpdateListener { pressDepth = it.animatedValue as Float; invalidate() }
    }
    private val ledPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun animOn() = Anim.level(context) > 0

    private fun lift() {
        if (animOn()) { liftAnim.cancel(); liftValue = 0f; liftAnim.start() } else liftValue = 1f
    }

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
        lift()
        invalidate()
    }

    fun pressFocused() {
        playSoundEffect(SoundEffectConstants.CLICK)
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        pressed = fr to fc
        if (animOn()) { pressAnim.cancel(); pressAnim.start() } else pressDepth = 1f
        postDelayed({ pressed = null; pressDepth = 0f; invalidate() }, if (animOn()) 210L else 90L)
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
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), metalPaint)
        // housing: lit top edge, side bezels, shaded bottom
        val dn = context.resources.displayMetrics.density
        bgPaint.shader = null
        bgPaint.color = 0x88FFFFFF.toInt(); c.drawRect(0f, 0f, width.toFloat(), dn, bgPaint)
        bgPaint.color = 0x99000000.toInt(); c.drawRect(0f, dn, width.toFloat(), 2 * dn, bgPaint)
        bgPaint.color = 0x33FFFFFF; c.drawRect(0f, 0f, dn, height.toFloat(), bgPaint)
        bgPaint.color = 0x66000000; c.drawRect(width - dn, 0f, width.toFloat(), height.toFloat(), bgPaint)
        bgPaint.shader = android.graphics.LinearGradient(0f, height - 10 * dn, 0f, height.toFloat(), 0x00000000, 0x77000000, android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, height - 10 * dn, width.toFloat(), height.toFloat(), bgPaint)
        bgPaint.shader = null

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

        // inner shadow under the suggestion strip
        bgPaint.shader = android.graphics.LinearGradient(0f, stripH.toFloat(), 0f, stripH + 7 * dn, 0x66000000, 0x00000000, android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, stripH.toFloat(), width.toFloat(), stripH + 7 * dn, bgPaint)
        bgPaint.shader = null

        // Keys: resting keys first, the highlighted one last so its lift draws over its neighbours
        rows.forEachIndexed { ri, row -> row.forEachIndexed { ci, k -> if (!(fr == ri && fc == ci)) drawKey(c, ri, ci, k, dn) } }
        if (fr >= 0 && fr < rows.size && fc < rows[fr].size) drawKey(c, fr, fc, rows[fr][fc], dn)
    }

    private fun drawKey(c: Canvas, ri: Int, ci: Int, k: Key, dn: Float) {
        val r = rects[ri][ci]
        val focused = fr == ri && fc == ci
        val depth = if (pressed == (ri to ci)) pressDepth else 0f
        val lift = if (focused) liftValue else 0f
        c.save()
        val sc = 1f + 0.10f * lift - 0.06f * depth
        c.scale(sc, sc, r.centerX(), r.centerY())
        c.translate(0f, depth * dn * 1.5f - lift * dn * 1.5f)
        val d = when {
            focused -> keyFocus
            k.act == Act.CHAR || k.act == Act.SPACE -> keyNormal
            else -> keySpecial
        }
        (d as? ChromeDrawable)?.shadowOn = depth < 0.4f
        d.setBounds(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
        d.draw(c)
        val lit = k.act == Act.SHIFT && (shifted || caps)
        val color = if (lit && !focused) Theme.BLUE_LIGHT else Color.WHITE
        val label = display(k)
        val size = when {
            k.act == Act.CHAR -> 22f
            k.act == Act.ENTER && label.length > 1 -> 13f
            else -> 16f
        }
        if (label.isNotEmpty()) centeredText(c, label, r, size, color)
        if (k.act == Act.SHIFT) drawLed(c, r, dn, shifted || caps, caps)
        c.restore()
    }

    /** Small status LED in the corner of the shift key: off, lit (shift) or lit with a ring (caps lock). */
    private fun drawLed(c: Canvas, r: RectF, dn: Float, on: Boolean, caps: Boolean) {
        val cx = r.right - 9 * dn
        val cy = r.top + 9 * dn
        ledPaint.shader = null
        ledPaint.style = Paint.Style.FILL
        ledPaint.color = 0xFF050607.toInt()
        c.drawCircle(cx, cy, 4.2f * dn, ledPaint)
        if (on) {
            ledPaint.shader = android.graphics.RadialGradient(
                cx, cy, (if (caps) 9f else 7f) * dn, intArrayOf(0xFFE8F6FF.toInt(), 0xFF3C9AF0.toInt(), 0x003C9AF0), floatArrayOf(0f, 0.45f, 1f), android.graphics.Shader.TileMode.CLAMP
            )
            c.drawCircle(cx, cy, (if (caps) 9f else 7f) * dn, ledPaint)
            ledPaint.shader = null
        } else {
            ledPaint.color = 0xFF1B2A38.toInt()
            c.drawCircle(cx, cy, 2.6f * dn, ledPaint)
        }
        ledPaint.style = Paint.Style.STROKE
        ledPaint.strokeWidth = dn
        ledPaint.color = if (caps) 0xFFBFE0FF.toInt() else 0x66FFFFFF
        c.drawCircle(cx, cy, 4.2f * dn, ledPaint)
        ledPaint.style = Paint.Style.FILL
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
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> hit(e.x, e.y)?.let {
                if (fr != it.first || fc != it.second) { fr = it.first; fc = it.second; lift() }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val h = hit(e.x, e.y)
                if (h != null) { fr = h.first; fc = h.second; pressFocused() }
            }
        }
        return true
    }
}
