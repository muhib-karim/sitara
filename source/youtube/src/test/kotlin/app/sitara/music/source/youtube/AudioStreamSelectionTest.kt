package app.sitara.music.source.youtube

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `video ids are exactly eleven url-safe characters`() {
        assertTrue(isVideoId("dQw4w9WgXcQ"))
        assertTrue(isVideoId("a-b_C1d2E3f"))
        assertFalse(isVideoId("tr-01"))
        assertFalse(isVideoId("dQw4w9WgXcQ&list=x"))
        assertFalse(isVideoId("dQw4w9WgXc/"))
    }
}
