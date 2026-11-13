package ai.heretek.pwnapple.components.tabs

import ai.heretek.pwnapple.theme.PwnappleTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class IosTabItem(
    val title: String,
    val icon: ImageVector,
    val badge: String? = null
)

/**
 * Skeuomorphic dark matte bottom tab bar with blue glow active indicators.
 */
@Composable
fun IosTabBar(
    items: List<IosTabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val sound = PwnappleTheme.sound
    val colors = PwnappleTheme.colors
    val typography = PwnappleTheme.typography

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(49.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF222222),
                        Color(0xFF141414),
                        Color(0xFF080808)
                    )
                )
            )
            .drawBehind {
                // Top 1px border
                drawLine(
                    color = Color.Black,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f
                )
                // Inner highlight line
                drawLine(
                    color = Color.White.copy(alpha = 0.15f),
                    start = Offset(0f, 1f),
                    end = Offset(size.width, 1f),
                    strokeWidth = 1f
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val tintColor = if (isSelected) colors.tabBarActiveGlow else colors.tabBarInactive

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (index != selectedIndex) {
                                sound.playButtonClick()
                                onTabSelected(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = tintColor,
                                modifier = Modifier.size(24.dp)
                            )
                            if (item.badge != null) {
                                IosBadge(
                                    count = item.badge,
                                    modifier = Modifier.offset(x = 10.dp, y = (-4).dp)
                                )
                            }
                        }

                        Text(
                            text = item.title,
                            style = typography.tabBarLabel.copy(
                                color = tintColor,
                                shadow = if (isSelected) {
                                    Shadow(
                                        color = Color(0x992EABFE),
                                        offset = Offset(0f, 0f),
                                        blurRadius = 4f
                                    )
                                } else null
                            ),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
