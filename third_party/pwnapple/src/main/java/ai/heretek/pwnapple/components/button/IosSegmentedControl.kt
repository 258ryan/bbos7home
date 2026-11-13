package ai.heretek.pwnapple.components.button

import ai.heretek.pwnapple.graphics.glossHighlight
import ai.heretek.pwnapple.graphics.innerShadow
import ai.heretek.pwnapple.theme.PwnappleTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Connected segmented control with active/inactive skeuomorphic gradient styling.
 */
@Composable
fun IosSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 30.dp
) {
    val sound = PwnappleTheme.sound
    val outerShape = remember { RoundedCornerShape(4.dp) }

    Row(
        modifier = modifier
            .height(height)
            .clip(outerShape)
            .border(1.dp, Color(0xFF6B7E96), outerShape)
    ) {
        items.forEachIndexed { index, title ->
            val isSelected = index == selectedIndex

            val segmentShape = when {
                items.size == 1 -> outerShape
                index == 0 -> RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                index == items.lastIndex -> RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                else -> RoundedCornerShape(0.dp)
            }

            val brush = if (isSelected) {
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF33537E),
                        Color(0xFF4C73A6),
                        Color(0xFF5B84B8)
                    )
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFBFBFB),
                        Color(0xFFE2E2E2),
                        Color(0xFFD0D0D0)
                    )
                )
            }

            val textStyle = if (isSelected) {
                TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.6f),
                        offset = Offset(0f, -1.5f),
                        blurRadius = 0f
                    )
                )
            } else {
                TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF333333),
                    shadow = Shadow(
                        color = Color.White.copy(alpha = 0.8f),
                        offset = Offset(0f, 1.5f),
                        blurRadius = 0f
                    )
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(segmentShape)
                    .background(brush)
                    .then(
                        if (isSelected) {
                            Modifier.innerShadow(cornerRadius = 0.dp, shadowColor = Color(0x66000000), shadowHeight = 3.dp)
                        } else {
                            Modifier.glossHighlight(cornerRadius = 0.dp, glossAlpha = 0.35f)
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (index != selectedIndex) {
                            sound.playButtonClick()
                            onItemSelected(index)
                        }
                    }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = textStyle
                )
            }

            // Divider line between segments
            if (index < items.lastIndex) {
                Box(
                    modifier = Modifier
                        .height(height)
                        .background(Color(0xFF6B7E96))
                        .defaultMinSize(minWidth = 1.dp)
                )
            }
        }
    }
}
