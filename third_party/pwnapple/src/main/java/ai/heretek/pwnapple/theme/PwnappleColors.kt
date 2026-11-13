package ai.heretek.pwnapple.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Immutable
data class PwnappleColors(
    // Canvas & Backgrounds
    val canvasPinstripeBase: Color = Color(0xFFC5CCD4),
    val canvasPinstripeStripe: Color = Color(0xFFCBD2D8),
    val canvasDarkLinenBase: Color = Color(0xFF262B30),
    val canvasDarkLinenStripe: Color = Color(0xFF30363C),

    // Grouped Table Cards
    val cardBackground: Color = Color(0xFFFFFFFF),
    val cardBorder: Color = Color(0xFFABABAB),
    val cellDivider: Color = Color(0xFFE2E2E2),
    val cellPressed: Color = Color(0xFF0272EC),
    val cellPressedText: Color = Color(0xFFFFFFFF),

    // Navigation Bar
    val navBarBlueTop: Color = Color(0xFF8A9EB9),
    val navBarBlueBottom: Color = Color(0xFF5C7496),
    val navBarBlackTop: Color = Color(0xFF4C4C4C),
    val navBarBlackBottom: Color = Color(0xFF141414),
    val navBarShadow: Color = Color(0xFF2A3442),

    // Standard Buttons (Silver)
    val buttonSilverTop: Color = Color(0xFFFCFCFC),
    val buttonSilverMid: Color = Color(0xFFE9E9E9),
    val buttonSilverBottom: Color = Color(0xFFD4D4D4),
    val buttonSilverBorder: Color = Color(0xFFA5A5A5),
    val buttonSilverText: Color = Color(0xFF222222),

    // Action Buttons (Gloss Blue)
    val buttonBlueTop: Color = Color(0xFF6E9BD7),
    val buttonBlueMid: Color = Color(0xFF3F75C1),
    val buttonBlueBottom: Color = Color(0xFF2B5FAA),
    val buttonBlueBorder: Color = Color(0xFF1E4378),
    val buttonBlueText: Color = Color(0xFFFFFFFF),

    // Destructive Buttons (Gloss Red)
    val buttonRedTop: Color = Color(0xFFEE5353),
    val buttonRedMid: Color = Color(0xFFDA2A2A),
    val buttonRedBottom: Color = Color(0xFFB81818),
    val buttonRedBorder: Color = Color(0xFF851010),
    val buttonRedText: Color = Color(0xFFFFFFFF),

    // Switch & Slider (Cyan / Blue)
    val switchOnTop: Color = Color(0xFF2D8CE6),
    val switchOnBottom: Color = Color(0xFF0A62BD),
    val switchOffTop: Color = Color(0xFFD6D6D6),
    val switchOffBottom: Color = Color(0xFFF2F2F2),
    val switchKnobTop: Color = Color(0xFFFFFFFF),
    val switchKnobBottom: Color = Color(0xFFE4E4E4),
    val switchKnobBorder: Color = Color(0xFFB0B0B0),

    // Text & Headers
    val headerText: Color = Color(0xFF4C566C),
    val bodyText: Color = Color(0xFF000000),
    val secondaryText: Color = Color(0xFF385487),
    val tertiaryText: Color = Color(0xFF7F7F7F),

    // Tab Bar
    val tabBarBackground: Color = Color(0xFF121212),
    val tabBarBorder: Color = Color(0xFF000000),
    val tabBarActiveGlow: Color = Color(0xFF2EABFE),
    val tabBarInactive: Color = Color(0xFF919191),

    // Badges & Indicators
    val badgeRedTop: Color = Color(0xFFEE2828),
    val badgeRedBottom: Color = Color(0xFFB21010),
    val badgeBorder: Color = Color(0xFFFFFFFF),

    // Alert & Action Sheet
    val alertBackgroundTop: Color = Color(0xFF233654),
    val alertBackgroundBottom: Color = Color(0xFF111E33),
    val alertBorder: Color = Color(0xFF8B9FB8)
) {
    fun navBarBlueBrush(): Brush = Brush.verticalGradient(
        listOf(navBarBlueTop, navBarBlueBottom)
    )

    fun navBarBlackBrush(): Brush = Brush.verticalGradient(
        listOf(navBarBlackTop, navBarBlackBottom)
    )

    fun buttonSilverBrush(isPressed: Boolean = false): Brush {
        return if (!isPressed) {
            Brush.verticalGradient(
                listOf(buttonSilverTop, buttonSilverMid, buttonSilverBottom)
            )
        } else {
            Brush.verticalGradient(
                listOf(buttonSilverBottom, buttonSilverMid, buttonSilverTop)
            )
        }
    }

    fun buttonBlueBrush(isPressed: Boolean = false): Brush {
        return if (!isPressed) {
            Brush.verticalGradient(
                listOf(buttonBlueTop, buttonBlueMid, buttonBlueBottom)
            )
        } else {
            Brush.verticalGradient(
                listOf(buttonBlueBottom, buttonBlueMid, buttonBlueTop)
            )
        }
    }

    fun buttonRedBrush(isPressed: Boolean = false): Brush {
        return if (!isPressed) {
            Brush.verticalGradient(
                listOf(buttonRedTop, buttonRedMid, buttonRedBottom)
            )
        } else {
            Brush.verticalGradient(
                listOf(buttonRedBottom, buttonRedMid, buttonRedTop)
            )
        }
    }

    fun switchOnBrush(): Brush = Brush.verticalGradient(
        listOf(switchOnTop, switchOnBottom)
    )

    fun switchOffBrush(): Brush = Brush.verticalGradient(
        listOf(switchOffTop, switchOffBottom)
    )
}
