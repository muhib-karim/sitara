package app.sitara.music.core.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Sitara's sound in ExoPlayer's audio pipeline: [leveller] evens out loudness, then [equaliser] shapes it. Runs on
 * 16-bit or float PCM; other encodings pass by untouched.
 */
@OptIn(UnstableApi::class)
class SoundAudioProcessor(private val leveller: LoudnessLeveller, private val equaliser: Equaliser) : BaseAudioProcessor() {
    private var samples = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat = when (inputAudioFormat.encoding) {
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT -> inputAudioFormat
        else -> AudioProcessor.AudioFormat.NOT_SET
    }

    // A seek or a new stream: the equaliser's filters must not ring with audio from before it.
    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) = equaliser.reset()

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val output = replaceOutputBuffer(size)
        if (leveller.isUnity && equaliser.isUnity) {
            output.put(inputBuffer).flip()
            return
        }
        val isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val count = size / (if (isFloat) 4 else 2)
        if (samples.size < count) samples = FloatArray(count)
        for (i in 0 until count) samples[i] = if (isFloat) inputBuffer.getFloat() else inputBuffer.getShort() / 32_768f
        leveller.process(samples, count, inputAudioFormat.channelCount, inputAudioFormat.sampleRate)
        equaliser.process(samples, count, inputAudioFormat.channelCount, inputAudioFormat.sampleRate)
        for (i in 0 until count) {
            if (isFloat) output.putFloat(samples[i]) else output.putShort((samples[i] * 32_768f).roundToInt().coerceIn(-32_768, 32_767).toShort())
        }
        output.flip()
    }
}
