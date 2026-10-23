package app.sitara.music.source.spotify

import app.sitara.music.core.model.SourceSong
import app.sitara.music.core.model.SpotifyLink
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Spotify links and embed pages, with pages shaped like Spotify's (invented songs), so no test touches the network. */
class SpotifyTest {
    private val id = "37i9dQZF1DXcBWIGoYBM5M"

    private fun track(title: String, artists: String, duration: Long) =
        """{"uri":"spotify:track:x","title":"$title","subtitle":"$artists","duration":$duration,"isPlayable":true,"entityType":"track"}"""

    private fun page(type: String = "playlist", name: String = "Monsoon Evenings", tracks: List<String>, status: Int? = null): String {
        val props = if (status != null) {
            """{"status":$status,"title":"Page not found"}"""
        } else {
            """{"state":{"data":{"entity":{"type":"$type","name":"$name","trackList":[${tracks.joinToString(",")}]}}}}"""
        }
        return """<html><head></head><body><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":$props}}</script></body></html>"""
    }

    @Test
    fun `playlist and album links are read in their shared and app forms`() {
        val playlist = SpotifyLink(SpotifyLink.Kind.PLAYLIST, id)
        assertEquals(playlist, spotifyLinkFrom("https://open.spotify.com/playlist/$id"))
        assertEquals(playlist, spotifyLinkFrom("https://open.spotify.com/playlist/$id?si=abc123&pt=x"))
        assertEquals(playlist, spotifyLinkFrom("https://open.spotify.com/intl-de/playlist/$id"))
        assertEquals(playlist, spotifyLinkFrom("Listen with me: open.spotify.com/playlist/$id"))
        assertEquals(playlist, spotifyLinkFrom("spotify:playlist:$id"))
        assertEquals(SpotifyLink(SpotifyLink.Kind.ALBUM, "4aawyAB9vmqN3uQ7FjRGTy"), spotifyLinkFrom("https://open.spotify.com/album/4aawyAB9vmqN3uQ7FjRGTy"))
    }

    @Test
    fun `other links are not taken, and a song link is told apart`() {
        assertNull(spotifyLinkFrom("https://open.spotify.com/track/6OmhkSOpvYBokMKQxpIGx2"))
        assertNull(spotifyLinkFrom("https://example.org/playlist/$id"))
        assertNull(spotifyLinkFrom("https://open.spotify.com/playlist/short"))
        assertNull(spotifyLinkFrom("https://music.youtube.com/playlist?list=PL123"))
        assertTrue(isSpotifySongLink("https://open.spotify.com/track/6OmhkSOpvYBokMKQxpIGx2?si=x"))
        assertTrue(isSpotifySongLink("spotify:track:6OmhkSOpvYBokMKQxpIGx2"))
        assertFalse(isSpotifySongLink("https://open.spotify.com/playlist/$id"))
    }

    @Test
    fun `an embed page gives the list's name and songs with their artists and length`() {
        val list = parseEmbedPage(page(tracks = listOf(track("Rain on Tin", "Orchid Lane,\u00a0Lumen Drift", 214_000), track("Paper Boats", "Orchid Lane", 0))))
        assertEquals("Monsoon Evenings", list.name)
        assertEquals(
            listOf(SourceSong("Rain on Tin", "Orchid Lane, Lumen Drift", "", 214_000), SourceSong("Paper Boats", "Orchid Lane", "", null)),
            list.songs,
        )
        assertFalse(list.truncated)
    }

    @Test
    fun `an album page names the album on every song`() {
        val list = parseEmbedPage(page(type = "album", name = "Harbour Lights", tracks = listOf(track("Opening", "Orchid Lane", 100_000))))
        assertEquals("Harbour Lights", list.songs.single().album)
    }

    @Test
    fun `a page of a hundred songs says the list may go on`() {
        val list = parseEmbedPage(page(tracks = (1..100).map { track("Song $it", "Artist", 200_000) }))
        assertEquals(100, list.songs.size)
        assertTrue(list.truncated)
    }

    @Test
    fun `a missing or private list is not found, and a page without the data is unreadable`() {
        assertEquals(SpotifyException.Reason.NOT_FOUND, assertThrows<SpotifyException> { parseEmbedPage(page(tracks = emptyList(), status = 404)) }.reason)
        assertEquals(SpotifyException.Reason.UNREADABLE, assertThrows<SpotifyException> { parseEmbedPage("<html>new design</html>") }.reason)
        assertEquals(
            SpotifyException.Reason.UNREADABLE,
            assertThrows<SpotifyException> { parseEmbedPage("""<script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"state":{}}}}</script>""") }.reason,
        )
    }

    private val sent = mutableListOf<Request>()

    private fun reader(code: Int, body: String) = SpotifyEmbedReader(
        OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                sent += chain.request()
                okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("status $code").body(body.toResponseBody()).build()
            },
        ).build(),
    )

    @Test
    fun `the reader asks for the embed page of the list`() = runTest {
        reader(200, page(tracks = listOf(track("Rain on Tin", "Orchid Lane", 214_000)))).read(SpotifyLink(SpotifyLink.Kind.PLAYLIST, id))
        assertEquals("https://open.spotify.com/embed/playlist/$id", sent.single().url.toString())
    }

    @Test
    fun `a 404 is not found and a server error is a failure to reach Spotify`() = runTest {
        assertEquals(SpotifyException.Reason.NOT_FOUND, assertThrows<SpotifyException> { reader(404, "").read(SpotifyLink(SpotifyLink.Kind.ALBUM, id)) }.reason)
        assertThrows<IOException> { reader(503, "").read(SpotifyLink(SpotifyLink.Kind.ALBUM, id)) }
    }
}
