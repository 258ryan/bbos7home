package ai.heretek.pwnapple.theme

import ai.heretek.pwnapple.audio.SoundPlayer
import androidx.compose.runtime.compositionLocalOf

class PwnappleAudioFeedback(
    var enabled: Boolean = true
) {
    fun playButtonClick() {
        if (enabled) SoundPlayer.playButtonClick()
    }

    fun playWheelTick() {
        if (enabled) SoundPlayer.playWheelTick()
    }

    fun playSwitchLatch() {
        if (enabled) SoundPlayer.playSwitchLatch()
    }
}

val LocalPwnappleSoundFeedback = compositionLocalOf { PwnappleAudioFeedback() }
