package ai.heretek.pwnapple.components.indicator

import ai.heretek.pwnapple.graphics.glossHighlight
import ai.heretek.pwnapple.graphics.innerShadow
import ai.heretek.pwnapple.theme.PwnappleTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Skeuomorphic iOS 6 progress bar with glossy cyan-blue progress and recessed track.
 */
@Composable
fun IosProgressBar(
    progress: Float, // 0f..1f
    modifier: Modifier = Modifier
) {
    val colors = PwnappleTheme.colors
    val clamped = progress.coerceIn(0f, 1f)
    val shape = remember { RoundedCornerShape(5.dp) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFC0C0C0), Color(0xFFE2E2E2))
                )
            )
            .border(0.75.dp, Color(0xFF9E9E9E), shape)
            .innerShadow(cornerRadius = 5.dp, shadowColor = Color(0x66000000), shadowHeight = 2.dp)
    ) {
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(clamped)
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(colors.switchOnTop, colors.switchOnBottom)
                        )
                    )
                    .glossHighlight(cornerRadius = 5.dp, glossAlpha = 0.35f)
            )
        }
    }
}
