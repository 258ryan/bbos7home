package ai.heretek.pwnapple.graphics

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a 3D beveled skeuomorphic border with an outer stroke,
 * top inner specular highlight line, and bottom inner shadow.
 */
fun Modifier.skeuoBevel(
    cornerRadius: Dp = 4.dp,
    borderColor: Color,
    borderWidth: Dp = 1.dp,
    highlightColor: Color = Color.White.copy(alpha = 0.35f),
    shadowColor: Color = Color.Black.copy(alpha = 0.25f)
): Modifier = this.drawWithContent {
    drawContent()
    val width = size.width
    val height = size.height
    val rPx = cornerRadius.toPx()
    val bWidthPx = borderWidth.toPx()

    // Outer border stroke
    drawRoundRect(
        color = borderColor,
        topLeft = Offset(bWidthPx / 2f, bWidthPx / 2f),
        size = Size(width - bWidthPx, height - bWidthPx),
        cornerRadius = CornerRadius(rPx, rPx),
        style = Stroke(width = bWidthPx)
    )

    // Inner top highlight line
    drawLine(
        color = highlightColor,
        start = Offset(rPx, bWidthPx + 0.5f),
        end = Offset(width - rPx, bWidthPx + 0.5f),
        strokeWidth = 1f
    )

    // Inner bottom shadow line
    drawLine(
        color = shadowColor,
        start = Offset(rPx, height - bWidthPx - 0.5f),
        end = Offset(width - rPx, height - bWidthPx - 0.5f),
        strokeWidth = 1f
    )
}
