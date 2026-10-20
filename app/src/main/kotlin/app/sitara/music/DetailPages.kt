package app.sitara.music

import app.sitara.music.feature.library.DetailState
import kotlinx.coroutines.flow.StateFlow

/** Detail page states by route; the view model loads them, debug captures and tests supply fixtures. */
interface DetailPages {
    fun state(route: DetailRoute): StateFlow<DetailState>

    fun retry(route: DetailRoute)
}
