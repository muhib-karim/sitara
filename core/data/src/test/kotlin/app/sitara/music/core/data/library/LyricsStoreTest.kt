package app.sitara.music.core.data.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.sitara.music.core.model.CachedLyrics
import app.sitara.music.core.model.LyricsSong
import app.sitara.music.core.model.LyricsTranslation
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Each lyrics source's answer per song, the source the listener picked, translations, and the move from schema 6 (LRCLIB's answers only) to 7. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LyricsStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val opened = mutableListOf<SitaraDatabase>()
    private val name = "migration-6-7.db"

    private fun inMemory() = Room.inMemoryDatabaseBuilder(context, SitaraDatabase::class.java).allowMainThreadQueries().build().also(opened::add)

    @After
    fun close() {
        opened.forEach(SitaraDatabase::close)
        context.deleteDatabase(name)
    }

    @Test
    fun `each source keeps its own answer for a song, with the song it matched and who licensed the lyrics`() = runTest {
        val store = RoomLyricsCache(inMemory().lyricsCache())
        val lrclib = CachedLyrics("[00:01.00]Paper lanterns", "Paper lanterns", 10, LyricsSong("Paper Lanterns", "Orchid Lane", 215_000))
        val youtube = CachedLyrics(null, "Paper lanterns", 20, LyricsSong("Paper Lanterns", "Orchid Lane", 214_000), licensor = "Musixmatch")

        store.put("a", "lrclib", lrclib)
        store.put("a", "youtube-music", youtube)

        assertEquals(lrclib, store.get("a", "lrclib"))
        assertEquals(youtube, store.get("a", "youtube-music"))
        assertNull(store.get("b", "lrclib"))
    }

    @Test
    fun `forgetting a song's misses drops only the sources that found nothing`() = runTest {
        val store = RoomLyricsCache(inMemory().lyricsCache())
        val found = CachedLyrics(null, "Paper lanterns", 10)
        store.put("a", "lrclib", CachedLyrics(null, null, 10))
        store.put("a", "youtube-music", found)
        store.put("b", "lrclib", CachedLyrics(null, null, 10))

        store.forgetMisses("a")

        assertNull(store.get("a", "lrclib"))
        assertEquals(found, store.get("a", "youtube-music"))
        assertEquals("other songs keep theirs", CachedLyrics(null, null, 10), store.get("b", "lrclib"))
    }

    @Test
    fun `the source picked for a song is kept, and a new pick replaces it`() = runTest {
        val store = RoomLyricsCache(inMemory().lyricsCache())
        assertNull(store.choice("a"))

        store.choose("a", "youtube-music")
        store.choose("a", "lrclib")
        store.choose("b", "youtube-music")

        assertEquals("lrclib", store.choice("a"))
        assertEquals("youtube-music", store.choice("b"))
    }

    @Test
    fun `a translation is kept per song, source and language, blank lines and all`() = runTest {
        val store = RoomLyricsCache(inMemory().lyricsCache())
        val english = LyricsTranslation(listOf("Paper lanterns", "", "Over the water"), fromLanguage = "es", toLanguage = "en")
        val bangla = LyricsTranslation(listOf("কাগজের লণ্ঠন", "", "জলের উপর"), fromLanguage = null, toLanguage = "bn")

        store.putTranslation("a", "lrclib", english)
        store.putTranslation("a", "lrclib", bangla)

        assertEquals(english, store.translation("a", "lrclib", "en"))
        assertEquals(bangla, store.translation("a", "lrclib", "bn"))
        assertNull(store.translation("a", "youtube-music", "en"))
    }

    @Test
    fun `lyrics cached by version 6 open in version 7 as LRCLIB's, with no matched song`() = runTest {
        context.deleteDatabase(name)
        createDatabaseAt(6, context.getDatabasePath(name)) { old ->
            old.execSQL("INSERT INTO lyrics_cache (track_id, synced, plain, fetched_at) VALUES ('a', '[00:01.00]Paper lanterns', 'Paper lanterns', 10)")
        }

        // Room runs the 6 -> 7 and 7 -> 8 migrations and checks the result against the current schema as it opens.
        val store = RoomLyricsCache(Room.databaseBuilder(context, SitaraDatabase::class.java, name).allowMainThreadQueries().build().also(opened::add).lyricsCache())

        assertEquals(CachedLyrics("[00:01.00]Paper lanterns", "Paper lanterns", 10), store.get("a", "lrclib"))
        assertNull(store.get("a", "youtube-music"))
    }
}
