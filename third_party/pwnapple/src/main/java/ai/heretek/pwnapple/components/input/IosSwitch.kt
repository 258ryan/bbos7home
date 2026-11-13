package ai.heretek.pwnapple.components.input

import ai.heretek.pwnapple.graphics.glossHighlight
import ai.heretek.pwnapple.graphics.innerShadow
import ai.heretek.pwnapple.theme.PwnappleTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * The iconic iOS 6 sliding switch with glossy cyan "ON" and silver "OFF" tracks,
 * beveled circular metal thumb, embossed labels, and tactile latch audio.
 */
@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val sound = PwnappleTheme.sound
    val colors = PwnappleTheme.colors

    val trackWidth = 76.dp
    val trackHeight = 28.dp
    val thumbSize = 28.dp
    val cornerRadius = 14.dp
    val trackShape = remember { RoundedCornerShape(cornerRadius) }

    // Progress: 0.0 (OFF) -> 1.0 (ON)
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 600f),
        label = "switchThumbPosition"
    )

    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(trackShape)
            .border(1.dp, Color(0xFF949494), trackShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                sound.playSwitchLatch()
                onCheckedChange(!checked)
            }
    ) {
        // Track layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (checked) colors.switchOnBrush() else colors.switchOffBrush()
                )
                .innerShadow(cornerRadius = cornerRadius, shadowColor = Color(0x66000000), shadowHeight = 4.dp)
                .glossHighlight(cornerRadius = cornerRadius, glossAlpha = 0.25f)
        ) {
            // "ON" text when checked
            if (progress > 0.4f) {
                Text(
                    text = "ON",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = progress),
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.45f * progress),
                            offset = Offset(0f, -1.5f),
                            blurRadius = 0f
                        )
                    ),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                )
            }

            // "OFF" text when unchecked
            if (progress < 0.6f) {
                Text(
                    text = "OFF",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF7D7D7D).copy(alpha = 1f - progress),
                        shadow = Shadow(
                            color = Color.White.copy(alpha = 0.9f * (1f - progress)),
                            offset = Offset(0f, 1.5f),
                            blurRadius = 0f
                        )
                    ),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                )
            }
        }

        // Circular Thumb Knob
        val maxOffsetPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            (trackWidth - thumbSize).toPx()
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(x = (maxOffsetPx * progress).roundToInt(), y = 0) }
                .size(thumbSize)
                .drawBehind {
                    // Drop shadow on knob
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.35f),
                        radius = size.minDimension / 2f,
                        center = center + Offset(if (checked) -2f else 2f, 1.5f)
                    )
                }
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.switchKnobTop,
                            Color(0xFFEDEDED),
                            colors.switchKnobBottom
                        )
                    )
                )
                .border(1.dp, Color(0xFFB0B0B0), CircleShape)
                .glossHighlight(cornerRadius = cornerRadius, glossAlpha = 0.45f)
        )
    }
}
