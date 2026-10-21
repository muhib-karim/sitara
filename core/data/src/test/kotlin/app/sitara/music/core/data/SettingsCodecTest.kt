package app.sitara.music.core.data

import app.sitara.music.core.model.AccentColor
import app.sitara.music.core.model.AudioQuality
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.EqualiserPreset
import app.sitara.music.core.model.EqualiserSettings
import app.sitara.music.core.model.LibraryLayout
import app.sitara.music.core.model.LibrarySort
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SitaraSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsCodecTest {
    @Test
    fun `round trips every field`() {
        val s = SitaraSettings(
            darkMode = DarkMode.DARK, pureBlack = true, dynamicColor = false, accent = AccentColor.TEAL, artworkColorsAcrossApp = true, reducedMotion = true, crossfadeSeconds = 6, normalisation = false, targetLufs = -16f, downloadOnWifiOnly = false, skipSilence = true,
            equaliser = EqualiserSettings(enabled = true, bandsDb = listOf(-12, -7, 0, 1, 2, 3, 4, 5, 11, 12), preampDb = -5, useSystem = true),
            searchFilter = SearchFilter.PLAYLISTS, recentSearches = listOf("lo-fi beats", "Jazz, piano"),
            libraryLayout = LibraryLayout.LIST, librarySort = LibrarySort.MOST_PLAYED, pauseListeningHistory = true,
            wifiQuality = AudioQuality.NORMAL, mobileQuality = AudioQuality.DATA_SAVER,
        )
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun `every equaliser preset round trips and is still recognised`() {
        EqualiserPreset.entries.forEach { preset ->
            val s = SitaraSettings(equaliser = EqualiserSettings().withPreset(preset))
            assertEquals(preset, SettingsCodec.decode(SettingsCodec.encode(s)).equaliser.preset)
        }
    }

    @Test
    fun `a corrupt equaliser falls back to flat bands, clamping gains`() {
        val decoded = { bands: String -> SettingsCodec.decode(mapOf(SettingsCodec.EQ_BANDS to bands, SettingsCodec.EQ_PREAMP to "40")).equaliser }
        assertEquals(EqualiserSettings().bandsDb, decoded("1,2,3").bandsDb)
        assertEquals(EqualiserSettings().bandsDb, decoded("1,2,3,4,5,6,7,8,9,x").bandsDb)
        assertEquals(EqualiserSettings().bandsDb, decoded("1,2,3,4,5,6,7,8,9,10,11").bandsDb)
        assertEquals(listOf(12, -12, 0, 0, 0, 0, 0, 0, 0, 3), decoded("99,-99,0,0,0,0,0,0,0,3").bandsDb)
        assertEquals(12, decoded("").preampDb)
        assertEquals(false, decoded("").enabled)
    }

    @Test
    fun `empty storage gives defaults`() {
        assertEquals(SitaraSettings(), SettingsCodec.decode(emptyMap()))
    }

    @Test
    fun `a fresh install follows the system theme with wallpaper colours and the saffron accent`() {
        val s = SettingsCodec.decode(emptyMap())
        assertEquals(DarkMode.SYSTEM, s.darkMode)
        assertEquals(false, s.pureBlack)
        assertEquals(true, s.dynamicColor)
        assertEquals(AccentColor.SAFFRON, s.accent)
        assertEquals(false, s.artworkColorsAcrossApp)
    }

    @Test
    fun `the old AMOLED colour choice becomes pure black in the dark theme, once`() {
        val legacy = SettingsCodec.decode(mapOf(SettingsCodec.LEGACY_THEME to "AMOLED", SettingsCodec.DARK to "SYSTEM"))
        assertEquals(true, legacy.pureBlack)
        assertEquals(DarkMode.DARK, legacy.darkMode)
        // Once the new keys are written, the left-over old key no longer decides anything.
        val rewritten = SettingsCodec.encode(legacy.copy(pureBlack = false, darkMode = DarkMode.LIGHT)) + (SettingsCodec.LEGACY_THEME to "AMOLED")
        assertEquals(false, SettingsCodec.decode(rewritten).pureBlack)
        assertEquals(DarkMode.LIGHT, SettingsCodec.decode(rewritten).darkMode)
        assertEquals(false, SettingsCodec.decode(mapOf(SettingsCodec.LEGACY_THEME to "ARTWORK")).pureBlack)
    }

    @Test
    fun `downloads wait for wifi unless the listener allows mobile data`() {
        assertEquals(true, SettingsCodec.decode(emptyMap()).downloadOnWifiOnly)
        assertEquals(true, SettingsCodec.decode(mapOf(SettingsCodec.WIFI_ONLY to "maybe")).downloadOnWifiOnly)
        assertEquals(false, SettingsCodec.decode(mapOf(SettingsCodec.WIFI_ONLY to "false")).downloadOnWifiOnly)
    }

    @Test
    fun `listening history is kept unless the listener paused it`() {
        assertEquals(false, SettingsCodec.decode(emptyMap()).pauseListeningHistory)
        assertEquals(true, SettingsCodec.decode(mapOf(SettingsCodec.PAUSE_HISTORY to "true")).pauseListeningHistory)
    }

    @Test
    fun `skip silence is off unless the listener turned it on`() {
        assertEquals(false, SettingsCodec.decode(emptyMap()).skipSilence)
        assertEquals(false, SettingsCodec.decode(mapOf(SettingsCodec.SKIP_SILENCE to "1")).skipSilence)
        assertEquals(true, SettingsCodec.decode(mapOf(SettingsCodec.SKIP_SILENCE to "true")).skipSilence)
    }

    @Test
    fun `search starts on Songs with no recent searches, and Library as a grid of what was added last`() {
        val s = SettingsCodec.decode(emptyMap())
        assertEquals(SearchFilter.SONGS, s.searchFilter)
        assertEquals(emptyList<String>(), s.recentSearches)
        assertEquals(LibraryLayout.GRID, s.libraryLayout)
        assertEquals(LibrarySort.RECENTLY_ADDED, s.librarySort)
    }

    @Test
    fun `no recent searches round trip as none, and blank lines are not searches`() {
        assertEquals(emptyList<String>(), SettingsCodec.decode(SettingsCodec.encode(SitaraSettings())).recentSearches)
        assertEquals(listOf("a", "b"), SettingsCodec.decode(mapOf(SettingsCodec.RECENT_SEARCHES to "a\n\n  \nb")).recentSearches)
    }

    @Test
    fun `corrupt values fall back or clamp`() {
        val s = SettingsCodec.decode(
            mapOf(
                SettingsCodec.ACCENT to "NEON",
                SettingsCodec.DYNAMIC_COLOR to "on",
                SettingsCodec.REDUCED_MOTION to "yes",
                SettingsCodec.CROSSFADE to "99",
                SettingsCodec.TARGET_LUFS to "3",
                SettingsCodec.SEARCH_FILTER to "VIDEOS",
                SettingsCodec.LIBRARY_LAYOUT to "TILES",
                SettingsCodec.LIBRARY_SORT to "",
            ),
        )
        assertEquals(AccentColor.SAFFRON, s.accent)
        assertEquals(true, s.dynamicColor)
        assertEquals(false, s.reducedMotion)
        assertEquals(12, s.crossfadeSeconds)
        assertEquals(-14f, s.targetLufs)
        assertEquals(SearchFilter.SONGS, s.searchFilter)
        assertEquals(LibraryLayout.GRID, s.libraryLayout)
        assertEquals(LibrarySort.RECENTLY_ADDED, s.librarySort)
    }

    @Test
    fun `model rejects out of range crossfade`() {
        assertThrows(IllegalArgumentException::class.java) { SitaraSettings(crossfadeSeconds = 13) }
    }
}
