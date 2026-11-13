package ai.heretek.pwnapple.components.button

import ai.heretek.pwnapple.graphics.glossHighlight
import ai.heretek.pwnapple.graphics.innerShadow
import ai.heretek.pwnapple.graphics.skeuoBevel
import ai.heretek.pwnapple.theme.PwnappleTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class IosButtonStyle {
    Silver,
    Blue,
    Red,
    Black
}

enum class IosButtonSize {
    Normal,
    Small
}

/**
 * Authentic iOS 6 glossy beveled button with tactile pressed states,
 * specular reflection, and click audio.
 */
@Composable
fun IosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: IosButtonStyle = IosButtonStyle.Silver,
    size: IosButtonSize = IosButtonSize.Normal,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val colors = PwnappleTheme.colors
    val typography = PwnappleTheme.typography
    val sound = PwnappleTheme.sound
    val pressedState by interactionSource.collectIsPressedAsState()
    val focusedState by interactionSource.collectIsFocusedAsState()
    val isPressed = pressedState || focusedState

    val cornerRadius: Dp = if (size == IosButtonSize.Small) 4.5.dp else 6.dp
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    val brush = when (style) {
        IosButtonStyle.Silver -> colors.buttonSilverBrush(isPressed)
        IosButtonStyle.Blue -> colors.buttonBlueBrush(isPressed)
        IosButtonStyle.Red -> colors.buttonRedBrush(isPressed)
        IosButtonStyle.Black -> if (!isPressed) colors.navBarBlackBrush() else colors.navBarBlackBrush()
    }

    val borderColor = when (style) {
        IosButtonStyle.Silver -> colors.buttonSilverBorder
        IosButtonStyle.Blue -> colors.buttonBlueBorder
        IosButtonStyle.Red -> colors.buttonRedBorder
        IosButtonStyle.Black -> Color.Black
    }

    val textStyle: TextStyle = when (style) {
        IosButtonStyle.Silver -> typography.buttonSilverText
        else -> typography.buttonColorText
    }

    val minHeight: Dp = if (size == IosButtonSize.Small) 30.dp else 42.dp
    val horizontalPadding: Dp = if (size == IosButtonSize.Small) 10.dp else 18.dp

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .clip(shape)
            .background(brush)
            .skeuoBevel(
                cornerRadius = cornerRadius,
                borderColor = borderColor,
                highlightColor = if (isPressed) Color.Transparent else Color.White.copy(alpha = 0.4f),
                shadowColor = if (isPressed) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.2f)
            )
            .then(
                if (isPressed) {
                    Modifier.innerShadow(cornerRadius = cornerRadius, shadowColor = Color(0x66000000), shadowHeight = 3.dp)
                } else {
                    Modifier.glossHighlight(cornerRadius = cornerRadius, glossAlpha = 0.42f)
                }
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) {
                sound.playButtonClick()
                onClick()
            }
            .padding(horizontal = horizontalPadding, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = textStyle,
            modifier = Modifier.offset(y = if (isPressed) 1.dp else 0.dp)
        )
    }
}
