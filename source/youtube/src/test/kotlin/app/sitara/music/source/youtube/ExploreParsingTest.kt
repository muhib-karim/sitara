package app.sitara.music.source.youtube

import app.sitara.music.core.model.HomeItem
import app.sitara.music.core.model.Mood
import app.sitara.music.core.model.ShelfLayout
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * YouTube Music search suggestions, Moods & genres and one genre's page, recorded signed out (GB, October 2026) and
 * trimmed: tracking removed, a few items per group. moods.json gains a "Charts" tile that opens the charts page, which
 * Sitara cannot show, made from a recorded tile.
 */
class ExploreParsingTest {
    private fun response(name: String): JsonObject = JsonParser.`object`().from(javaClass.getResourceAsStream("/explore/$name.json"))

    @Test
    fun `suggestions are the typed completions in order, without the songs and artists shown under them`() {
        assertEquals(
            listOf("lofi", "lofi girl", "lofi hip hop", "lofi study music", "lofi beats", "lofi music"),
            parseSuggestions(response("suggestions")),
        )
    }

    @Test
    fun `a response with no suggestions is an empty list`() {
        assertEquals(emptyList<String>(), parseSuggestions(JsonObject()))
    }

    @Test
    fun `moods and genres come in their groups, each tile with its colour and the params of its page`() {
        val sections = parseMoods(response("moods"))

        assertEquals(listOf("Moods & moments", "Genres"), sections.map { it.title })
        assertEquals(listOf("Chill", "Commute", "Energize", "Feel good"), sections[0].moods.map { it.title })
        assertEquals(Mood("Chill", 0xFFA4C5FF.toInt(), "ggMPOg1uX1JOQWZFeDByc2Jm"), sections[0].moods[0])
    }

    @Test
    fun `a tile that opens anything but a mood or genre page is left out`() {
        val genres = parseMoods(response("moods"))[1].moods

        assertEquals(listOf("African", "Arabic", "Blues", "Bollywood & Indian"), genres.map { it.title })
    }

    @Test
    fun `a genre page is shelves like Home's, songs as rows and playlists, videos and albums as cards`() {
        val page = parseHomePage(response("mood_page"))

        assertEquals(listOf("Songs", "Featured playlists", "Community playlists", "Music videos", "Albums"), page.shelves.map { it.title })
        assertEquals(ShelfLayout.SONG_ROWS, page.shelves[0].layout)
        assertEquals("I Lied to You", (page.shelves[0].items[1] as HomeItem.Song).track.title)
        val featured = page.shelves[1].items.map { (it as HomeItem.PlaylistCard).playlist }
        assertEquals("RDCLAK5uy_m-qCrzwr92eguM1Hqshp9oBI1Uf4u7SxE", featured.first().id)
        assertTrue(page.shelves[3].items.all { it is HomeItem.Song })
        assertEquals("OLAK5uy_n50t3PKfaJqlxRlIUFfUuDt3tXZ3EPeeU", (page.shelves[4].items.first() as HomeItem.AlbumCard).album.id)
    }
}
