package app.sitara.music.source.youtube

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Image.ResolutionLevel
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** Channel, release and song items are built by hand the way the extractor fills them for a YouTube Music artist. */
class ArtistMappingTest {
    private val channelId = "UCabcdefghijklmnopqrstuv"
    private val channelUrl = "https://www.youtube.com/channel/$channelId"

    private fun channel(name: String = "Xefer - Topic") = ChannelInfo(0, channelId, channelUrl, channelUrl, name).apply {
        avatars = listOf(
            Image("https://yt3.googleusercontent.com/avatar=s88-c-k-c0x00ffffff-no-rj", 88, 88, ResolutionLevel.LOW),
            Image("https://yt3.googleusercontent.com/avatar=s176-c-k-c0x00ffffff-no-rj", 176, 176, ResolutionLevel.MEDIUM),
        )
        banners = listOf(
            Image("https://yt3.googleusercontent.com/banner=w1060-fcrop64=1", 175, 1060, ResolutionLevel.MEDIUM),
            Image("https://yt3.googleusercontent.com/banner=w2560-fcrop64=1", 424, 2560, ResolutionLevel.HIGH),
        )
    }

    private fun song(videoId: String, artistUrl: String? = channelUrl, artist: String = "Xefer") =
        StreamInfoItem(0, "https://music.youtube.com/watch?v=$videoId", "Song $videoId", StreamType.VIDEO_STREAM).apply {
            uploaderName = artist
            uploaderUrl = artistUrl
            duration = 200
        }

    private fun release(listId: String, title: String = "Album $listId", uploader: String? = null) =
        PlaylistInfoItem(0, "https://www.youtube.com/playlist?list=$listId", title).apply {
            uploaderName = uploader
            setThumbnails(listOf(Image("https://i.ytimg.com/vi/x/hqdefault.jpg?sqp=a", 270, 480, ResolutionLevel.MEDIUM)))
        }

    @Test
    fun `maps the channel to an artist without the topic suffix`() {
        val page = artistPage(channel(), emptyList(), emptyList())

        assertEquals(channelId, page.artist.id)
        assertEquals("Xefer", page.artist.name)
    }

    @Test
    fun `upgrades the avatar to a large size and keeps the widest banner`() {
        val page = artistPage(channel(), emptyList(), emptyList())

        assertEquals("https://yt3.googleusercontent.com/avatar=s544-c-k-c0x00ffffff-no-rj", page.artist.artwork.imageUrl)
        assertEquals("https://yt3.googleusercontent.com/banner=w2560-fcrop64=1", page.bannerUrl)
    }

    @Test
    fun `a channel without images still has generated colours and no banner`() {
        val bare = ChannelInfo(0, channelId, channelUrl, channelUrl, "Xefer")

        val page = artistPage(bare, emptyList(), emptyList())

        assertNull(page.artist.artwork.imageUrl)
        assertNull(page.bannerUrl)
    }

    @Test
    fun `top songs keep only this artist's songs, once each, in search order`() {
        val songs = listOf(
            song("x7kGuhlXmFI"),
            song("aaaaaaaaaaa", artistUrl = "https://www.youtube.com/channel/UCzzzzzzzzzzzzzzzzzzzzzz", artist = "Someone else"),
            song("bbbbbbbbbbb"),
            song("x7kGuhlXmFI"),
            song("ccccccccccc", artistUrl = null),
        )

        val page = artistPage(channel(), emptyList(), songs)

        assertEquals(listOf("x7kGuhlXmFI", "bbbbbbbbbbb"), page.topSongs.map { it.id })
        assertEquals(channelId, page.topSongs.first().artistId)
    }

    @Test
    fun `releases become albums with their playlist id and the artist as fallback uploader`() {
        val releases = listOf(release("OLAK5uy_one"), release("OLAK5uy_two", uploader = "Xefer & Friends"), release("OLAK5uy_one"))

        val page = artistPage(channel(), releases, emptyList())

        assertEquals(listOf("OLAK5uy_one", "OLAK5uy_two"), page.releases.map { it.id })
        assertEquals(listOf("Xefer", "Xefer & Friends"), page.releases.map { it.artist })
        assertEquals("Album OLAK5uy_one", page.releases.first().title)
        assertEquals(channelId, page.releases.first().artistId)
        assertNull(page.releases.first().year)
        assertEquals("https://i.ytimg.com/vi/x/hqdefault.jpg?sqp=a", page.releases.first().artwork.imageUrl)
    }

    @Test
    fun `items that are not playlists or have no playlist id are not releases`() {
        val releases = listOf(song("x7kGuhlXmFI"), PlaylistInfoItem(0, "https://www.youtube.com/channel/$channelId", "Not a playlist"))

        assertEquals(emptyList<Any>(), artistPage(channel(), releases, emptyList()).releases)
    }

    @Test
    fun `reads channel and playlist ids from links`() {
        assertEquals(channelId, channelIdFromUrl(channelUrl))
        assertNull(channelIdFromUrl("https://www.youtube.com/@xefer"))
        assertNull(channelIdFromUrl(null))
        assertEquals("OLAK5uy_abc-1", playlistIdFromUrl("https://music.youtube.com/playlist?list=OLAK5uy_abc-1&si=x"))
        assertNull(playlistIdFromUrl("https://www.youtube.com/watch?v=x7kGuhlXmFI"))
    }

    @Test
    fun `only well-formed channel ids are accepted`() {
        assertTrue(isChannelId(channelId))
        assertFalse(isChannelId("UCshort"))
        assertFalse(isChannelId("../../etc/passwd"))
    }
}
