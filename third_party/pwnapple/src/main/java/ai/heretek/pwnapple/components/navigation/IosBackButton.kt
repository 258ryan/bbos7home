package ai.heretek.pwnapple.components.navigation

import ai.heretek.pwnapple.graphics.ArrowBackShape
import ai.heretek.pwnapple.graphics.glossHighlight
import ai.heretek.pwnapple.theme.PwnappleTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * The iconic iOS 6 chevron back button with arrow point,
 * glossy gradient fill, and embossed title text.
 */
@Composable
fun IosBackButton(
    text: String = "Back",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val pressedState by interactionSource.collectIsPressedAsState()
    val focusedState by interactionSource.collectIsFocusedAsState()
    val isPressed = pressedState || focusedState
    val sound = PwnappleTheme.sound
    val shape = remember { ArrowBackShape(arrowWidth = 13.dp, cornerRadius = 4.5.dp) }

    val brush = if (!isPressed) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF7A94B5),
                Color(0xFF567298),
                Color(0xFF425E85)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xFF334A6A),
                Color(0xFF486386)
            )
        )
    }

    Box(
        modifier = modifier
            .height(30.dp)
            .clip(shape)
            .background(brush)
            .border(1.dp, Color(0xFF263952), shape)
            .glossHighlight(cornerRadius = 4.dp, glossAlpha = 0.35f)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                sound.playButtonClick()
                onClick()
            }
            .padding(start = 14.dp, end = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color.White,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.65f),
                    offset = Offset(0f, -1.5f),
                    blurRadius = 0f
                )
            )
        )
    }
}
