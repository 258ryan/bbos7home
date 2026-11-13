package ai.heretek.pwnapple.graphics

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws the iconic iOS 6 curved specular reflection over the top half of a component.
 */
fun Modifier.glossHighlight(
    cornerRadius: Dp = 4.dp,
    glossAlpha: Float = 0.45f
): Modifier = this.drawWithContent {
    drawContent()
    val width = size.width
    val height = size.height
    val rPx = cornerRadius.toPx()

    val path = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = 0f,
                top = 0f,
                right = width,
                bottom = height,
                topLeftCornerRadius = CornerRadius(rPx, rPx),
                topRightCornerRadius = CornerRadius(rPx, rPx),
                bottomLeftCornerRadius = CornerRadius(rPx, rPx),
                bottomRightCornerRadius = CornerRadius(rPx, rPx)
            )
        )
    }

    clipPath(path) {
        // Linear top gloss layer (top 50%)
        val glossHeight = height * 0.50f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = glossAlpha),
                    Color.White.copy(alpha = glossAlpha * 0.15f)
                ),
                startY = 0f,
                endY = glossHeight
            ),
            topLeft = Offset.Zero,
            size = Size(width, glossHeight)
        )

        // Specular highlight hairline on very top rim
        drawLine(
            color = Color.White.copy(alpha = glossAlpha * 0.9f),
            start = Offset(rPx, 1f),
            end = Offset(width - rPx, 1f),
            strokeWidth = 1.5f
        )
    }
}

/**
 * Renders an inner recessed shadow for inset controls (text fields, switch grooves, pressed buttons).
 */
fun Modifier.innerShadow(
    cornerRadius: Dp = 4.dp,
    shadowColor: Color = Color(0x55000000),
    shadowHeight: Dp = 4.dp
): Modifier = this.drawWithContent {
    drawContent()
    val width = size.width
    val height = size.height
    val rPx = cornerRadius.toPx()
    val sHeight = shadowHeight.toPx()

    val path = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = 0f,
                top = 0f,
                right = width,
                bottom = height,
                topLeftCornerRadius = CornerRadius(rPx, rPx),
                topRightCornerRadius = CornerRadius(rPx, rPx),
                bottomLeftCornerRadius = CornerRadius(rPx, rPx),
                bottomRightCornerRadius = CornerRadius(rPx, rPx)
            )
        )
    }

    clipPath(path) {
        // Inset shadow gradient on top edge
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(shadowColor, Color.Transparent),
                startY = 0f,
                endY = sHeight
            ),
            topLeft = Offset.Zero,
            size = Size(width, sHeight)
        )
    }
}
