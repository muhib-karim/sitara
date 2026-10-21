package app.sitara.music.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AudioQualityTest {
    @Test
    fun `auto streams high on wi-fi and normal on mobile data`() {
        val settings = SitaraSettings()
        assertEquals(AudioQuality.AUTO, settings.wifiQuality)
        assertEquals(AudioQuality.AUTO, settings.mobileQuality)
        assertEquals(AudioQuality.HIGH, streamQuality(settings, onWifi = true))
        assertEquals(AudioQuality.NORMAL, streamQuality(settings, onWifi = false))
    }

    @Test
    fun `each network uses its own setting`() {
        val settings = SitaraSettings(wifiQuality = AudioQuality.NORMAL, mobileQuality = AudioQuality.DATA_SAVER)
        assertEquals(AudioQuality.NORMAL, streamQuality(settings, onWifi = true))
        assertEquals(AudioQuality.DATA_SAVER, streamQuality(settings, onWifi = false))
        assertEquals(AudioQuality.HIGH, streamQuality(SitaraSettings(mobileQuality = AudioQuality.HIGH), onWifi = false))
    }

    @Test
    fun `the readout names the codec and bitrate, and nothing when either is unknown`() {
        assertEquals("Opus · 160 kbps", StreamFormat("Opus", 160).label)
        assertEquals(null, StreamFormat(null, 160).label)
        assertEquals(null, StreamFormat("AAC", 0).label)
    }
}
