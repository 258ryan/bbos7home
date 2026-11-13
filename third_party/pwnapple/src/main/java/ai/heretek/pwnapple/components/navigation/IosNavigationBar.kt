package ai.heretek.pwnapple.components.navigation

import ai.heretek.pwnapple.graphics.glossHighlight
import ai.heretek.pwnapple.theme.PwnappleTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class IosNavBarStyle {
    DefaultBlue,
    BlackTranslucent,
    BlackOpaque
}

/**
 * Standard iOS 6 navigation bar with glossy gradient, embossed center title,
 * specular highlight line, and bottom hairline border.
 */
@Composable
fun IosNavigationBar(
    title: String,
    modifier: Modifier = Modifier,
    style: IosNavBarStyle = IosNavBarStyle.DefaultBlue,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = PwnappleTheme.colors
    val typography = PwnappleTheme.typography

    val brush = when (style) {
        IosNavBarStyle.DefaultBlue -> colors.navBarBlueBrush()
        IosNavBarStyle.BlackTranslucent -> Brush.verticalGradient(
            listOf(colors.navBarBlackTop.copy(alpha = 0.88f), colors.navBarBlackBottom.copy(alpha = 0.88f))
        )
        IosNavBarStyle.BlackOpaque -> colors.navBarBlackBrush()
    }

    val bottomBorderColor = when (style) {
        IosNavBarStyle.DefaultBlue -> Color(0xFF2A374A)
        else -> Color(0xFF000000)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(brush)
            .glossHighlight(cornerRadius = 0.dp, glossAlpha = 0.28f)
            .drawBehind {
                // Top 1px specular line
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(0f, 0.5f),
                    end = Offset(size.width, 0.5f),
                    strokeWidth = 1f
                )
                // Bottom 1px hairline border
                drawLine(
                    color = bottomBorderColor,
                    start = Offset(0f, size.height - 0.5f),
                    end = Offset(size.width, size.height - 0.5f),
                    strokeWidth = 1f
                )
                // Bottom drop shadow
                drawLine(
                    color = Color.Black.copy(alpha = 0.3f),
                    start = Offset(0f, size.height + 0.5f),
                    end = Offset(size.width, size.height + 0.5f),
                    strokeWidth = 1.5f
                )
            }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Left navigation icon
        if (navigationIcon != null) {
            Box(
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                navigationIcon()
            }
        }

        // Embossed center title
        Text(
            text = title,
            style = typography.navTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 48.dp)
        )

        // Right actions
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
    }
}
