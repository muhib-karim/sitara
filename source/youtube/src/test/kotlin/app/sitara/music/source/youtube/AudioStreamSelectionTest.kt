package app.sitara.music.source.youtube

import app.sitara.music.core.model.AudioQuality
import com.grack.nanojson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod

class AudioStreamSelectionTest {
    private fun audio(
        url: String,
        format: MediaFormat?,
        kbps: Int,
        delivery: DeliveryMethod = DeliveryMethod.PROGRESSIVE_HTTP,
        trackType: AudioTrackType? = null,
    ): AudioStream = AudioStream.Builder()
        .setId("itag")
        .setContent(url, true)
        .setMediaFormat(format)
        .setDeliveryMethod(delivery)
        .setAverageBitrate(kbps)
        .setAudioTrackType(trackType)
        .build()

    @Test
    fun `picks the highest bitrate and reports its mime type, bitrate and expiry`() {
        val streams = listOf(
            audio("https://rr1.googlevideo.com/videoplayback?itag=140&expire=1760000000", MediaFormat.M4A, 129),
            audio("https://rr1.googlevideo.com/videoplayback?itag=251&expire=1760000000&ip=1", MediaFormat.WEBMA_OPUS, 141),
            audio("https://rr1.googlevideo.com/videoplayback?itag=250", MediaFormat.WEBMA_OPUS, 70),
        )

        val stream = selectAudioStream(streams)!!

        assertEquals("https://rr1.googlevideo.com/videoplayback?itag=251&expire=1760000000&ip=1", stream.url)
        assertEquals("audio/webm", stream.mimeType)
        assertEquals(141, stream.bitrateKbps)
        assertEquals(1_760_000_000_000L, stream.expiresAtMs)
    }

    @Test
    fun `opus wins a bitrate tie with m4a`() {
        val streams = listOf(
            audio("https://a.example/m4a", MediaFormat.M4A, 128),
            audio("https://a.example/opus", MediaFormat.WEBMA_OPUS, 128),
        )

        assertEquals("https://a.example/opus", selectAudioStream(streams)!!.url)
    }

    @Test
    fun `m4a is used when it is the best playable stream`() {
        val stream = selectAudioStream(listOf(audio("https://a.example/m4a", MediaFormat.M4A, 129)))!!

        assertEquals("audio/mp4", stream.mimeType)
        assertNull(stream.expiresAtMs)
    }

    @Test
    fun `skips plain http, manifests, unknown formats and dubbed tracks`() {
        val streams = listOf(
            audio("http://a.example/insecure", MediaFormat.WEBMA_OPUS, 320),
            audio("https://a.example/manifest.mpd", MediaFormat.M4A, 256, delivery = DeliveryMethod.DASH),
            audio("https://a.example/mp3", MediaFormat.MP3, 256),
            audio("https://a.example/unknown", null, 256),
            audio("https://a.example/dubbed", MediaFormat.WEBMA_OPUS, 200, trackType = AudioTrackType.DUBBED),
            audio("https://a.example/original", MediaFormat.WEBMA_OPUS, 130, trackType = AudioTrackType.ORIGINAL),
        )

        assertEquals("https://a.example/original", selectAudioStream(streams)!!.url)
    }

    @Test
    fun `falls back to a dubbed track when it is the only audio`() {
        val streams = listOf(audio("https://a.example/dubbed", MediaFormat.M4A, 128, trackType = AudioTrackType.DUBBED))

        assertEquals("https://a.example/dubbed", selectAudioStream(streams)!!.url)
    }

    @Test
    fun `unknown bitrate is reported as zero and ranks last`() {
        val streams = listOf(
            audio("https://a.example/unknown", MediaFormat.WEBMA_OPUS, AudioStream.UNKNOWN_BITRATE),
            audio("https://a.example/known", MediaFormat.M4A, 48),
        )

        assertEquals("https://a.example/known", selectAudioStream(streams)!!.url)
        assertEquals(0, selectAudioStream(streams.take(1))!!.bitrateKbps)
    }

    @Test
    fun `nothing playable gives null`() {
        assertNull(selectAudioStream(emptyList()))
        assertNull(selectAudioStream(listOf(audio("http://a.example/a", MediaFormat.M4A, 128))))
    }

    @Test
    fun `a malformed expire parameter is ignored`() {
        assertNull(selectAudioStream(listOf(audio("https://a.example/a?expire=soon", MediaFormat.M4A, 128)))!!.expiresAtMs)
    }

    // YouTube's usual audio ladder: two AAC and three Opus streams.
    private val ladder = listOf(
        audio("https://a.example/139", MediaFormat.M4A, 48),
        audio("https://a.example/140", MediaFormat.M4A, 129),
        audio("https://a.example/249", MediaFormat.WEBMA_OPUS, 50),
        audio("https://a.example/250", MediaFormat.WEBMA_OPUS, 70),
        audio("https://a.example/251", MediaFormat.WEBMA_OPUS, 141),
    )

    @Test
    fun `high takes the highest bitrate, normal the highest up to 96 kbps, data saver the lowest`() {
        assertEquals("https://a.example/251", selectAudioStream(ladder, AudioQuality.HIGH)!!.url)
        assertEquals("https://a.example/250", selectAudioStream(ladder, AudioQuality.NORMAL)!!.url)
        assertEquals("https://a.example/139", selectAudioStream(ladder, AudioQuality.DATA_SAVER)!!.url)
    }

    @Test
    fun `normal takes the lowest stream when every one is above 96 kbps`() {
        assertEquals("https://a.example/140", selectAudioStream(listOf(ladder[1], ladder[4]), AudioQuality.NORMAL)!!.url)
    }

    @Test
    fun `a stream of unknown bitrate is never taken to save data while others say theirs`() {
        val streams = listOf(audio("https://a.example/unknown", MediaFormat.WEBMA_OPUS, AudioStream.UNKNOWN_BITRATE), ladder[3])
        assertEquals("https://a.example/250", selectAudioStream(streams, AudioQuality.DATA_SAVER)!!.url)
    }

    @Test
    fun `the codec is named for the readout`() {
        assertEquals("Opus", selectAudioStream(ladder, AudioQuality.HIGH)!!.codec)
        assertEquals("AAC", selectAudioStream(ladder.take(2), AudioQuality.HIGH)!!.codec)
    }

    private fun format(url: String, mimeType: String, bitrate: Int): JsonObject =
        JsonObject.builder().value("url", url).value("mimeType", mimeType).value("averageBitrate", bitrate).done()

    @Test
    fun `player response formats are chosen by quality too, with their codec from the mime type`() {
        val formats = listOf(
            format("https://a.example/140", "audio/mp4; codecs=\"mp4a.40.2\"", 129_000),
            format("https://a.example/250", "audio/webm; codecs=\"opus\"", 70_000),
            format("https://a.example/251", "audio/webm; codecs=\"opus\"", 141_000),
        )
        assertEquals("https://a.example/251", selectAudioFormat(formats, AudioQuality.HIGH)!!.url)
        val normal = selectAudioFormat(formats, AudioQuality.NORMAL)!!
        assertEquals("https://a.example/250", normal.url)
        assertEquals("Opus", normal.codec)
        assertEquals(70, normal.bitrateKbps)
        assertEquals("AAC", selectAudioFormat(formats.take(1), AudioQuality.DATA_SAVER)!!.codec)
    }
}
