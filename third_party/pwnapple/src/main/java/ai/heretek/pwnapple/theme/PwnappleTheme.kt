package ai.heretek.pwnapple.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

val LocalPwnappleColors = staticCompositionLocalOf { PwnappleColors() }
val LocalPwnappleTypography = staticCompositionLocalOf { PwnappleTypography() }

@Composable
fun PwnappleTheme(
    colors: PwnappleColors = PwnappleTheme.colors,
    typography: PwnappleTypography = PwnappleTheme.typography,
    soundFeedbackEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val soundFeedback = remember(soundFeedbackEnabled) {
        PwnappleAudioFeedback(enabled = soundFeedbackEnabled)
    }

    CompositionLocalProvider(
        LocalPwnappleColors provides colors,
        LocalPwnappleTypography provides typography,
        LocalPwnappleSoundFeedback provides soundFeedback,
        content = content
    )
}

object PwnappleTheme {
    val colors: PwnappleColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPwnappleColors.current

    val typography: PwnappleTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalPwnappleTypography.current

    val sound: PwnappleAudioFeedback
        @Composable
        @ReadOnlyComposable
        get() = LocalPwnappleSoundFeedback.current
}
