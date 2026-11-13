package ai.heretek.pwnapple.components.picker

import ai.heretek.pwnapple.theme.PwnappleTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * The iconic 3D drum cylinder wheel picker (UIPickerView) with cylindrical
 * perspective curvature, glass lens overlay, and ratchet audio ticks.
 */
@Composable
fun IosPickerWheel(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleItemsCount: Int = 5,
    itemHeight: androidx.compose.ui.unit.Dp = 38.dp
) {
    if (items.isEmpty()) return

    val sound = PwnappleTheme.sound
    val itemHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { itemHeight.toPx() }
    val totalHeight = itemHeight * visibleItemsCount

    var currentOffsetPx by remember(selectedIndex) {
        mutableFloatStateOf(-selectedIndex * itemHeightPx)
    }

    var lastTickIndex by remember { mutableIntStateOf(selectedIndex) }

    val draggableState = rememberDraggableState { delta ->
        val maxOffset = 0f
        val minOffset = -(items.size - 1) * itemHeightPx
        currentOffsetPx = (currentOffsetPx + delta).coerceIn(minOffset, maxOffset)

        val activeIndex = (-currentOffsetPx / itemHeightPx).roundToInt().coerceIn(0, items.lastIndex)
        if (activeIndex != lastTickIndex) {
            sound.playWheelTick()
            lastTickIndex = activeIndex
        }
    }

    val shape = remember { RoundedCornerShape(6.dp) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFE3E5E8),
                        Color(0xFFF7F8F9),
                        Color(0xFFFFFFFF),
                        Color(0xFFF7F8F9),
                        Color(0xFFE3E5E8)
                    )
                )
            )
            .border(1.dp, Color(0xFF9E9E9E), shape)
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStopped = {
                    val targetIndex = (-currentOffsetPx / itemHeightPx).roundToInt().coerceIn(0, items.lastIndex)
                    currentOffsetPx = -targetIndex * itemHeightPx
                    onSelectedIndexChange(targetIndex)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Render visible items with 3D cylindrical projection
        items.forEachIndexed { index, text ->
            val itemCenterY = (index * itemHeightPx) + currentOffsetPx + (totalHeight.value * 1.5f)
            val diffFromCenterPx = (index * itemHeightPx) + currentOffsetPx
            val normalizedDiff = (diffFromCenterPx / itemHeightPx)

            if (abs(normalizedDiff) <= (visibleItemsCount / 2f + 1f)) {
                val scale = (1f - (abs(normalizedDiff) * 0.10f)).coerceIn(0.75f, 1f)
                val alpha = (1f - (abs(normalizedDiff) * 0.28f)).coerceIn(0.15f, 1f)
                val rotationX = (-normalizedDiff * 22f).coerceIn(-65f, 65f)

                val isCenter = abs(normalizedDiff) < 0.5f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .offset { IntOffset(0, diffFromCenterPx.roundToInt()) }
                        .graphicsLayer {
                            this.scaleX = scale
                            this.scaleY = scale
                            this.alpha = alpha
                            this.rotationX = rotationX
                            cameraDistance = 12f * density
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text,
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 20.sp,
                            color = if (isCenter) Color.Black else Color(0xFF4A4A4A),
                            shadow = if (isCenter) {
                                Shadow(
                                    color = Color.White.copy(alpha = 0.9f),
                                    offset = Offset(0f, 1.5f),
                                    blurRadius = 0f
                                )
                            } else null
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Central Glass Magnifying Lens Overlay Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .drawWithContent {
                    drawContent()
                    // Top glass reflection line
                    drawLine(
                        color = Color(0x664A7EBB),
                        start = Offset(0f, 0.5f),
                        end = Offset(size.width, 0.5f),
                        strokeWidth = 1.5f
                    )
                    // Bottom glass reflection line
                    drawLine(
                        color = Color(0x664A7EBB),
                        start = Offset(0f, size.height - 0.5f),
                        end = Offset(size.width, size.height - 0.5f),
                        strokeWidth = 1.5f
                    )
                }
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x1A2A6EB5),
                            Color(0x082A6EB5),
                            Color(0x1A2A6EB5)
                        )
                    )
                )
        )

        // Top & Bottom cylindrical shadow vignette
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    val vHeight = size.height * 0.35f
                    // Top shadow vignette
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(0x77000000), Color.Transparent),
                            startY = 0f,
                            endY = vHeight
                        ),
                        size = Size(size.width, vHeight)
                    )
                    // Bottom shadow vignette
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0x77000000)),
                            startY = size.height - vHeight,
                            endY = size.height
                        ),
                        topLeft = Offset(0f, size.height - vHeight),
                        size = Size(size.width, vHeight)
                    )
                }
        )
    }
}
