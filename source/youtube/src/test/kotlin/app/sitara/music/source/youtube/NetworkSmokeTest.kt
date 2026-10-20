package app.sitara.music.source.youtube

import java.net.InetAddress
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.abort
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException

/**
 * Live checks of the owner's acceptance track ("Niye Jabe Ki" by Xefer). Excluded from `test`; run with
 * `gradle :source:youtube:networkSmokeTest`. Skipped when YouTube cannot be reached, or when it answers this network's
 * IP with a "confirm you're not a bot" check (common on CI data-centre runners), so only real regressions fail.
 */
@Tag("network")
class NetworkSmokeTest {
    private val link = "https://music.youtube.com/watch?v=x7kGuhlXmFI"

    @BeforeEach
    fun requireNetwork() {
        assumeTrue(runCatching { InetAddress.getByName("music.youtube.com") }.isSuccess, "offline: YouTube Music is not resolvable")
        installNewPipe()
    }

    @Test
    fun `the acceptance link resolves to the song and a playable audio stream`() = runBlocking {
        val track = unlessBotChecked { YouTubeMusicSource().search(link) }.tracks.single()
        println("link -> ${track.id} | ${track.title} | ${track.artist} | ${track.durationMs} ms")
        assertEquals("x7kGuhlXmFI", track.id)
        assertTrue(track.title.contains("Niye Jabe Ki", ignoreCase = true), track.title)

        val stream = unlessBotChecked { YouTubeStreamResolver().resolve(track) }
        println("stream -> ${stream.mimeType} ${stream.bitrateKbps} kbps, expires ${stream.expiresAtMs}")
        assertTrue(stream.url.startsWith("https://"))
        assertTrue(stream.mimeType.startsWith("audio/"), stream.mimeType)
    }

    @Test
    fun `a songs search finds the acceptance track`() = runBlocking {
        val tracks = YouTubeMusicSource().search("Niye Jabe Ki Xefer").tracks
        tracks.take(5).forEach { println("search -> ${it.id} | ${it.title} | ${it.artist}") }
        assertTrue(
            tracks.any { it.title.contains("Niye Jabe Ki", ignoreCase = true) && it.artist.contains("Xefer", ignoreCase = true) },
            "no Niye Jabe Ki by Xefer among ${tracks.size} songs",
        )
    }

    private inline fun <T> unlessBotChecked(block: () -> T): T = try {
        block()
    } catch (e: SignInConfirmNotBotException) {
        abort("YouTube asked this network to confirm it is not a bot: ${e.message}")
    }
}
