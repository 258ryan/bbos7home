package ai.heretek.pwnapple.graphics

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Shape producing the iconic iOS 6 chevron back-button outline:
 * a pointed left arrow head merging seamlessly into a rounded rectangle.
 */
class ArrowBackShape(
    private val arrowWidth: Dp = 14.dp,
    private val cornerRadius: Dp = 4.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val w = size.width
        val h = size.height
        val aw = with(density) { arrowWidth.toPx() }.coerceAtMost(w * 0.45f)
        val r = with(density) { cornerRadius.toPx() }.coerceAtMost(h / 2f)

        // Start at left tip midpoint
        path.moveTo(0f, h / 2f)
        // Slant up to top edge
        path.lineTo(aw, 0f)
        // Horizontal top edge
        path.lineTo(w - r, 0f)
        // Top right corner arc
        path.quadraticTo(w, 0f, w, r)
        // Right edge
        path.lineTo(w, h - r)
        // Bottom right corner arc
        path.quadraticTo(w, h, w - r, h)
        // Horizontal bottom edge
        path.lineTo(aw, h)
        // Slant back down-left to tip midpoint
        path.lineTo(0f, h / 2f)
        path.close()

        return Outline.Generic(path)
    }
}
