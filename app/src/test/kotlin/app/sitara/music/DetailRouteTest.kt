package app.sitara.music

import app.sitara.music.feature.library.LocalKind
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
            DetailRoute.LikedSongs,
            DetailRoute.UserPlaylist(7),
            DetailRoute.Mood("ggMPOg1uX2NXUkgxdW0zUHJp", "R&B | Soul: 90s"),
            DetailRoute.Stats,
            DetailRoute.Import,
            DetailRoute.Scrobbling,
            DetailRoute.OnThisPhone,
            DetailRoute.LocalGroup(LocalKind.ALBUMS, "orbit"),
            DetailRoute.LocalGroup(LocalKind.FOLDERS, "Music/Live | Bootlegs"),
            DetailRoute.Backup,
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
        assertNull(decodeDetailRoute("mine:not-a-number"))
        assertNull(decodeDetailRoute("mood:ggMPOg1uX2NXUkgxdW0zUHJp"))
        assertNull(decodeDetailRoute("mood:|Blues"))
        assertNull(decodeDetailRoute("localgroup:SONGS|x"))
        assertNull(decodeDetailRoute("localgroup:ALBUMS|"))
    }
}
