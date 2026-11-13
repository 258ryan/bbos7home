package ai.heretek.pwnapple.components.dialog

import ai.heretek.pwnapple.components.button.IosButton
import ai.heretek.pwnapple.components.button.IosButtonStyle
import ai.heretek.pwnapple.graphics.darkLinenBackground
import ai.heretek.pwnapple.graphics.glossHighlight
import ai.heretek.pwnapple.theme.PwnappleTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Skeuomorphic iOS 6 bottom action sheet with dark translucent container,
 * optional title, action buttons, destructive button, and cancel button.
 */
@Composable
fun IosActionSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    destructiveText: String? = null,
    onDestructiveClick: (() -> Unit)? = null,
    actionButtons: List<Pair<String, () -> Unit>> = emptyList(),
    cancelText: String = "Cancel",
    onCancelClick: () -> Unit = onDismissRequest
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismissRequest() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {} // consume clicks inside sheet
                .background(Color(0xE61E242B))
                .darkLinenBackground()
                .drawBehind {
                    // Top 1px highlight line
                    drawLine(
                        color = Color.White.copy(alpha = 0.25f),
                        start = Offset(0f, 0.5f),
                        end = Offset(size.width, 0.5f),
                        strokeWidth = 1f
                    )
                }
                .padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!title.isNullOrEmpty()) {
                    Text(
                        text = title,
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFFC0CAD5),
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.8f),
                                offset = Offset(0f, -1f),
                                blurRadius = 0f
                            )
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                // Destructive button (Red)
                if (destructiveText != null && onDestructiveClick != null) {
                    IosButton(
                        text = destructiveText,
                        onClick = { onDestructiveClick(); onDismissRequest() },
                        style = IosButtonStyle.Red,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Standard action buttons (Silver)
                actionButtons.forEach { (btnText, action) ->
                    IosButton(
                        text = btnText,
                        onClick = { action(); onDismissRequest() },
                        style = IosButtonStyle.Silver,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Cancel button (Black/Dark)
                IosButton(
                    text = cancelText,
                    onClick = { onCancelClick(); onDismissRequest() },
                    style = IosButtonStyle.Black,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
