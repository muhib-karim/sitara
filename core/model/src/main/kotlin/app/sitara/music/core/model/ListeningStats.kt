package app.sitara.music.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** The stretch of listening the stats cover; each starts at local midnight and runs to now. */
enum class StatsRange(val label: String) {
    WEEK("This week"),
    MONTH("This month"),
    YEAR("This year"),
    ALL_TIME("All time"),
    ;

    /** Where the range starts in [zone], in ms since the epoch: the week's [firstDay], the 1st of the month, 1 January; 0 for all time. */
    fun startMs(nowMs: Long, zone: ZoneId, firstDay: DayOfWeek): Long {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val start = when (this) {
            WEEK -> today.with(TemporalAdjusters.previousOrSame(firstDay))
            MONTH -> today.withDayOfMonth(1)
            YEAR -> today.withDayOfYear(1)
            ALL_TIME -> return 0
        }
        return start.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}

/** One song's plays in a range, with its latest details. */
data class SongTotal(val track: Track, val plays: Int, val listenedMs: Long, val lastPlayedAt: Long)

/** An artist's plays in a range; [artwork] is their top song's cover. */
data class ArtistTotal(val name: String, val artistId: String?, val artwork: Artwork, val plays: Int, val listenedMs: Long)

/** An album's plays in a range, by title and artist; [artwork] is its top song's cover. */
data class AlbumTotal(val title: String, val artist: String, val artwork: Artwork, val plays: Int, val listenedMs: Long)

/** A play's start and time heard, for charting when the listening happens. */
data class TimedListen(val startedAtMs: Long, val listenedMs: Long)

/** What the listener played in a range: totals, the top songs, artists and albums, and time heard by the hour each play started (24 entries). */
data class ListeningStats(
    val plays: Int,
    val listenedMs: Long,
    val topSongs: List<SongTotal>,
    val topArtists: List<ArtistTotal>,
    val topAlbums: List<AlbumTotal>,
    val listenedByHour: List<Long>,
) {
    val isEmpty: Boolean get() = plays == 0

    /** The hour of the day with the most time heard, the earlier on a tie; null with nothing heard. */
    val peakHour: Int? get() = listenedByHour.indices.maxByOrNull { listenedByHour[it] }?.takeIf { listenedByHour[it] > 0 }

    companion object {
        const val TOP_SONGS = 10
        const val TOP_ARTISTS = 5
        const val TOP_ALBUMS = 5

        /** Most played first; ties go to more time heard, then the latest play, then the title. */
        private val songOrder = compareByDescending<SongTotal> { it.plays }.thenByDescending { it.listenedMs }.thenByDescending { it.lastPlayedAt }
            .thenBy { it.track.title }.thenBy { it.track.id }

        fun of(songs: List<SongTotal>, listens: List<TimedListen>, zone: ZoneId): ListeningStats {
            val ranked = songs.sortedWith(songOrder)
            // Grouping keeps the ranked order, so each group's first song is its top song.
            val artists = ranked.groupBy { it.track.artistId ?: it.track.artist }.values.map { group ->
                val top = group.first().track
                ArtistTotal(top.artist, top.artistId, top.artwork, group.sumOf { it.plays }, group.sumOf { it.listenedMs })
            }
            val albums = ranked.filter { it.track.album.isNotBlank() }.groupBy { it.track.album to it.track.artist }.values.map { group ->
                val top = group.first().track
                AlbumTotal(top.album, top.artist, top.artwork, group.sumOf { it.plays }, group.sumOf { it.listenedMs })
            }
            val byHour = LongArray(HOURS)
            listens.forEach { byHour[Instant.ofEpochMilli(it.startedAtMs).atZone(zone).hour] += it.listenedMs }
            return ListeningStats(
                plays = songs.sumOf { it.plays },
                listenedMs = songs.sumOf { it.listenedMs },
                topSongs = ranked.take(TOP_SONGS),
                topArtists = artists.sortedWith(compareByDescending<ArtistTotal> { it.plays }.thenByDescending { it.listenedMs }).take(TOP_ARTISTS),
                topAlbums = albums.sortedWith(compareByDescending<AlbumTotal> { it.plays }.thenByDescending { it.listenedMs }).take(TOP_ALBUMS),
                listenedByHour = byHour.toList(),
            )
        }

        private const val HOURS = 24
    }
}
