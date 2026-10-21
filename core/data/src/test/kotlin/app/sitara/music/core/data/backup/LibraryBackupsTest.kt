package app.sitara.music.core.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.sitara.music.core.data.SettingsCodec
import app.sitara.music.core.data.library.RoomLikedSongs
import app.sitara.music.core.data.library.RoomLyricsCache
import app.sitara.music.core.data.library.RoomPlayHistory
import app.sitara.music.core.data.library.RoomPlaylists
import app.sitara.music.core.data.library.RoomSavedCollections
import app.sitara.music.core.data.library.RoomSongGain
import app.sitara.music.core.data.library.SitaraDatabase
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.Listen
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.Track
import app.sitara.music.core.model.localTrackId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Backups written from one in-memory library and restored into another. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryBackupsTest {
    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SitaraDatabase::class.java).allowMainThreadQueries().build()

    private val source = database()
    private val target = database()
    private var now = 1_000L
    private val clock = { now++ }

    @After
    fun close() {
        source.close()
        target.close()
    }

    private fun song(n: Int) = Track(
        id = if (n % 10 == 0) localTrackId("content://media/external/audio/media/$n") else "id$n", title = "Song $n", artist = "Artist ${n % 7}",
        album = "Album ${n % 13}", durationMs = 180_000L + n, artwork = Artwork(listOf(0xFF9A7BFF.toInt(), -n), imageUrl = "https://example.org/$n.jpg".takeIf { n % 3 != 0 }),
        explicit = n % 5 == 0, artistId = "UC${n % 7}".takeIf { n % 2 == 0 },
    )

    /** The problem restoring [text] into the target is refused with. */
    private suspend fun refused(text: String): BackupProblem = try {
        LibraryBackups(target.backup()).restore(text)
        throw AssertionError("restored a backup that should have been refused")
    } catch (e: BackupException) {
        e.problem
    }

    private val settings = SitaraSettings(darkMode = DarkMode.LIGHT, crossfadeSeconds = 6, recentSearches = listOf("night drive", "lo-fi"))

    /** 1,000 liked songs, two playlists of the same name sharing songs, 1,500 plays with repeats, trims, lyric sources, saved albums and artists. */
    private suspend fun fill(db: SitaraDatabase) {
        val liked = RoomLikedSongs(db.likedSongs(), clock)
        (1..1_000).forEach { liked.setLiked(song(it), true) }
        val playlists = RoomPlaylists(db.playlists(), clock)
        val first = playlists.create("Road trip")
        (1..400).reversed().forEach { playlists.add(first, song(it)) }
        val second = playlists.create("Road trip")
        (300..600).forEach { playlists.add(second, song(it)) }
        playlists.create("Empty for now")
        val history = RoomPlayHistory(db.playHistory())
        (1..1_500).forEach { history.record(Listen(song(it % 250 + 1), startedAtMs = 10_000L * it, listenedMs = 30_000L + it)) }
        RoomSongGain(db.songGain()).apply { setTrimDb("id1", -4); setTrimDb("id2", 6) }
        RoomLyricsCache(db.lyricsCache()).apply { choose("id1", "youtube-music"); choose("id3", "lrclib") }
        RoomSavedCollections(db.saved(), clock).apply {
            setSaved(Album("OLAK5uy_1", "Paper Lanterns", "Orchid Lane", 2024, Artwork(listOf(1, 2), "https://example.org/a.jpg"), emptyList(), "UC1"), true)
            setSaved(Artist("UC1", "Orchid Lane", Artwork(listOf(3))), true)
        }
    }

    @Test
    fun `a 1,000 song library comes back whole, in its order, with its repeats`() = runTest {
        fill(source)
        val written = LibraryBackups(source.backup(), clock = { 7L }).export(SettingsCodec.encode(settings), appVersion = "0.10.0").text

        val restoredBackup = LibraryBackups(target.backup()).restore(written)
        val settingsBack = restoredBackup.settings

        assertEquals(SettingsCodec.encode(settings), settingsBack)
        assertEquals(BackupSummary(likedSongs = 1_000, playlists = 3, plays = 1_500), restoredBackup.summary)
        assertEquals("the restored library writes the very same backup", written, LibraryBackups(target.backup(), clock = { 7L }).export(settingsBack, appVersion = "0.10.0").text)
        assertEquals(RoomLikedSongs(source.likedSongs()).tracks.first(), RoomLikedSongs(target.likedSongs()).tracks.first())
        val restored = RoomPlaylists(target.playlists()).all.first()
        assertEquals(listOf("Empty for now", "Road trip", "Road trip"), restored.map { it.name })
        val firstTrip = RoomPlaylists(target.playlists()).playlist(restored.last().id).first()!!
        assertEquals((1..400).reversed().map(::song), firstTrip.tracks)
        assertEquals(RoomPlayHistory(source.playHistory()).counts.first().toSet(), RoomPlayHistory(target.playHistory()).counts.first().toSet())
        assertEquals(-4, RoomSongGain(target.songGain()).trimDb("id1").first())
        assertEquals("youtube-music", RoomLyricsCache(target.lyricsCache()).choice("id1"))
    }

    @Test
    fun `a backup names its format, schema version and the app that wrote it`() = runTest {
        val json = JSONObject(LibraryBackups(source.backup(), clock = { 7L }).export(emptyMap(), appVersion = "0.10.0").text)
        assertEquals("sitara-backup", json.getString("format"))
        assertEquals(LibraryBackups.SCHEMA_VERSION, json.getInt("schemaVersion"))
        assertEquals("0.10.0", json.getString("appVersion"))
        assertEquals(7L, json.getLong("createdAt"))
    }

    @Test
    fun `a truncated backup is refused and the library is left exactly as it was`() = runTest {
        fill(source)
        val written = LibraryBackups(source.backup()).export(emptyMap(), appVersion = "0.10.0").text
        RoomLikedSongs(target.likedSongs(), clock).setLiked(song(1), true)

        val problem = refused(written.take(written.length / 2))

        assertEquals(BackupProblem.DAMAGED, problem)
        assertEquals(listOf(song(1)), RoomLikedSongs(target.likedSongs()).tracks.first())
        assertEquals(emptyList<Any>(), RoomPlaylists(target.playlists()).all.first())
    }

    @Test
    fun `a song with a missing title anywhere fails the whole backup before anything is written`() = runTest {
        fill(source)
        val json = JSONObject(LibraryBackups(source.backup()).export(emptyMap(), appVersion = "0.10.0").text)
        json.getJSONArray("history").getJSONObject(1_499).getJSONObject("track").remove("title")
        RoomLikedSongs(target.likedSongs(), clock).setLiked(song(1), true)

        val problem = refused(json.toString())

        assertEquals(BackupProblem.DAMAGED, problem)
        assertEquals(listOf(song(1)), RoomLikedSongs(target.likedSongs()).tracks.first())
    }

    @Test
    fun `a file that is not a Sitara backup is said to be one`() = runTest {
        for (text in listOf("""{"playlists": []}""", "#EXTM3U\nsong.mp3", "")) {
            val problem = refused(text)
            assertEquals(text, BackupProblem.NOT_A_BACKUP, problem)
        }
    }

    @Test
    fun `a backup from a newer Sitara is refused rather than half read`() = runTest {
        val json = JSONObject(LibraryBackups(source.backup()).export(emptyMap(), appVersion = "0.10.0").text).put("schemaVersion", LibraryBackups.SCHEMA_VERSION + 1)
        val problem = refused(json.toString())
        assertEquals(BackupProblem.NEWER_VERSION, problem)
    }

    @Test
    fun `restoring replaces the library rather than merging into it`() = runTest {
        RoomLikedSongs(source.likedSongs(), clock).setLiked(song(2), true)
        val written = LibraryBackups(source.backup()).export(emptyMap(), appVersion = "0.10.0").text
        RoomLikedSongs(target.likedSongs(), clock).setLiked(song(1), true)
        RoomPlaylists(target.playlists(), clock).create("Only here")

        LibraryBackups(target.backup()).restore(written)

        assertEquals(listOf(song(2)), RoomLikedSongs(target.likedSongs()).tracks.first())
        assertEquals(emptyList<Any>(), RoomPlaylists(target.playlists()).all.first())
    }

    @Test
    fun `a backup from an older Sitara restores its plays without time heard and its old settings`() = runTest {
        // Written by hand as 0.9.4 would have kept it: plays without time heard, the AMOLED theme key from before Pure black.
        val old = """
            {"format": "sitara-backup", "schemaVersion": 1, "appVersion": "0.9.4", "createdAt": 5,
             "liked": [], "savedAlbums": [], "savedArtists": [], "playlists": [], "songVolume": [], "lyricsSource": [],
             "history": [{"track": {"id": "id9", "title": "Song 9", "artist": "Artist 2", "album": "Album 9", "durationMs": 180009,
                          "artworkColors": [1], "explicit": false}, "playedAt": 900, "listenedMs": null}],
             "settings": {"theme_mode": "AMOLED"}}
        """.trimIndent()

        val settingsBack = LibraryBackups(target.backup()).restore(old).settings

        assertTrue("AMOLED carries over to pure black", SettingsCodec.decode(settingsBack).pureBlack)
        assertEquals(listOf("Song 9"), RoomPlayHistory(target.playHistory()).recent(5).first().map { it.title })
        assertEquals("plays without time heard stay out of stats", 0, RoomPlayHistory(target.playHistory()).stats(0, java.time.ZoneOffset.UTC).first().plays)
    }

    @Test
    fun `a song listed twice in a playlist comes back once, as the library keeps it`() = runTest {
        val json = JSONObject(LibraryBackups(source.backup()).export(emptyMap(), appVersion = "0.10.0").text)
        val entry = JSONObject().put("track", JSONObject().put("id", "id1").put("title", "Song 1").put("artist", "A").put("album", "B").put("durationMs", 1).put("artworkColors", org.json.JSONArray().put(1)).put("explicit", false)).put("addedAt", 3)
        json.put("playlists", org.json.JSONArray().put(JSONObject().put("name", "Twice").put("createdAt", 1).put("updatedAt", 2).put("songs", org.json.JSONArray().put(entry).put(entry))))

        LibraryBackups(target.backup()).restore(json.toString())

        val restored = RoomPlaylists(target.playlists()).all.first().single()
        assertEquals(1, restored.songCount)
    }
}
