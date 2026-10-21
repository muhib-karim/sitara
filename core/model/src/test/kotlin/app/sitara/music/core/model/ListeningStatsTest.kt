package app.sitara.music.core.model

import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ListeningStatsTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val newYork = ZoneId.of("America/New_York")

    private fun ms(zone: ZoneId, y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    // Wednesday 1 April 2026, 00:30 in Berlin: still Tuesday 31 March in New York.
    private val now = ms(berlin, 2026, 4, 1, 0, 30)

    @Test
    fun `this week starts at local midnight of the first day of the week`() {
        assertEquals(ms(berlin, 2026, 3, 30), StatsRange.WEEK.startMs(now, berlin, DayOfWeek.MONDAY))
        assertEquals(ms(berlin, 2026, 3, 29), StatsRange.WEEK.startMs(now, berlin, DayOfWeek.SUNDAY))
        assertEquals(ms(newYork, 2026, 3, 29), StatsRange.WEEK.startMs(now, newYork, DayOfWeek.SUNDAY))
    }

    @Test
    fun `a week that crosses a clock change still starts at local midnight`() {
        // Clocks went forward in Berlin on Sunday 29 March 2026.
        val sunday = ms(berlin, 2026, 3, 29, 12)
        assertEquals(ms(berlin, 2026, 3, 23), StatsRange.WEEK.startMs(sunday, berlin, DayOfWeek.MONDAY))
    }

    @Test
    fun `this month and this year follow the phone's time zone`() {
        assertEquals(ms(berlin, 2026, 4, 1), StatsRange.MONTH.startMs(now, berlin, DayOfWeek.MONDAY))
        assertEquals("the same moment is still March in New York", ms(newYork, 2026, 3, 1), StatsRange.MONTH.startMs(now, newYork, DayOfWeek.MONDAY))
        assertEquals(ms(berlin, 2026, 1, 1), StatsRange.YEAR.startMs(now, berlin, DayOfWeek.MONDAY))
        assertEquals(0L, StatsRange.ALL_TIME.startMs(now, berlin, DayOfWeek.MONDAY))
    }

    private fun song(id: String, artist: String = "Artist $id", artistId: String? = "UC$id", album: String = "Album $id") =
        Track(id, "Song $id", artist, album, 200_000, Artwork(listOf(id.hashCode()), "https://example.org/$id.jpg"), artistId = artistId)

    private fun total(track: Track, plays: Int, minutes: Int, last: Long = 0) = SongTotal(track, plays, minutes * 60_000L, last)

    @Test
    fun `totals add up every song's plays and time heard`() {
        val stats = ListeningStats.of(listOf(total(song("a"), 3, 10), total(song("b"), 2, 7)), emptyList(), berlin)
        assertEquals(5, stats.plays)
        assertEquals(17 * 60_000L, stats.listenedMs)
    }

    @Test
    fun `top songs go by plays, then time heard, then the latest play`() {
        val a = total(song("a"), 4, 10)
        val b = total(song("b"), 5, 9)
        val c = total(song("c"), 4, 12)
        val d = total(song("d"), 4, 10, last = 99)
        val stats = ListeningStats.of(listOf(a, b, c, d), emptyList(), berlin)
        assertEquals(listOf("b", "c", "d", "a"), stats.topSongs.map { it.track.id })
    }

    @Test
    fun `only the top songs are kept`() {
        val many = (1..30).map { total(song("s$it"), it, it) }
        val stats = ListeningStats.of(many, emptyList(), berlin)
        assertEquals(ListeningStats.TOP_SONGS, stats.topSongs.size)
        assertEquals("s30", stats.topSongs.first().track.id)
    }

    @Test
    fun `an artist adds up their songs and shows their top song's cover`() {
        val hit = song("hit", artist = "Asha", artistId = "UCasha")
        val deepCut = song("cut", artist = "Asha", artistId = "UCasha")
        val other = song("other", artist = "Ravi", artistId = "UCravi")
        val stats = ListeningStats.of(listOf(total(deepCut, 2, 5), total(hit, 3, 6), total(other, 4, 8)), emptyList(), berlin)

        assertEquals(listOf("Asha", "Ravi"), stats.topArtists.map { it.name })
        val asha = stats.topArtists.first()
        assertEquals(5, asha.plays)
        assertEquals(11 * 60_000L, asha.listenedMs)
        assertEquals("UCasha", asha.artistId)
        assertEquals(hit.artwork, asha.artwork)
    }

    @Test
    fun `artists without a catalogue id are told apart by name`() {
        val stats = ListeningStats.of(
            listOf(total(song("a", artist = "Local", artistId = null), 1, 1), total(song("b", artist = "Local", artistId = null), 1, 1)),
            emptyList(),
            berlin,
        )
        assertEquals(listOf(ArtistTotal("Local", null, song("a").artwork, 2, 120_000)), stats.topArtists)
    }

    @Test
    fun `albums group by title and artist and skip songs without one`() {
        val one = song("one", artist = "Asha", album = "Monsoon")
        val two = song("two", artist = "Asha", album = "Monsoon")
        val single = song("single", artist = "Asha", album = "")
        val stats = ListeningStats.of(listOf(total(one, 1, 3), total(two, 2, 4), total(single, 9, 30)), emptyList(), berlin)
        assertEquals(listOf(AlbumTotal("Monsoon", "Asha", two.artwork, 3, 7 * 60_000L)), stats.topAlbums)
    }

    @Test
    fun `time heard is charted by the local hour each play started`() {
        val listens = listOf(
            TimedListen(ms(berlin, 2026, 3, 30, 7, 59), 60_000),
            TimedListen(ms(berlin, 2026, 3, 31, 7, 5), 30_000),
            TimedListen(ms(berlin, 2026, 3, 31, 23, 40), 45_000),
        )
        val byHour = ListeningStats.of(emptyList(), listens, berlin).listenedByHour
        assertEquals(24, byHour.size)
        assertEquals(90_000L, byHour[7])
        assertEquals(45_000L, byHour[23])
        assertEquals("New York sees the same plays six hours earlier", 90_000L, ListeningStats.of(emptyList(), listens, newYork).listenedByHour[1])
    }

    @Test
    fun `no plays is empty`() {
        val stats = ListeningStats.of(emptyList(), emptyList(), berlin)
        assertTrue(stats.isEmpty)
        assertEquals(List(24) { 0L }, stats.listenedByHour)
        assertEquals(null, stats.peakHour)
    }

    @Test
    fun `the peak hour is the one with the most time heard, the earlier on a tie`() {
        fun at(vararg hours: Int) = ListeningStats.of(emptyList(), hours.map { TimedListen(ms(berlin, 2026, 3, 31, it), 60_000) }, berlin).peakHour
        assertEquals(9, at(21, 9))
        assertEquals(21, at(9, 21, 21))
    }
}
