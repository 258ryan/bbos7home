package ai.heretek.pwnapple.components.tabs

import ai.heretek.pwnapple.graphics.glossHighlight
import ai.heretek.pwnapple.theme.PwnappleTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Authentic glossy red notification badge with white rim and drop shadow.
 */
@Composable
fun IosBadge(
    count: String,
    modifier: Modifier = Modifier
) {
    val typography = PwnappleTheme.typography
    val shape = remember { RoundedCornerShape(10.dp) }

    Box(
        modifier = modifier
            .drawBehind {
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.45f),
                    topLeft = Offset(0f, 1.5f),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx())
                )
            }
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFEE2D2D),
                        Color(0xFFBA0F0F)
                    )
                )
            )
            .border(1.5.dp, Color.White, shape)
            .glossHighlight(cornerRadius = 10.dp, glossAlpha = 0.5f)
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count,
            style = typography.badgeText
        )
    }
}
