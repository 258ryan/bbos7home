package com.example.slatelauncher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.sqrt

/** Built-in wallpapers, generated at the screen's size. */
object Wallpapers {
    class Item(val id: String, val title: String)

    val items = listOf(
        Item("hex", "Hex mesh"), Item("carbon", "Carbon fibre"), Item("leather", "Leather"),
        Item("steel", "Brushed steel"), Item("navy", "Navy glass"), Item("charcoal", "Charcoal")
    )

    private fun vignette(c: Canvas, w: Int, h: Int, alpha: Int = 150) {
        val p = Paint()
        p.shader = RadialGradient(w * 0.4f, h * 0.35f, maxOf(w, h) * 0.8f, Color.TRANSPARENT, Color.argb(alpha, 0, 0, 0), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }

    private fun tiledFill(c: Canvas, tile: Bitmap, w: Int, h: Int) {
        val p = Paint()
        p.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }

    fun render(id: String, w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (id) {
            "charcoal" -> {
                p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF3A3E45.toInt(), 0xFF17191D.toInt(), 0xFF050506.toInt()), null, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            }
            "navy" -> {
                p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF2A5CA8.toInt(), 0xFF0E2447.toInt(), 0xFF040810.toInt()), null, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                p.shader = LinearGradient(0f, 0f, 0f, h * 0.45f, 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w.toFloat(), h * 0.45f, p)
            }
            "leather" -> { tiledFill(c, Materials.leather(), w, h); vignette(c, w, h, 170) }
            "steel" -> { tiledFill(c, Materials.brushed(), w, h); vignette(c, w, h, 150) }
            "carbon" -> {
                val t = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
                for (y in 0 until 8) for (x in 0 until 8) {
                    val light = ((x / 2 + y / 2) % 2 == 0)
                    val v = (if (light) 0x2A else 0x12) + (if ((x + y) % 2 == 0) 4 else 0)
                    t.setPixel(x, y, Color.rgb(v, v, v + 2))
                }
                val big = Bitmap.createScaledBitmap(t, 24, 24, false)
                tiledFill(c, big, w, h); vignette(c, w, h, 160)
            }
            else -> { // hex mesh, like the OS 7 "dark mesh" wallpapers
                c.drawColor(0xFF101215.toInt())
                val r = maxOf(w, h) / 34f
                val hh = r * sqrt(3f) / 2f
                val path = Path()
                p.style = Paint.Style.STROKE
                var row = 0
                var y = 0f
                while (y < h + hh) {
                    var x = if (row % 2 == 0) 0f else r * 1.5f
                    while (x < w + r) {
                        path.reset()
                        for (i in 0 until 6) {
                            val a = Math.PI / 3 * i
                            val px = x + r * 0.92f * Math.cos(a).toFloat(); val py = y + r * 0.92f * Math.sin(a).toFloat()
                            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        path.close()
                        p.strokeWidth = 1.2f; p.color = 0xFF2C3036.toInt(); c.drawPath(path, p)
                        p.strokeWidth = 0.8f; p.color = 0x22FFFFFF; c.save(); c.translate(-0.8f, -0.8f); c.drawPath(path, p); c.restore()
                        x += r * 3f
                    }
                    y += hh; row++
                }
                vignette(c, w, h, 190)
            }
        }
        return b
    }

    fun screen(ctx: Context): Pair<Int, Int> {
        val dm = ctx.resources.displayMetrics
        val wm = WallpaperManager.getInstance(ctx)
        return maxOf(dm.widthPixels, wm.desiredMinimumWidth) to maxOf(dm.heightPixels, wm.desiredMinimumHeight)
    }

    fun apply(ctx: Context, id: String): Boolean = try {
        val (w, h) = screen(ctx)
        WallpaperManager.getInstance(ctx).setBitmap(render(id, w, h))
        true
    } catch (_: Exception) { false }
}
