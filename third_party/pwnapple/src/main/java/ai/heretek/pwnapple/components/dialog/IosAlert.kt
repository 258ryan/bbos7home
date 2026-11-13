package ai.heretek.pwnapple.components.dialog

import ai.heretek.pwnapple.components.button.IosButton
import ai.heretek.pwnapple.components.button.IosButtonStyle
import ai.heretek.pwnapple.graphics.glossHighlight
import ai.heretek.pwnapple.graphics.skeuoBevel
import ai.heretek.pwnapple.theme.PwnappleTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * The iconic iOS 6 UIAlertView with deep navy translucent gradient,
 * silver beveled trim, embossed title, and glossy action buttons.
 */
@Composable
fun IosAlert(
    title: String,
    message: String,
    onDismissRequest: () -> Unit,
    confirmButtonText: String = "OK",
    onConfirm: () -> Unit = onDismissRequest,
    dismissButtonText: String? = null,
    onDismiss: (() -> Unit)? = null
) {
    val colors = PwnappleTheme.colors
    val shape = remember { RoundedCornerShape(12.dp) }

    Dialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = Modifier
                .width(280.dp)
                .drawBehind {
                    // Outer dialog drop shadow
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.6f),
                        topLeft = Offset(0f, 4f),
                        size = size,
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                    )
                }
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.alertBackgroundTop.copy(alpha = 0.95f),
                            colors.alertBackgroundBottom.copy(alpha = 0.95f)
                        )
                    )
                )
                .skeuoBevel(
                    cornerRadius = 12.dp,
                    borderColor = colors.alertBorder,
                    borderWidth = 1.5.dp,
                    highlightColor = Color.White.copy(alpha = 0.4f),
                    shadowColor = Color.Black.copy(alpha = 0.5f)
                )
                .glossHighlight(cornerRadius = 12.dp, glossAlpha = 0.32f)
                .padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Alert Title
                Text(
                    text = title,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            offset = Offset(0f, -1.5f),
                            blurRadius = 0f
                        )
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Alert Message
                Text(
                    text = message,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        color = Color(0xFFD6E2F0),
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.6f),
                            offset = Offset(0f, -1f),
                            blurRadius = 0f
                        )
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons
                if (dismissButtonText != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IosButton(
                            text = dismissButtonText,
                            onClick = { onDismiss?.invoke(); onDismissRequest() },
                            style = IosButtonStyle.Silver,
                            modifier = Modifier.weight(1f)
                        )
                        IosButton(
                            text = confirmButtonText,
                            onClick = { onConfirm(); onDismissRequest() },
                            style = IosButtonStyle.Blue,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    IosButton(
                        text = confirmButtonText,
                        onClick = { onConfirm(); onDismissRequest() },
                        style = IosButtonStyle.Blue,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
