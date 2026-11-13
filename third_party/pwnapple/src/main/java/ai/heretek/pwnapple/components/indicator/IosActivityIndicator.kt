package ai.heretek.pwnapple.components.indicator

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Authentic 12-blade iOS 6 activity indicator (spinner).
 */
@Composable
fun IosActivityIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    baseColor: Color = Color(0xFF5C5C5C)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spinnerTransition")
    val step by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinnerStep"
    )

    val currentStep = step.toInt() % 12

    Canvas(modifier = modifier.size(size)) {
        val bladeWidth = this.size.width * 0.09f
        val bladeHeight = this.size.height * 0.28f
        val radius = this.size.width / 2f

        for (i in 0 until 12) {
            val alphaIndex = (i - currentStep + 12) % 12
            // Blades fade from 1.0 down to 0.15
            val alpha = (1f - (alphaIndex / 12f) * 0.85f).coerceIn(0.15f, 1f)

            rotate(degrees = i * 30f, pivot = Offset(radius, radius)) {
                drawRoundRect(
                    color = baseColor.copy(alpha = alpha),
                    topLeft = Offset(radius - bladeWidth / 2f, this.size.height * 0.05f),
                    size = Size(bladeWidth, bladeHeight),
                    cornerRadius = CornerRadius(bladeWidth / 2f, bladeWidth / 2f)
                )
            }
        }
    }
}
