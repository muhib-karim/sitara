package app.sitara.music.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.sitara.music.core.data.library.SitaraDatabase
import app.sitara.music.core.model.LocalFolder
import app.sitara.music.core.model.LocalSong
import app.sitara.music.core.model.localTrackId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The local index against an in-memory database, with readers standing in for the phone's music and picked folders. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalIndexTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SitaraDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private var now = 1_000L

    /** What a reader lists: files, their tags, which files hold a cover, and whether reading is still allowed. */
    private class FakeReader(var files: List<AudioFile>, val tags: Map<String, AudioTags> = emptyMap(), val covers: Set<String> = emptySet()) : AudioReader {
        var revoked = false
        val tagsRead = mutableListOf<String>()

        override fun files(): List<AudioFile> = if (revoked) throw SecurityException("access revoked") else files

        override fun tags(file: AudioFile): AudioTags = tags.getValue(file.uri).also { tagsRead += file.uri }

        override fun art(file: AudioFile): ByteArray? = if (file.uri in covers) byteArrayOf(1, 2, 3) else null
    }

    private val savedArt = mutableListOf<String>()
    private var phone: AudioReader? = null
    private val folders = mutableMapOf<String, FakeReader>()
    private val index = LocalIndex(
        db.local(),
        phoneMusic = { phone },
        folderReader = { uri -> folders[uri] },
        saveArt = { key, _ -> savedArt += key; "file:///art/$key.jpg" },
        clock = { now++ },
    )

    private val tree = "content://com.android.externalstorage.documents/tree/primary%3AMusic"

    private fun doc(name: String, size: Long = 4_000, modified: Long = 10) =
        AudioFile("$tree/document/primary%3AMusic%2F$name", "Music/$name", size, modified)

    private fun store(n: Int, tags: AudioTags?, size: Long = 4_000, modified: Long = 10) =
        AudioFile("content://media/external/audio/media/$n", "Music/Store/$n.mp3", size, modified, tags)

    private val saffron = AudioTags("Saffron Morning", "Tara Lane", "Orbit", 61_000, 1)

    @After
    fun close() = db.close()

    @Test
    fun `tags give each song its title, artist, album, length and track number`() = runTest {
        phone = FakeReader(listOf(store(1, saffron)))
        index.rescan()
        val song = index.songs.first().single()
        assertEquals(localTrackId("content://media/external/audio/media/1"), song.track.id)
        assertEquals(listOf("Saffron Morning", "Tara Lane", "Orbit"), listOf(song.track.title, song.track.artist, song.track.album))
        assertEquals(61_000L, song.track.durationMs)
        assertEquals(1, song.trackNumber)
        assertNull(song.folder)
    }

    @Test
    fun `a file without tags is named after the file and an unknown artist and album`() = runTest {
        folders[tree] = FakeReader(listOf(doc("field recording 03.ogg")), tags = mapOf(doc("field recording 03.ogg").uri to AudioTags(" ", "<unknown>", null, 12_000, null)))
        index.addFolder(tree, "Music")
        val song = index.songs.first().single().track
        assertEquals(listOf("field recording 03", "Unknown artist", "Unknown album"), listOf(song.title, song.artist, song.album))
    }

    @Test
    fun `a cover is saved once per album and shared by its songs`() = runTest {
        val second = AudioTags("Second Light", "Tara Lane", "Orbit", 70_000, 2)
        phone = FakeReader(listOf(store(1, saffron), store(2, second)), covers = setOf(store(1, saffron).uri, store(2, second).uri))
        index.rescan()
        assertEquals(1, savedArt.size)
        assertEquals(setOf("file:///art/${savedArt.single()}.jpg"), index.songs.first().map { it.track.artwork.imageUrl }.toSet())
    }

    @Test
    fun `the same song in the phone's music and a picked folder is listed once, the phone's copy`() = runTest {
        phone = FakeReader(listOf(store(1, saffron)))
        index.rescan()
        folders[tree] = FakeReader(listOf(doc("Saffron Morning.mp3")), tags = mapOf(doc("Saffron Morning.mp3").uri to saffron.copy(title = "saffron morning", durationMs = 62_500)))
        index.addFolder(tree, "Music")
        assertEquals(listOf(localTrackId(store(1, saffron).uri)), index.songs.first().map { it.track.id })
    }

    @Test
    fun `songs with the same tags but clearly different lengths are both kept`() = runTest {
        phone = FakeReader(listOf(store(1, saffron), store(2, saffron.copy(durationMs = 300_000))))
        index.rescan()
        assertEquals(2, index.songs.first().size)
    }

    @Test
    fun `a rescan reads tags only of new and changed files and drops files that are gone`() = runTest {
        val reader = FakeReader(
            listOf(doc("a.mp3"), doc("b.mp3")),
            tags = mapOf(doc("a.mp3").uri to saffron, doc("b.mp3").uri to saffron.copy(title = "B"), doc("c.mp3").uri to saffron.copy(title = "C")),
        )
        folders[tree] = reader
        index.addFolder(tree, "Music")
        reader.tagsRead.clear()

        reader.files = listOf(doc("a.mp3", modified = 99), doc("c.mp3"))
        index.rescan()
        assertEquals(listOf(doc("a.mp3").uri, doc("c.mp3").uri), reader.tagsRead)
        assertEquals(setOf("Saffron Morning", "C"), index.songs.first().map { it.track.title }.toSet())
    }

    @Test
    fun `an unchanged song keeps when it was added`() = runTest {
        phone = FakeReader(listOf(store(1, saffron)))
        index.rescan()
        val added = index.songs.first().single().addedAt
        index.rescan()
        assertEquals(added, index.songs.first().single().addedAt)
    }

    @Test
    fun `a revoked folder keeps its songs and asks to be relinked`() = runTest {
        val reader = FakeReader(listOf(doc("a.mp3")), tags = mapOf(doc("a.mp3").uri to saffron))
        folders[tree] = reader
        index.addFolder(tree, "Music")

        reader.revoked = true
        index.rescan()
        assertEquals(1, index.songs.first().size)
        assertEquals(listOf(LocalFolder(tree, "Music", songCount = 1, linked = false)), index.folders.first())
    }

    @Test
    fun `a folder without a kept permission is not linked and loses nothing`() = runTest {
        folders[tree] = FakeReader(listOf(doc("a.mp3")), tags = mapOf(doc("a.mp3").uri to saffron))
        index.addFolder(tree, "Music")
        folders.remove(tree)
        index.rescan()
        assertEquals(listOf(LocalFolder(tree, "Music", songCount = 1, linked = false)), index.folders.first())
        assertEquals(1, index.songs.first().size)
    }

    @Test
    fun `relinking the folder reads it again`() = runTest {
        val reader = FakeReader(listOf(doc("a.mp3")), tags = mapOf(doc("a.mp3").uri to saffron, doc("b.mp3").uri to saffron.copy(title = "B")))
        folders[tree] = reader
        index.addFolder(tree, "Music")
        reader.revoked = true
        index.rescan()

        reader.revoked = false
        reader.files = listOf(doc("a.mp3"), doc("b.mp3"))
        index.addFolder(tree, "Music")
        assertEquals(listOf(LocalFolder(tree, "Music", songCount = 2, linked = true)), index.folders.first())
    }

    @Test
    fun `without permission for the phone's music its songs stay and picked folders still work`() = runTest {
        phone = FakeReader(listOf(store(1, saffron)))
        index.rescan()
        phone = null
        folders[tree] = FakeReader(listOf(doc("b.mp3")), tags = mapOf(doc("b.mp3").uri to saffron.copy(title = "B")))
        index.addFolder(tree, "Music")
        assertEquals(setOf("Saffron Morning", "B"), index.songs.first().map { it.track.title }.toSet())
    }

    @Test
    fun `removing a folder forgets its songs`() = runTest {
        folders[tree] = FakeReader(listOf(doc("a.mp3")), tags = mapOf(doc("a.mp3").uri to saffron))
        index.addFolder(tree, "Music")
        index.removeFolder(tree)
        assertEquals(emptyList<LocalSong>(), index.songs.first())
        assertEquals(emptyList<LocalFolder>(), index.folders.first())
    }

    @Test
    fun `songs come newest first`() = runTest {
        phone = FakeReader(listOf(store(1, saffron)))
        index.rescan()
        (phone as FakeReader).files = listOf(store(1, saffron), store(2, saffron.copy(title = "Later")))
        index.rescan()
        assertEquals(listOf("Later", "Saffron Morning"), index.songs.first().map { it.track.title })
    }
}
