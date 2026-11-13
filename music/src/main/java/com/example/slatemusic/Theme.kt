package com.example.slatemusic

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/** Same palette as Slate Home (the launcher): charcoal chrome, navy banner, solid blue selection. */
object Slate {
    val Text = Color.White
    val Dim = Color(0xFF9AA0A8)
    val BlueLight = Color(0xFF8FCBFF)
    val Blue = Color(0xFF2F86DB)
    val ChromeHi = Color(0xFF5B5F66)
    val ChromeMid = Color(0xFF2A2C30)
    val ChromeLo = Color(0xFF121315)
    val Hairline = Color(0x44FFFFFF)
    val Banner = Brush.verticalGradient(listOf(Color(0xF0183663), Color(0xF00B1A38)))
    val Wallpaper = Brush.verticalGradient(listOf(Color(0xFF3A3E45), Color(0xFF17191D), Color(0xFF050506)))
    val Select = Brush.verticalGradient(listOf(Color(0xFF3C9AF0), Color(0xFF1668C6)))
    val SelectDim = Brush.verticalGradient(listOf(Color(0x663C9AF0), Color(0x661668C6)))
    val Chrome = Brush.verticalGradient(listOf(ChromeHi, ChromeMid, ChromeLo))
}

@Composable
fun Txt(
    text: String, size: Int, color: Color = Slate.Text, bold: Boolean = false, light: Boolean = false,
    modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start, maxLines: Int = 1
) {
    Text(
        text = text, modifier = modifier, textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = color, fontSize = size.sp,
            fontWeight = if (bold) FontWeight.Bold else if (light) FontWeight.Light else FontWeight.Normal,
            shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 3f)
        )
    )
}

/** One of the BB OS 7 theme icons bundled in assets/bb (same ones Slate Home uses). */
@Composable
fun BbIcon(name: String, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp = remember(name) {
        try { ctx.assets.open("bb/$name.png").use { BitmapFactory.decodeStream(it) }?.asImageBitmap() } catch (_: Exception) { null }
    }
    if (bmp != null) Image(bitmap = bmp, contentDescription = null, modifier = modifier.size(size))
}
