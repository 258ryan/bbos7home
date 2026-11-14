package com.example.slatelauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/**
 * The "skeuomorphic" layer: procedurally generated leather / brushed-metal textures, gel highlights,
 * bevelled chrome, stitched edges. No image assets; everything is drawn at runtime.
 */
object Materials {
    /** Turned off in settings for speed: bars fall back to flat gradients. */
    @Volatile var enabled = true

    private fun flat(top: Int, bottom: Int): Drawable = shade(top, bottom)

    private val cache = HashMap<String, Bitmap>()

    private fun make(name: String, size: Int, px: (Int, Int, Random) -> Int): Bitmap = cache.getOrPut(name) {
        val rnd = Random(name.hashCode().toLong())
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val a = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) a[y * size + x] = px(x, y, rnd)
        b.setPixels(a, 0, size, 0, 0, size, size)
        b
    }

    /** Dark pebbled leather. The sine terms use whole periods, so the tile is seamless. */
    fun leather(): Bitmap {
        val s = 96
        return make("leather", s) { x, y, rnd ->
            val t = 2.0 * PI / s
            val grain = 7.0 * sin(t * 6 * x) * sin(t * 6 * y) + 5.0 * sin(t * (5 * x + 7 * y)) + 3.0 * sin(t * (11 * x - 9 * y))
            val v = (0x26 + grain + rnd.nextInt(12) - 6).toInt().coerceIn(0, 255)
            Color.rgb(v, v, (v + 3).coerceAtMost(255))
        }
    }

    /** Green baize, like the original Game Center table. */
    fun felt(): Bitmap {
        val s = 96
        return make("felt", s) { x, y, rnd ->
            val t = 2.0 * PI / s
            val fibre = 4.0 * sin(t * 9 * x + 2.0 * sin(t * 4 * y)) * sin(t * 7 * y)
            val n = rnd.nextInt(16) - 8 + fibre.toInt()
            Color.rgb((0x1B + n / 2).coerceIn(0, 255), (0x5A + n).coerceIn(0, 255), (0x35 + n / 2).coerceIn(0, 255))
        }
    }

    /** Warm wood with horizontal grain (tiles seamlessly along x). */
    fun wood(): Bitmap {
        val w = 128; val h = 64
        val rows = DoubleArray(h).also { r -> val rnd = Random(5); for (i in r.indices) r[i] = rnd.nextDouble() }
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val a = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val ring = 0.5 + 0.5 * sin(2 * PI * (y * 2.0 / h + 0.15 * sin(2 * PI * x * 2.0 / w) + rows[y] * 0.25))
            val v = 0.62 + 0.28 * ring + (rows[(y * 7) % h] - 0.5) * 0.12
            a[y * w + x] = Color.rgb((0x6E * v).toInt().coerceIn(0, 255), (0x46 * v).toInt().coerceIn(0, 255), (0x24 * v).toInt().coerceIn(0, 255))
        }
        b.setPixels(a, 0, w, 0, 0, w, h)
        return cache.getOrPut("wood") { b }
    }

    /** Felt panel: baize with a soft vignette. */
    fun feltPanel(ctx: Context): Drawable {
        if (!enabled) return flat(0xFF1F6A3B.toInt(), 0xFF123F23.toInt())
        val vig = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = ctx.dpf(520f)
            setGradientCenter(0.5f, 0.35f)
            colors = intArrayOf(0x00000000, 0x99000000.toInt())
        }
        return LayerDrawable(arrayOf(tiled(ctx, felt()), vig))
    }

    /** Wood rail with a lit top edge and a shadowed lower edge. */
    fun woodRail(ctx: Context): Drawable {
        if (!enabled) return flat(0xFF7A4F2A.toInt(), 0xFF3E2512.toInt())
        return LayerDrawable(arrayOf(tiled(ctx, wood()), shade(0x40FFFFFF, 0x77000000)))
    }

    /** A soft drop-shadow strip to place under a bar. */
    fun shadowStrip(): Drawable = shade(0x66000000, 0x00000000)

    fun shadowStripView(ctx: Context): android.view.View = android.view.View(ctx).apply { background = shadowStrip() }

    /** Horizontally brushed steel. */
    fun brushed(): Bitmap {
        val s = 64
        val rows = IntArray(s).also { r -> val rnd = Random(11); for (i in r.indices) r[i] = rnd.nextInt(22) - 11 }
        return make("brushed", s) { _, y, rnd ->
            val v = (0x40 + rows[y] + rnd.nextInt(7) - 3).coerceIn(0, 255)
            Color.rgb(v, v, (v + 2).coerceAtMost(255))
        }
    }

    /** Tiles [bmp] over whatever size the view has. No intrinsic size, so it never forces a view taller. */
    @Suppress("UNUSED_PARAMETER")
    private fun tiled(ctx: Context, bmp: Bitmap): Drawable = TileDrawable(bmp)

    private fun shade(top: Int, bottom: Int) =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))

    /** Leather, lit from above. */
    fun leatherTiled(ctx: Context): Drawable = if (!enabled) flat(0xFF2E3034.toInt(), 0xFF141517.toInt()) else
        LayerDrawable(arrayOf(tiled(ctx, leather()), shade(0x22FFFFFF, 0x66000000)))

    /** Translucent leather used as the dimmer behind expanded panels. */
    fun dimLeather(ctx: Context, alpha: Int = 232): Drawable =
        if (!enabled) android.graphics.drawable.ColorDrawable(Color.argb(alpha, 8, 9, 10))
        else tiled(ctx, leather()).apply { this.alpha = alpha }

    /** Brushed-metal band (folder strip). */
    fun metalBand(ctx: Context): Drawable = if (!enabled) flat(0xFF4A4D52.toInt(), 0xFF1B1C1E.toInt()) else
        LayerDrawable(arrayOf(tiled(ctx, brushed()), shade(0x40FFFFFF, 0x80000000.toInt())))

    /** Leather with a dashed cream stitch line inset from the edge. */
    fun stitchedLeather(ctx: Context, insetDp: Int = 4): Drawable {
        val stitch = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = ctx.dpf(3f)
            setStroke(ctx.dp(1), Color.parseColor("#B0D8C9A0"), ctx.dpf(5f), ctx.dpf(3f))
        }
        return LayerDrawable(arrayOf(leatherTiled(ctx), InsetDrawable(stitch, ctx.dp(insetDp))))
    }

    /** Recessed (inset) field: dark well with a light lip along the bottom edge. */
    fun recessed(ctx: Context, radiusDp: Float = 4f): Drawable {
        val lip = GradientDrawable().apply { setColor(0x40FFFFFF); cornerRadius = ctx.dpf(radiusDp) }
        val well = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF040405.toInt(), 0xFF1A1C1F.toInt())).apply {
            cornerRadius = ctx.dpf(radiusDp)
            setStroke(ctx.dp(1), Color.parseColor("#55585E"))
        }
        return LayerDrawable(arrayOf(lip, well)).also { it.setLayerInset(1, 0, 0, 0, maxOf(1, ctx.dp(1))) }
    }
}

/**
 * Repeats a bitmap across the drawable's bounds. It deliberately reports no intrinsic size: a BitmapDrawable would make every
 * view that uses it as a background at least as tall as the bitmap (64 / 96 px), which is what blew up the bars.
 */
class TileDrawable(bmp: Bitmap) : MatDrawable() {
    init { p.shader = android.graphics.BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
    override fun draw(c: Canvas) { c.drawRect(bounds, p) }
    override fun getIntrinsicWidth() = -1
    override fun getIntrinsicHeight() = -1
}

abstract class MatDrawable : Drawable() {
    protected val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    protected fun gradient(r: RectF, top: Int, bottom: Int): Shader =
        LinearGradient(r.left, r.top, r.left, r.bottom, top, bottom, Shader.TileMode.CLAMP)
}

/** Blue "gel" button: gradient body, glossy top half, dark outline, light inner edge, optional glow. */
class GelDrawable(
    ctx: Context, private val top: Int, private val bottom: Int, radiusDp: Float, private val glow: Boolean = false
) : MatDrawable() {
    private val d = ctx.dpf(1f)
    private val r = ctx.dpf(radiusDp)
    private val rect = RectF()
    private val g = RectF()

    override fun draw(c: Canvas) {
        rect.set(bounds)
        if (glow) {
            p.shader = null; p.style = Paint.Style.FILL
            for (i in 3 downTo 1) {
                p.color = Color.argb(26, 120, 190, 255)
                val gi = i * d * 1.6f
                c.drawRoundRect(rect.left - gi, rect.top - gi, rect.right + gi, rect.bottom + gi, r + gi, r + gi, p)
            }
        }
        p.style = Paint.Style.FILL
        p.shader = gradient(rect, top, bottom)
        c.drawRoundRect(rect, r, r, p)
        g.set(rect.left + 2 * d, rect.top + 2 * d, rect.right - 2 * d, rect.top + rect.height() * 0.5f)
        p.shader = gradient(g, 0x99FFFFFF.toInt(), 0x22FFFFFF)
        c.drawRoundRect(g, r * 0.8f, r * 0.8f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = d
        p.color = 0x66FFFFFF
        c.drawRoundRect(rect.left + 1.5f * d, rect.top + 1.5f * d, rect.right - 1.5f * d, rect.bottom - 1.5f * d, r, r, p)
        p.color = 0xFF0B3F7A.toInt()
        c.drawRoundRect(rect.left + 0.5f * d, rect.top + 0.5f * d, rect.right - 0.5f * d, rect.bottom - 0.5f * d, r, r, p)
        p.style = Paint.Style.FILL
    }
}

/** Bevelled chrome plate / keycap: soft drop shadow, steel gradient, specular top half, dark outline. */
class ChromeDrawable(ctx: Context, radiusDp: Float, private val dark: Boolean = false) : MatDrawable() {
    /** Off while a key is pressed down, so it sits flat on the board. */
    var shadowOn = true
    private val d = ctx.dpf(1f)
    private val r = ctx.dpf(radiusDp)
    private val rect = RectF()
    private val g = RectF()

    override fun draw(c: Canvas) {
        val b = bounds
        val sh = 2 * d
        rect.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom - sh)
        p.shader = null; p.style = Paint.Style.FILL
        // soft drop shadow: three offset, fading copies
        if (shadowOn) for (i in 3 downTo 1) {
            p.color = Color.argb(34, 0, 0, 0)
            c.drawRoundRect(rect.left, rect.top + i * d * 0.9f, rect.right, rect.bottom + i * d * 0.9f, r, r, p)
        }
        p.shader = if (dark) gradient(rect, 0xFF42454A.toInt(), 0xFF111214.toInt())
        else LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            intArrayOf(0xFF72767D.toInt(), 0xFF383B3F.toInt(), 0xFF17181A.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, r, r, p)
        g.set(rect.left + 1.5f * d, rect.top + 1.5f * d, rect.right - 1.5f * d, rect.top + rect.height() * 0.48f)
        p.shader = gradient(g, 0x70FFFFFF, 0x10FFFFFF)
        c.drawRoundRect(g, r * 0.85f, r * 0.85f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = d
        p.color = 0x40FFFFFF
        c.drawRoundRect(rect.left + 1.5f * d, rect.top + 1.5f * d, rect.right - 1.5f * d, rect.bottom - 1.5f * d, r, r, p)
        p.color = 0xFF08090A.toInt()
        c.drawRoundRect(rect.left + 0.5f * d, rect.top + 0.5f * d, rect.right - 0.5f * d, rect.bottom - 0.5f * d, r, r, p)
        p.style = Paint.Style.FILL
    }
}

/** Navy glass bar: gradient, glossy upper half, light top edge and dark bottom edge. */
class GlassBarDrawable(ctx: Context) : MatDrawable() {
    private val d = ctx.dpf(1f)
    private val rect = RectF()

    override fun draw(c: Canvas) {
        rect.set(bounds)
        p.style = Paint.Style.FILL
        p.shader = gradient(rect, 0xF0224C8A.toInt(), 0xF00B1A38.toInt())
        c.drawRect(rect, p)
        p.shader = gradient(RectF(rect.left, rect.top, rect.right, rect.centerY()), 0x30FFFFFF, 0x08FFFFFF)
        c.drawRect(rect.left, rect.top, rect.right, rect.centerY(), p)
        p.shader = null
        p.color = 0x66FFFFFF
        c.drawRect(rect.left, rect.top, rect.right, rect.top + d, p)
        p.color = 0xAA000000.toInt()
        c.drawRect(rect.left, rect.bottom - d, rect.right, rect.bottom, p)
    }
}

/** A game case standing on the felt: soft shadow, dark plastic body, bevelled spine on the left. */
class CaseDrawable(ctx: Context) : MatDrawable() {
    private val d = ctx.dpf(1f)
    private val body = RectF()
    private val spine = RectF()

    override fun draw(c: Canvas) {
        val b = bounds
        body.set(b.left + 2 * d, b.top + 2 * d, b.right - 2 * d, b.bottom - 6 * d)
        p.shader = null; p.style = Paint.Style.FILL
        for (i in 5 downTo 1) {
            p.color = Color.argb(34, 0, 0, 0)
            c.drawRoundRect(body.left - i * d * 0.6f, body.top + i * d * 1.1f, body.right + i * d * 0.6f, body.bottom + i * d * 1.3f, 5 * d, 5 * d, p)
        }
        p.shader = gradient(body, 0xFF3A3D42.toInt(), 0xFF0B0C0D.toInt())
        c.drawRoundRect(body, 3 * d, 3 * d, p)
        spine.set(body.left, body.top, body.left + 8 * d, body.bottom)
        p.shader = LinearGradient(spine.left, 0f, spine.right, 0f, 0xFF62666D.toInt(), 0xFF141618.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(spine, 3 * d, 3 * d, p)
        p.shader = null
        p.color = 0x66FFFFFF
        c.drawRect(spine.left + d, spine.top + 3 * d, spine.left + 1.8f * d, spine.bottom - 3 * d, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = d
        p.color = 0x33FFFFFF
        c.drawRoundRect(body.left + 1.5f * d, body.top + 1.5f * d, body.right - 1.5f * d, body.bottom - 1.5f * d, 3 * d, 3 * d, p)
        p.color = 0xFF050506.toInt()
        c.drawRoundRect(body.left + 0.5f * d, body.top + 0.5f * d, body.right - 0.5f * d, body.bottom - 0.5f * d, 3 * d, 3 * d, p)
        p.style = Paint.Style.FILL
    }
}
