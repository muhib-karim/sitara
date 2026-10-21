package app.sitara.music

import app.sitara.music.core.model.StatsRange
import app.sitara.music.feature.home.FeedState
import app.sitara.music.feature.library.DetailState
import app.sitara.music.feature.library.StatsState
import kotlinx.coroutines.flow.StateFlow

/** Detail page states by route; the view model loads them, debug captures and tests supply fixtures. */
interface DetailPages {
    fun state(route: DetailRoute): StateFlow<DetailState>

    /** A mood or genre page, which is shelves like Home's. */
    fun moodPage(route: DetailRoute.Mood): StateFlow<FeedState>

    /** The listener's stats for [range], following new plays. */
    fun stats(range: StatsRange): StateFlow<StatsState>

    /** Loads a failed page, a mood page too, again. */
    fun retry(route: DetailRoute)
}
