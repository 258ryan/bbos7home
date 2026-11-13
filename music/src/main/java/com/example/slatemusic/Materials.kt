package com.example.slatemusic

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/**
 * Skeuomorphic materials, drawn at runtime (same look as the launcher): pebbled leather, brushed steel,
 * gel highlights, bevelled chrome, stitched edges, recessed wells.
 */
object Tex {
    private fun build(name: String, size: Int, px: (Int, Int, Random) -> Int): ImageBitmap {
        val rnd = Random(name.hashCode().toLong())
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val a = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) a[y * size + x] = px(x, y, rnd)
        b.setPixels(a, 0, size, 0, 0, size, size)
        return b.asImageBitmap()
    }

    val leather: ImageBitmap by lazy {
        val s = 96
        build("leather", s) { x, y, rnd ->
            val t = 2.0 * PI / s
            val grain = 7.0 * sin(t * 6 * x) * sin(t * 6 * y) + 5.0 * sin(t * (5 * x + 7 * y)) + 3.0 * sin(t * (11 * x - 9 * y))
            val v = (0x26 + grain + rnd.nextInt(12) - 6).toInt().coerceIn(0, 255)
            AColor.rgb(v, v, (v + 3).coerceAtMost(255))
        }
    }

    val brushed: ImageBitmap by lazy {
        val s = 64
        val rows = IntArray(s).also { r -> val rnd = Random(11); for (i in r.indices) r[i] = rnd.nextInt(22) - 11 }
        build("brushed", s) { _, y, rnd ->
            val v = (0x40 + rows[y] + rnd.nextInt(7) - 3).coerceIn(0, 255)
            AColor.rgb(v, v, (v + 2).coerceAtMost(255))
        }
    }
}

private fun DrawScope.tile(img: ImageBitmap, alpha: Float) {
    drawRect(brush = ShaderBrush(ImageShader(img, TileMode.Repeated, TileMode.Repeated)), alpha = alpha)
}

/** Pebbled leather, lit from above. */
fun Modifier.leather(alpha: Float = 1f): Modifier = drawBehind {
    tile(Tex.leather, alpha)
    drawRect(brush = Brush.verticalGradient(listOf(Color(0x22FFFFFF), Color(0x66000000))), alpha = alpha)
}

/** Brushed-steel band (folder strip). */
fun Modifier.metalBand(): Modifier = drawBehind {
    tile(Tex.brushed, 1f)
    drawRect(brush = Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x80000000))))
}

/** Navy glass bar: gradient, glossy upper half, light top edge, dark bottom edge. */
fun Modifier.glassBar(): Modifier = drawBehind {
    val d = 1.dp.toPx()
    drawRect(brush = Brush.verticalGradient(listOf(Color(0xF0224C8A), Color(0xF00B1A38))))
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0x30FFFFFF), Color(0x08FFFFFF)), endY = size.height / 2),
        size = Size(size.width, size.height / 2)
    )
    drawRect(color = Color(0x66FFFFFF), size = Size(size.width, d))
    drawRect(color = Color(0xAA000000), topLeft = Offset(0f, size.height - d), size = Size(size.width, d))
}

/** Dashed cream stitching inset from the edge. */
fun Modifier.stitched(inset: Dp = 4.dp): Modifier = drawBehind {
    val i = inset.toPx()
    drawRoundRect(
        color = Color(0xB0D8C9A0),
        topLeft = Offset(i, i),
        size = Size(size.width - 2 * i, size.height - 2 * i),
        cornerRadius = CornerRadius(3.dp.toPx()),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())))
    )
}

/** Light line on top, dark line underneath: a pressed seam between list rows. */
fun Modifier.bevelLines(): Modifier = drawBehind {
    val d = 1.dp.toPx()
    drawRect(color = Color(0x22FFFFFF), size = Size(size.width, d))
    drawRect(color = Color(0x99000000), topLeft = Offset(0f, size.height - d), size = Size(size.width, d))
}

/** Blue gel: gradient, glossy top half, dark outline, light inner edge, optional glow. [dim] = unfocused selection. */
fun Modifier.gel(radius: Dp, glow: Boolean = false, dim: Boolean = false, alpha: Float = 1f): Modifier = drawBehind {
    val r = radius.toPx()
    val d = 1.dp.toPx()
    val a = (if (dim) 0.45f else 1f) * alpha
    if (glow && alpha > 0.05f) for (i in 3 downTo 1) {
        val gi = i * d * 1.6f
        drawRoundRect(
            color = Color(0x1A78BEFF).copy(alpha = 0.1f * alpha), topLeft = Offset(-gi, -gi),
            size = Size(size.width + 2 * gi, size.height + 2 * gi), cornerRadius = CornerRadius(r + gi)
        )
    }
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF4DA3F5), Color(0xFF1668C6))),
        cornerRadius = CornerRadius(r), alpha = a
    )
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0x99FFFFFF), Color(0x22FFFFFF)), endY = size.height / 2),
        topLeft = Offset(2 * d, 2 * d), size = Size(size.width - 4 * d, size.height / 2 - 2 * d),
        cornerRadius = CornerRadius(r * 0.8f), alpha = a
    )
    drawRoundRect(
        color = Color(0x66FFFFFF), topLeft = Offset(1.5f * d, 1.5f * d),
        size = Size(size.width - 3 * d, size.height - 3 * d), cornerRadius = CornerRadius(r), style = Stroke(d), alpha = a
    )
    drawRoundRect(
        color = Color(0xFF0B3F7A), topLeft = Offset(0.5f * d, 0.5f * d),
        size = Size(size.width - d, size.height - d), cornerRadius = CornerRadius(r), style = Stroke(d), alpha = a
    )
}

/** Bevelled chrome (keycap / round button): soft shadow, steel gradient, specular top half, dark outline. */
fun Modifier.chrome(radius: Dp, dark: Boolean = false): Modifier = drawBehind {
    val r = radius.toPx()
    val d = 1.dp.toPx()
    val h = size.height - 2 * d
    for (i in 3 downTo 1) {
        drawRoundRect(
            color = Color(0x22000000), topLeft = Offset(0f, i * d * 0.9f),
            size = Size(size.width, h), cornerRadius = CornerRadius(r)
        )
    }
    val body = if (dark) Brush.verticalGradient(listOf(Color(0xFF42454A), Color(0xFF111214)), endY = h)
    else Brush.verticalGradient(listOf(Color(0xFF72767D), Color(0xFF383B3F), Color(0xFF17181A)), endY = h)
    drawRoundRect(brush = body, size = Size(size.width, h), cornerRadius = CornerRadius(r))
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0x70FFFFFF), Color(0x10FFFFFF)), endY = h * 0.48f),
        topLeft = Offset(1.5f * d, 1.5f * d), size = Size(size.width - 3 * d, h * 0.48f - 1.5f * d),
        cornerRadius = CornerRadius(r * 0.85f)
    )
    drawRoundRect(
        color = Color(0x40FFFFFF), topLeft = Offset(1.5f * d, 1.5f * d),
        size = Size(size.width - 3 * d, h - 3 * d), cornerRadius = CornerRadius(r), style = Stroke(d)
    )
    drawRoundRect(
        color = Color(0xFF08090A), topLeft = Offset(0.5f * d, 0.5f * d),
        size = Size(size.width - d, h - d), cornerRadius = CornerRadius(r), style = Stroke(d)
    )
}

/** Recessed well with a light lip along the bottom edge (text fields, slider tracks). */
fun Modifier.recessed(radius: Dp): Modifier = drawBehind {
    val r = radius.toPx()
    val d = 1.dp.toPx()
    drawRoundRect(color = Color(0x40FFFFFF), cornerRadius = CornerRadius(r))
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF040405), Color(0xFF1A1C1F))),
        size = Size(size.width, size.height - d), cornerRadius = CornerRadius(r)
    )
    drawRoundRect(
        color = Color(0xFF55585E), topLeft = Offset(0.5f * d, 0.5f * d),
        size = Size(size.width - d, size.height - 2 * d), cornerRadius = CornerRadius(r), style = Stroke(d)
    )
}

/** Soft layered drop shadow around a rounded box (drawn outside its bounds). */
fun Modifier.dropShadow(radius: Dp, spread: Dp = 8.dp, dy: Dp = 4.dp): Modifier = drawBehind {
    val r = radius.toPx()
    val sp = spread.toPx()
    val off = dy.toPx()
    for (i in 6 downTo 1) {
        val g = sp * i / 6f
        drawRoundRect(
            color = Color(0x1E000000), topLeft = Offset(-g, -g + off),
            size = Size(size.width + 2 * g, size.height + 2 * g), cornerRadius = CornerRadius(r + g)
        )
    }
}

/** Glossy metal ball (slider knob): shadow, radial shading, rim, specular highlight. */
fun Modifier.chromeKnob(): Modifier = drawBehind {
    val d = 1.dp.toPx()
    val r = size.minDimension / 2f - 2 * d
    val c = Offset(size.width / 2f, size.height / 2f - d)
    for (i in 3 downTo 1) drawCircle(color = Color(0x28000000), radius = r, center = Offset(c.x, c.y + i * d * 0.9f))
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFF2F4F7), Color(0xFF9CA2AA), Color(0xFF3A3D42), Color(0xFF111214)),
            center = Offset(c.x - r * 0.3f, c.y - r * 0.4f), radius = r * 1.6f
        ),
        radius = r, center = c
    )
    drawCircle(color = Color(0xFF08090A), radius = r, center = c, style = Stroke(d))
    drawCircle(color = Color(0x55FFFFFF), radius = r - 1.5f * d, center = c, style = Stroke(d))
    drawOval(
        brush = Brush.verticalGradient(listOf(Color(0xCCFFFFFF), Color(0x00FFFFFF)), startY = c.y - r, endY = c.y),
        topLeft = Offset(c.x - r * 0.55f, c.y - r * 0.9f), size = Size(r * 1.1f, r * 0.8f)
    )
}
