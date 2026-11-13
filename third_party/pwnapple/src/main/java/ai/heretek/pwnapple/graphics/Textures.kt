package ai.heretek.pwnapple.graphics

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws the classic vertical alternating pinstripe pattern from iOS 6 Settings.app.
 */
fun Modifier.pinstripeBackground(
    baseColor: Color = Color(0xFFC5CCD4),
    stripeColor: Color = Color(0xFFCBD2D8),
    stripeWidth: Dp = 2.dp
): Modifier = this.drawBehind {
    drawRect(color = baseColor)
    val width = size.width
    val height = size.height
    val sWidthPx = stripeWidth.toPx()
    val period = sWidthPx * 2

    var x = 0f
    while (x < width) {
        drawLine(
            color = stripeColor,
            start = Offset(x + (sWidthPx / 2f), 0f),
            end = Offset(x + (sWidthPx / 2f), height),
            strokeWidth = sWidthPx
        )
        x += period
    }
}

/**
 * Draws the dark woven linen texture seen in the iOS 6 notification center,
 * multitasking bar, and folder backgrounds.
 */
fun Modifier.darkLinenBackground(
    baseColor: Color = Color(0xFF23272B),
    crossColor: Color = Color(0xFF2C3238),
    gridStep: Dp = 4.dp
): Modifier = this.drawBehind {
    drawRect(color = baseColor)
    val width = size.width
    val height = size.height
    val stepPx = gridStep.toPx()

    // Diagonal weave lines
    var offset = -height
    while (offset < width) {
        drawLine(
            color = crossColor.copy(alpha = 0.5f),
            start = Offset(offset, 0f),
            end = Offset(offset + height, height),
            strokeWidth = 1f
        )
        drawLine(
            color = crossColor.copy(alpha = 0.5f),
            start = Offset(offset + height, 0f),
            end = Offset(offset, height),
            strokeWidth = 1f
        )
        offset += stepPx
    }
}
