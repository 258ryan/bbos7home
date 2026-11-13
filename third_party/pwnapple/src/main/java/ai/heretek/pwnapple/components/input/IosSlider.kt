package ai.heretek.pwnapple.components.input

import ai.heretek.pwnapple.graphics.glossHighlight
import ai.heretek.pwnapple.graphics.innerShadow
import ai.heretek.pwnapple.theme.PwnappleTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Skeuomorphic slider with filled cyan glossy progress bar,
 * inset unfilled track, and metallic thumb disc with grip grooves.
 */
@Composable
fun IosSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true
) {
    val colors = PwnappleTheme.colors
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val thumbSize = 26.dp
    val trackHeight = 8.dp
    val trackShape = remember { RoundedCornerShape(4.dp) }

    val normalized = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val newNorm = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                    val newValue = valueRange.start + newNorm * (valueRange.endInclusive - valueRange.start)
                    onValueChange(newValue)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures { change, _ ->
                    val newNorm = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                    val newValue = valueRange.start + newNorm * (valueRange.endInclusive - valueRange.start)
                    onValueChange(newValue)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Unfilled background track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(trackShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFC0C0C0), Color(0xFFE2E2E2))
                    )
                )
                .border(0.75.dp, Color(0xFF9E9E9E), trackShape)
                .innerShadow(cornerRadius = 4.dp, shadowColor = Color(0x66000000), shadowHeight = 2.dp)
        )

        // Filled active progress track
        if (trackWidthPx > 0f) {
            val fillWidth = (trackWidthPx * normalized)
            Box(
                modifier = Modifier
                    .size(
                        width = with(androidx.compose.ui.platform.LocalDensity.current) { fillWidth.toDp() },
                        height = trackHeight
                    )
                    .clip(trackShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(colors.switchOnTop, colors.switchOnBottom)
                        )
                    )
                    .glossHighlight(cornerRadius = 4.dp, glossAlpha = 0.35f)
            )
        }

        // Metallic circular thumb
        val thumbOffsetPx = if (trackWidthPx > 0f) {
            val maxTravel = trackWidthPx - with(androidx.compose.ui.platform.LocalDensity.current) { thumbSize.toPx() }
            (maxTravel * normalized).coerceAtLeast(0f)
        } else 0f

        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffsetPx.roundToInt(), 0) }
                .size(thumbSize)
                .drawBehind {
                    // Outer drop shadow
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.35f),
                        radius = size.minDimension / 2f,
                        center = center + Offset(0f, 1.5f)
                    )
                }
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFFFFFFF),
                            Color(0xFFEDEDED),
                            Color(0xFFDCDCDC)
                        )
                    )
                )
                .border(1.dp, Color(0xFFA6A6A6), CircleShape)
                .glossHighlight(cornerRadius = 13.dp, glossAlpha = 0.45f)
                .drawBehind {
                    // Center metallic grip lines
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    for (dy in listOf(-3f, 0f, 3f)) {
                        drawLine(
                            color = Color(0xFF8B8B8B),
                            start = Offset(cx - 4f, cy + dy),
                            end = Offset(cx + 4f, cy + dy),
                            strokeWidth = 1f
                        )
                    }
                }
        )
    }
}
