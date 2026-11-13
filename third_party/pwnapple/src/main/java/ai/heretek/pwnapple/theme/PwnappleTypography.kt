package ai.heretek.pwnapple.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class PwnappleTypography(
    val navTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        color = Color.White,
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.65f),
            offset = Offset(0f, -2f),
            blurRadius = 0f
        )
    ),
    val sectionHeader: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        color = Color(0xFF4C566C),
        shadow = Shadow(
            color = Color.White.copy(alpha = 0.9f),
            offset = Offset(0f, 2f),
            blurRadius = 0f
        )
    ),
    val sectionFooter: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = Color(0xFF4C566C),
        shadow = Shadow(
            color = Color.White.copy(alpha = 0.9f),
            offset = Offset(0f, 2f),
            blurRadius = 0f
        )
    ),
    val cellTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        color = Color.Black
    ),
    val cellSubtitle: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        color = Color(0xFF385487)
    ),
    val buttonSilverText: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = Color(0xFF222222),
        shadow = Shadow(
            color = Color.White.copy(alpha = 0.8f),
            offset = Offset(0f, 2f),
            blurRadius = 0f
        )
    ),
    val buttonColorText: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = Color.White,
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.6f),
            offset = Offset(0f, -2f),
            blurRadius = 0f
        )
    ),
    val tabBarLabel: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        color = Color(0xFF919191)
    ),
    val badgeText: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = Color.White,
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.6f),
            offset = Offset(0f, -1.5f),
            blurRadius = 0f
        )
    ),
    val switchLabel: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = Color.White,
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.4f),
            offset = Offset(0f, -1.5f),
            blurRadius = 0f
        )
    )
)
