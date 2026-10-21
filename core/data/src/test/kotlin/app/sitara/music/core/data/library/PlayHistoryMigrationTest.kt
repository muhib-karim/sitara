package app.sitara.music.core.data.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.sitara.music.core.model.Listen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The move from schema 3 (plays kept when they started) to 4 (plays with the time heard), with every play kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayHistoryMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-3-4.db"
    private var db: SitaraDatabase? = null

    @After
    fun close() {
        db?.close()
        context.deleteDatabase(name)
    }

    @Test
    fun `a version 3 history opens as version 4 with its plays kept and no time heard for them`() = runTest {
        context.deleteDatabase(name)
        createDatabaseAt(3, context.getDatabasePath(name)) { old ->
            listOf("a" to 100L, "b" to 200L, "a" to 300L).forEach { (id, at) ->
                old.execSQL(
                    "INSERT INTO play_history (played_at, track_id, track_title, track_artist, track_album, track_duration_ms, track_artwork_colors, track_artwork_url, track_explicit, track_artist_id) " +
                        "VALUES ($at, '$id', 'Song $id', 'Artist', 'Album', 200000, '1,2', NULL, 0, 'UC$id')",
                )
            }
        }

        // Room runs the 3 -> 4 migration and checks the result against the version 4 schema as it opens.
        val opened = Room.databaseBuilder(context, SitaraDatabase::class.java, name).allowMainThreadQueries().build().also { db = it }
        val history = RoomPlayHistory(opened.playHistory())

        assertEquals(listOf("a", "b"), history.recent(10).first().map { it.id })
        assertEquals(mapOf("a" to 2, "b" to 1), history.counts.first().associate { it.trackId to it.plays })
        assertEquals("plays from before heard time was kept have none", listOf(null, null, null), opened.listenedMs())

        val newer = history.recent(1).first().single().copy(id = "c")
        history.record(Listen(newer, startedAtMs = 400, listenedMs = 61_000))
        assertEquals(listOf(null, null, null, 61_000L), opened.listenedMs())
    }
}
