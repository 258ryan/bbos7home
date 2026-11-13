package ai.heretek.pwnapple.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Synthesizes and plays low-latency, authentic skeuomorphic tactile audio cues
 * directly via PCM AudioTrack without needing external audio asset files.
 */
object SoundPlayer {
    private const val SAMPLE_RATE = 44100
    private val executor = Executors.newSingleThreadExecutor()

    private val clickBuffer: ShortArray by lazy { generateButtonClick() }
    private val tickBuffer: ShortArray by lazy { generateWheelTick() }
    private val latchBuffer: ShortArray by lazy { generateSwitchLatch() }

    fun playButtonClick() {
        playSample(clickBuffer)
    }

    fun playWheelTick() {
        playSample(tickBuffer)
    }

    fun playSwitchLatch() {
        playSample(latchBuffer)
    }

    private fun playSample(buffer: ShortArray) {
        executor.execute {
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()
                // Sleep until playback completes then release track
                Thread.sleep((buffer.size * 1000L / SAMPLE_RATE) + 10)
                track.stop()
                track.release()
            } catch (_: Throwable) {
                // Silently ignore if audio subsystem is unavailable
            }
        }
    }

    /**
     * Crisp 22ms mechanical tactile click with fast attack and exponential decay.
     */
    private fun generateButtonClick(): ShortArray {
        val durationMs = 22
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(numSamples)
        val f1 = 1750.0
        val f2 = 820.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-t * 220.0)
            val wave = 0.7 * sin(2.0 * PI * f1 * t) + 0.3 * sin(2.0 * PI * f2 * t)
            buffer[i] = (wave * env * 24000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /**
     * 10ms high-pitch mechanical ratchet tick for picker drum wheels.
     */
    private fun generateWheelTick(): ShortArray {
        val durationMs = 12
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(numSamples)
        val freq = 3100.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-t * 450.0)
            val wave = sin(2.0 * PI * freq * t)
            buffer[i] = (wave * env * 20000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /**
     * 32ms dual-impulse snap for the classic spring-loaded sliding switch.
     */
    private fun generateSwitchLatch(): ShortArray {
        val durationMs = 32
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            // Phase 1: onset low thump
            val env1 = if (t < 0.012) exp(-t * 300.0) else 0.0
            val wave1 = sin(2.0 * PI * 550.0 * t)

            // Phase 2: sharp metallic snap 7ms later
            val t2 = t - 0.007
            val env2 = if (t2 > 0.0) exp(-t2 * 280.0) else 0.0
            val wave2 = sin(2.0 * PI * 2200.0 * t2)

            val combined = (wave1 * env1 * 0.4) + (wave2 * env2 * 0.7)
            buffer[i] = (combined * 25000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }
}
