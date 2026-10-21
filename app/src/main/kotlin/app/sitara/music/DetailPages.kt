package app.sitara.music

import app.sitara.music.feature.home.FeedState
import app.sitara.music.feature.library.DetailState
import kotlinx.coroutines.flow.StateFlow

/** Detail page states by route; the view model loads them, debug captures and tests supply fixtures. */
interface DetailPages {
    fun state(route: DetailRoute): StateFlow<DetailState>

    /** A mood or genre page, which is shelves like Home's. */
    fun moodPage(route: DetailRoute.Mood): StateFlow<FeedState>

    /** Loads a failed page, a mood page too, again. */
    fun retry(route: DetailRoute)
}
