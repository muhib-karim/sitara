package app.sitara.music.source.youtube

import app.sitara.music.core.model.Mood
import app.sitara.music.core.model.MoodSection
import com.grack.nanojson.JsonObject

internal const val MOOD_PAGE_BROWSE_ID = "FEmusic_moods_and_genres_category"

/** A `music/get_search_suggestions` response as the completions it offers; the songs and artists listed under them are left out. */
internal fun parseSuggestions(response: JsonObject): List<String> =
    response.getArray("contents").filterIsInstance<JsonObject>()
        .flatMap { it.getObject("searchSuggestionsSectionRenderer").getArray("contents").filterIsInstance<JsonObject>() }
        .mapNotNull { it.getObject("searchSuggestionRenderer").getObject("navigationEndpoint").getObject("searchEndpoint").getString("query") }

/** The Moods & genres `browse` response; only tiles that open a mood or genre page (shelves like Home's) are kept. */
internal fun parseMoods(response: JsonObject): List<MoodSection> =
    (response.getObject("contents").getObject("singleColumnBrowseResultsRenderer").getArray("tabs").filterIsInstance<JsonObject>().firstOrNull() ?: JsonObject())
        .getObject("tabRenderer").getObject("content").getObject("sectionListRenderer").getArray("contents").filterIsInstance<JsonObject>()
        .mapNotNull { section ->
            val grid = section.getObject("gridRenderer")
            val title = grid.getObject("header").getObject("gridHeaderRenderer").getObject("title").getArray("runs")
                .filterIsInstance<JsonObject>().joinToString("") { it.getString("text").orEmpty() }
            val moods = grid.getArray("items").filterIsInstance<JsonObject>().mapNotNull { it.getObject("musicNavigationButtonRenderer").toMood() }
            MoodSection(title, moods).takeIf { title.isNotEmpty() && moods.isNotEmpty() }
        }

private fun JsonObject.toMood(): Mood? {
    val endpoint = getObject("clickCommand").getObject("browseEndpoint")
    if (endpoint.getString("browseId") != MOOD_PAGE_BROWSE_ID) return null
    val title = getObject("buttonText").getArray("runs").filterIsInstance<JsonObject>().firstOrNull()?.getString("text") ?: return null
    // An unsigned ARGB number; one without a colour gets none rather than a guess.
    val color = getObject("solid").getNumber("leftStripeColor")?.toLong()?.toInt() ?: return null
    return Mood(title, color, endpoint.getString("params") ?: return null)
}
