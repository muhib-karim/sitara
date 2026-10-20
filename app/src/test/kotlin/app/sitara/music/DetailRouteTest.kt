package app.sitara.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailRouteTest {
    @Test
    fun everyRouteSurvivesSavingWithItsKindAndId() {
        val routes = listOf(
            DetailRoute.Artist("UCabcdefghijklmnopqrstuv"),
            DetailRoute.Album("OLAK5uy_abc-1"),
            DetailRoute.Playlist("PLmix"),
            DetailRoute.Album("al-afterglow"),
        )

        assertEquals(routes, routes.map { decodeDetailRoute(it.encode()) })
    }

    @Test
    fun anIdMayContainTheSeparator() {
        assertEquals(DetailRoute.Playlist("a:b:c"), decodeDetailRoute(DetailRoute.Playlist("a:b:c").encode()))
    }

    @Test
    fun unknownKindsAndEmptyIdsAreDropped() {
        assertNull(decodeDetailRoute("video:x7kGuhlXmFI"))
        assertNull(decodeDetailRoute("artist:"))
        assertNull(decodeDetailRoute("artist"))
        assertNull(decodeDetailRoute(""))
    }
}
