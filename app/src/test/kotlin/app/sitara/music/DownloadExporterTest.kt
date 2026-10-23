package app.sitara.music

import app.sitara.music.core.data.library.LyricsCacheRepository
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.CachedLyrics
import app.sitara.music.core.model.DownloadExport
import app.sitara.music.core.model.ExportProblem
import app.sitara.music.core.model.LyricsTranslation
import app.sitara.music.core.model.Track
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadExporterTest {
    private fun fixture(name: String) = checkNotNull(javaClass.getResourceAsStream("/export/$name")).readBytes()

    private val opusSong = Track("a1", "Saffron Morning", "Sitara Ensemble", "Orbit", 2_000, Artwork(listOf(0)))
    private val aacSong = Track("b2", "Second Light", "Sitara Ensemble", "Orbit", 2_000, Artwork(listOf(0)))
    private val partSong = Track("c3", "Half There", "Someone", "", 2_000, Artwork(listOf(0)))

    private class Folder(existing: Set<String> = emptySet()) : ExportFolder {
        val files = existing.associateWith { ByteArray(1) }.toMutableMap()
        var writes = 0
        var broken = false

        override fun names(): Set<String> = if (broken) throw IOException("revoked") else files.keys

        override fun write(name: String, content: (OutputStream) -> Unit) {
            writes++
            files[name] = ByteArrayOutputStream().also(content).toByteArray()
        }
    }

    private class Lyrics(private val rows: Map<Pair<String, String>, CachedLyrics>, private val chosen: Map<String, String> = emptyMap()) : LyricsCacheRepository {
        override suspend fun get(trackId: String, provider: String) = rows[trackId to provider]
        override suspend fun put(trackId: String, provider: String, lyrics: CachedLyrics) = Unit
        override suspend fun forgetMisses(trackId: String) = Unit
        override suspend fun choice(trackId: String) = chosen[trackId]
        override suspend fun choose(trackId: String, provider: String) = Unit
        override suspend fun translation(trackId: String, provider: String, language: String): LyricsTranslation? = null
        override suspend fun putTranslation(trackId: String, provider: String, translation: LyricsTranslation) = Unit
    }

    private fun exporter(
        songs: List<Track> = listOf(opusSong, aacSong),
        files: Map<String, ByteArray> = mapOf("a1" to fixture("tone.webm"), "b2" to fixture("tone_frag.m4a")),
        lyrics: LyricsCacheRepository = Lyrics(emptyMap()),
    ) = DownloadExporter({ songs }, { files[it] }, lyrics, listOf("lrclib", "youtube-music"), { null })

    private fun text(bytes: ByteArray) = String(bytes, Charsets.ISO_8859_1)

    @Test
    fun `opus and aac downloads become tagged files with an lrc beside synced lyrics`() = runBlocking {
        val lyrics = Lyrics(mapOf(("a1" to "lrclib") to CachedLyrics("[00:01.00]First line", "First line", 0, null)))
        val folder = Folder()
        val done = exporter(lyrics = lyrics).export(folder) { _, _ -> }
        assertEquals(DownloadExport.Done(exported = 2, withLyrics = 1, untagged = 0, skipped = 0), done)
        assertEquals(
            setOf("Sitara Ensemble - Saffron Morning.opus", "Sitara Ensemble - Saffron Morning.lrc", "Sitara Ensemble - Second Light.m4a"),
            folder.files.keys,
        )
        val opus = folder.files.getValue("Sitara Ensemble - Saffron Morning.opus")
        assertTrue(text(opus).startsWith("OggS"))
        assertTrue(text(opus).contains("TITLE=Saffron Morning"))
        assertTrue(text(opus).contains("LYRICS=First line"))
        assertTrue(String(folder.files.getValue("Sitara Ensemble - Saffron Morning.lrc")).contains("[ti:Saffron Morning]\n"))
        assertTrue(String(folder.files.getValue("Sitara Ensemble - Second Light.m4a"), Charsets.UTF_8).contains("Second Light"))
    }

    @Test
    fun `a song not wholly downloaded is skipped, never written short`() = runBlocking {
        val folder = Folder()
        val done = exporter(songs = listOf(opusSong, partSong)).export(folder) { _, _ -> }
        assertEquals(DownloadExport.Done(exported = 1, withLyrics = 0, untagged = 0, skipped = 1), done)
        assertFalse(folder.files.keys.any { it.contains("Half There") })
    }

    @Test
    fun `exporting again replaces the files instead of adding copies`() = runBlocking {
        val folder = Folder(existing = setOf("Sitara Ensemble - Saffron Morning.opus"))
        exporter().export(folder) { _, _ -> }
        exporter().export(folder) { _, _ -> }
        assertEquals(2, folder.files.size)
        assertTrue(text(folder.files.getValue("Sitara Ensemble - Saffron Morning.opus")).startsWith("OggS"))
    }

    @Test
    fun `two songs of one name in one export both keep a file`() = runBlocking {
        val twin = opusSong.copy(id = "a2")
        val folder = Folder()
        exporter(songs = listOf(opusSong, twin), files = mapOf("a1" to fixture("tone.webm"), "a2" to fixture("tone.webm"))).export(folder) { _, _ -> }
        assertEquals(setOf("Sitara Ensemble - Saffron Morning.opus", "Sitara Ensemble - Saffron Morning (2).opus"), folder.files.keys)
    }

    @Test
    fun `plain lyrics go into the tag only, and the chosen source wins`() = runBlocking {
        val lyrics = Lyrics(
            mapOf(
                ("a1" to "lrclib") to CachedLyrics(null, "From LRCLIB", 0, null),
                ("a1" to "youtube-music") to CachedLyrics(null, "From YouTube Music", 0, null),
            ),
            chosen = mapOf("a1" to "youtube-music"),
        )
        val folder = Folder()
        val done = exporter(songs = listOf(opusSong), lyrics = lyrics).export(folder) { _, _ -> }
        assertEquals(0, (done as DownloadExport.Done).withLyrics)
        assertEquals(setOf("Sitara Ensemble - Saffron Morning.opus"), folder.files.keys)
        assertTrue(text(folder.files.values.single()).contains("LYRICS=From YouTube Music"))
    }

    @Test
    fun `a file of an unknown kind is skipped and webm that is not opus is copied untagged`() = runBlocking {
        val webm = fixture("tone.webm")
        val at = text(webm).indexOf("A_OPUS")
        val vorbis = webm.copyOf().also { "A_VORB".toByteArray().copyInto(it, at) }
        val folder = Folder()
        val done = exporter(files = mapOf("a1" to vorbis, "b2" to "ID3 not ours".toByteArray())).export(folder) { _, _ -> }
        assertEquals(DownloadExport.Done(exported = 1, withLyrics = 0, untagged = 1, skipped = 1), done)
        assertEquals(setOf("Sitara Ensemble - Saffron Morning.webm"), folder.files.keys)
    }

    @Test
    fun `a file the parsers choke on is copied untagged and the export goes on`() = runBlocking {
        // An MP4 whose moov claims more bytes than the file has: the box walk refuses it.
        val broken = fixture("tone_frag.m4a").copyOf(4_000)
        val folder = Folder()
        val done = exporter(files = mapOf("a1" to fixture("tone.webm"), "b2" to broken)).export(folder) { _, _ -> }
        assertEquals(DownloadExport.Done(exported = 2, withLyrics = 0, untagged = 1, skipped = 0), done)
        assertArrayEquals(broken, folder.files.getValue("Sitara Ensemble - Second Light.m4a"))
    }

    @Test
    fun `two songs whose names differ only in case or kind keep their own files and lyrics`() = runBlocking {
        val lower = opusSong.copy(id = "a2", title = "saffron morning", artist = "sitara ensemble")
        val asAac = opusSong.copy(id = "a3")
        val lyrics = Lyrics(
            mapOf(
                ("a1" to "lrclib") to CachedLyrics("[00:01.00]One", null, 0, null),
                ("a3" to "lrclib") to CachedLyrics("[00:01.00]Three", null, 0, null),
            ),
        )
        val folder = Folder()
        exporter(
            songs = listOf(opusSong, lower, asAac), files = mapOf("a1" to fixture("tone.webm"), "a2" to fixture("tone.webm"), "a3" to fixture("tone_frag.m4a")),
            lyrics = lyrics,
        ).export(folder) { _, _ -> }
        assertEquals(
            setOf(
                "Sitara Ensemble - Saffron Morning.opus", "Sitara Ensemble - Saffron Morning.lrc",
                "sitara ensemble - saffron morning (2).opus", "Sitara Ensemble - Saffron Morning (3).m4a", "Sitara Ensemble - Saffron Morning (3).lrc",
            ),
            folder.files.keys,
        )
    }

    @Test
    fun `progress counts every song`() = runBlocking {
        val seen = mutableListOf<Pair<Int, Int>>()
        exporter().export(Folder()) { done, total -> seen += done to total }
        assertEquals(listOf(0 to 2, 1 to 2, 2 to 2), seen)
    }

    @Test
    fun `nothing downloaded and a folder that cannot be used are worded`() = runBlocking {
        assertEquals(DownloadExport.Failed(ExportProblem.NOTHING_DOWNLOADED), exporter(songs = emptyList()).export(Folder()) { _, _ -> })
        val exports = DownloadExports(CoroutineScope(SupervisorJob() + Dispatchers.Default), exporter()) { Folder().apply { broken = true } }
        exports.export("content://tree")
        val end = withTimeout(5_000) { exports.state.first { it is DownloadExport.Failed } }
        assertEquals(DownloadExport.Failed(ExportProblem.FOLDER_UNWRITABLE), end)
    }

    @Test
    fun `one export at a time`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var runs = 0
        val slow = DownloadExporter({ runs++; gate.await(); listOf(opusSong) }, { fixture("tone.webm") }, Lyrics(emptyMap()), emptyList(), { null })
        val exports = DownloadExports(CoroutineScope(SupervisorJob() + Dispatchers.Default), slow) { Folder() }
        exports.export("content://tree")
        exports.export("content://tree")
        gate.complete(Unit)
        withTimeout(5_000) { exports.state.first { it is DownloadExport.Done } }
        assertEquals(1, runs)
    }
}
