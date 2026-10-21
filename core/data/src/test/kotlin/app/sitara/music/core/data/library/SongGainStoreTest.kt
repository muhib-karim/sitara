package app.sitara.music.core.data.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Per-song volume trims, and the move from schema 2 (before trims) to 3 with everything kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SongGainStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val opened = mutableListOf<SitaraDatabase>()

    private fun inMemory() = Room.inMemoryDatabaseBuilder(context, SitaraDatabase::class.java).allowMainThreadQueries().build().also(opened::add)

    @After
    fun close() = opened.forEach(SitaraDatabase::close)

    @Test
    fun `a song plays at 0 dB until a trim is set, and setting 0 forgets it`() = runTest {
        val gains = RoomSongGain(inMemory().songGain())
        assertEquals(0, gains.trimDb("a").first())

        gains.setTrimDb("a", -4)
        assertEquals(-4, gains.trimDb("a").first())
        assertEquals("other songs are untouched", 0, gains.trimDb("b").first())

        gains.setTrimDb("a", 0)
        assertEquals(0, gains.trimDb("a").first())
    }

    @Test
    fun `a trim stays within 6 dB either way`() = runTest {
        val gains = RoomSongGain(inMemory().songGain())
        gains.setTrimDb("a", 9)
        gains.setTrimDb("b", -20)
        assertEquals(6, gains.trimDb("a").first())
        assertEquals(-6, gains.trimDb("b").first())
    }

    @Test
    fun `a version 2 library opens as version 3 with its songs and lyrics kept`() = runTest {
        val name = "migration-2-3.db"
        context.deleteDatabase(name)
        createVersion2(context.getDatabasePath(name))

        // Room runs the 2 -> 3 migration and checks the result against the version 3 schema as it opens.
        val db = Room.databaseBuilder(context, SitaraDatabase::class.java, name).allowMainThreadQueries().build().also(opened::add)

        val liked = RoomLikedSongs(db.likedSongs()).tracks.first()
        assertEquals(listOf("Kept song"), liked.map { it.title })
        assertEquals("kept lyrics", RoomLyricsCache(db.lyricsCache()).get("kept", "lrclib")?.plain)
        val gains = RoomSongGain(db.songGain())
        gains.setTrimDb("kept", 3)
        assertEquals(3, gains.trimDb("kept").first())
    }

    /** A database exactly as version 2 left it, with a liked song and cached lyrics. */
    private fun createVersion2(file: File) = createDatabaseAt(2, file) { db ->
        db.execSQL(
            "INSERT INTO liked_songs (liked_at, track_id, track_title, track_artist, track_album, track_duration_ms, track_artwork_colors, track_artwork_url, track_explicit, track_artist_id) " +
                "VALUES (1, 'kept', 'Kept song', 'Artist', 'Album', 200000, '1,2', NULL, 0, NULL)",
        )
        db.execSQL("INSERT INTO lyrics_cache (track_id, synced, plain, fetched_at) VALUES ('kept', NULL, 'kept lyrics', 1)")
    }
}
