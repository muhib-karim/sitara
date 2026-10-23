package app.sitara.music.core.data.beats

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.sitara.music.core.data.library.RoomLikedSongs
import app.sitara.music.core.data.library.SitaraDatabase
import app.sitara.music.core.data.library.createDatabaseAt
import app.sitara.music.core.model.BeatGrid
import app.sitara.music.core.model.RegionBeats
import app.sitara.music.core.model.SongBeats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Automix's analyses kept per song, side by side, and the move from schema 9 to 10 with everything kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SongBeatsStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val opened = mutableListOf<SitaraDatabase>()

    private fun inMemory() = Room.inMemoryDatabaseBuilder(context, SitaraDatabase::class.java).allowMainThreadQueries().build().also(opened::add)

    @After
    fun close() = opened.forEach(SitaraDatabase::close)

    private val intro = RegionBeats(BeatGrid(126.0, 1_480, 0.8f), -12f, 1_450, 30_000)
    private val outro = RegionBeats(BeatGrid(124.0, 210_321, 0.7f), -10f, 210_000, 239_000)
    private val beatless = RegionBeats(null, -20f, 0, 30_000)

    @Test
    fun `sides analysed at different times are kept together`() = runTest {
        val beats = SongBeatsRepository(inMemory().songBeats())
        beats.put(SongBeats("a", intro = intro), version = 1)
        beats.put(SongBeats("a", outro = outro), version = 1)
        assertEquals(SongBeats("a", intro, outro), beats.get("a", 1))
    }

    @Test
    fun `a side without a beat stays analysed, with no grid`() = runTest {
        val beats = SongBeatsRepository(inMemory().songBeats())
        beats.put(SongBeats("a", intro = beatless), version = 1)
        assertEquals(SongBeats("a", intro = beatless), beats.get("a", 1))
    }

    @Test
    fun `an analysis by another analyser version is not used or merged`() = runTest {
        val beats = SongBeatsRepository(inMemory().songBeats())
        beats.put(SongBeats("a", intro = intro), version = 1)
        assertNull(beats.get("a", 2))
        beats.put(SongBeats("a", outro = outro), version = 2)
        assertEquals(SongBeats("a", outro = outro), beats.get("a", 2))
    }

    @Test
    fun `a version 9 library opens as version 10 with its songs kept`() = runTest {
        val name = "migration-9-10.db"
        context.deleteDatabase(name)
        createDatabaseAt(9, context.getDatabasePath(name)) { db ->
            db.execSQL(
                "INSERT INTO liked_songs (liked_at, track_id, track_title, track_artist, track_album, track_duration_ms, track_artwork_colors, track_artwork_url, track_explicit, track_artist_id) " +
                    "VALUES (1, 'kept', 'Kept song', 'Artist', 'Album', 200000, '1,2', NULL, 0, NULL)",
            )
        }
        val db = Room.databaseBuilder(context, SitaraDatabase::class.java, name).allowMainThreadQueries().build().also(opened::add)
        assertEquals(listOf("Kept song"), RoomLikedSongs(db.likedSongs()).tracks.first().map { it.title })
        SongBeatsRepository(db.songBeats()).put(SongBeats("kept", intro = intro), version = 1)
        assertEquals(intro, SongBeatsRepository(db.songBeats()).get("kept", 1)?.intro)
    }

    @Test
    fun `a version 10 library with an unfinished import opens as version 11, the import not marked cut short`() = runTest {
        val name = "migration-10-11.db"
        context.deleteDatabase(name)
        createDatabaseAt(10, context.getDatabasePath(name)) { db ->
            db.execSQL("INSERT INTO import_jobs (id, source_key, name, playlist_id, finished, created_at) VALUES (1, 'spotify:playlist:x', 'Old', NULL, 0, 5)")
        }
        val db = Room.databaseBuilder(context, SitaraDatabase::class.java, name).allowMainThreadQueries().build().also(opened::add)
        val job = db.imports().job(1)!!
        assertEquals("Old", job.name)
        assertEquals(false, job.truncated)
    }
}
