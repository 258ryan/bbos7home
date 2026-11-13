package ai.heretek.pwnapple.components.table

import ai.heretek.pwnapple.theme.PwnappleTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class IosCellAccessory {
    None,
    DisclosureIndicator,
    DetailDisclosureButton,
    Checkmark
}

/**
 * Standard iOS 6 table cell with leading icon, title, detail label,
 * disclosure chevron, and interactive blue pressed state.
 */
@Composable
fun IosTableCell(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    detailText: String? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    accessory: IosCellAccessory = IosCellAccessory.None,
    accessoryContent: @Composable (() -> Unit)? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val colors = PwnappleTheme.colors
    val typography = PwnappleTheme.typography
    val sound = PwnappleTheme.sound
    val pressedState by interactionSource.collectIsPressedAsState()
    val focusedState by interactionSource.collectIsFocusedAsState()
    val isPressed = pressedState || focusedState

    val isInteractive = onClick != null
    val indentPx = if (leadingContent != null) 48.dp else 14.dp

    val pressedBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF0A85F6),
            Color(0xFF015FE6)
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .then(
                if (isPressed && isInteractive) {
                    Modifier.background(pressedBrush)
                } else {
                    Modifier.background(colors.cardBackground)
                }
            )
            .then(
                if (isInteractive) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        sound.playButtonClick()
                        onClick?.invoke()
                    }
                } else Modifier
            )
            .drawBehind {
                if (showDivider) {
                    val startX = indentPx.toPx()
                    drawLine(
                        color = colors.cellDivider,
                        start = Offset(startX, size.height - 0.5f),
                        end = Offset(size.width, size.height - 0.5f),
                        strokeWidth = 1f
                    )
                }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading icon/content
            if (leadingContent != null) {
                Box(
                    modifier = Modifier.padding(end = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    leadingContent()
                }
            }

            // Title & optional subtitle
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = typography.cellTitle.copy(
                        color = if (isPressed && isInteractive) Color.White else colors.bodyText
                    )
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        style = typography.cellSubtitle.copy(
                            color = if (isPressed && isInteractive) Color.White.copy(alpha = 0.85f) else colors.tertiaryText
                        ),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Detail text on right
            if (!detailText.isNullOrEmpty()) {
                Text(
                    text = detailText,
                    style = typography.cellSubtitle.copy(
                        color = if (isPressed && isInteractive) Color.White else colors.secondaryText
                    ),
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            // Custom accessory or built-in accessories
            if (accessoryContent != null) {
                accessoryContent()
            } else {
                when (accessory) {
                    IosCellAccessory.DisclosureIndicator -> {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Navigate",
                            tint = if (isPressed && isInteractive) Color.White else Color(0xFF7F7F7F),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IosCellAccessory.Checkmark -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = if (isPressed && isInteractive) Color.White else Color(0xFF2E63B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IosCellAccessory.DetailDisclosureButton -> {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF5A89C7), Color(0xFF2C5CA0))
                                    ),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Detail",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    IosCellAccessory.None -> {}
                }
            }
        }
    }
}
