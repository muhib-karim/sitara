package app.sitara.music.core.data.imports

import app.sitara.music.core.model.SourceSong
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistFilesTest {
    @Test
    fun `an extended M3U gives each entry its artist, title and duration, skipping comments`() {
        val text = "﻿#EXTM3U\r\n#PLAYLIST:Road trip\r\n#EXTINF:354,Queen - Bohemian Rhapsody\r\nC:\\Music\\Queen\\Bohemian Rhapsody.mp3\r\n" +
            "# a note\r\n\r\n#EXTINF:-1,Intro\r\n/sdcard/Music/intro.flac\r\n"
        assertEquals(
            listOf(
                SourceSong(title = "Bohemian Rhapsody", artist = "Queen", durationMs = 354_000),
                SourceSong(title = "Intro", artist = ""),
            ),
            parseM3u(text),
        )
    }

    @Test
    fun `a plain M3U names songs after their files, and keeps web links`() {
        val text = "Music/Daft_Punk - One More Time.mp3\nfile:///storage/Music/Tame%20Impala%20-%20Elephant.m4a\rhttps://youtu.be/x7kGuhlXmFI\n"
        assertEquals(
            listOf(
                SourceSong(title = "One More Time", artist = "Daft Punk"),
                SourceSong(title = "Elephant", artist = "Tame Impala"),
                SourceSong(title = "", artist = "", link = "https://youtu.be/x7kGuhlXmFI"),
            ),
            parseM3u(text),
        )
    }

    @Test
    fun `an EXTINF title stays with the web link it describes`() {
        val text = "#EXTM3U\n#EXTINF:200 tvg-id=\"x\",Xefer - Niye Jabe Ki\nhttps://music.youtube.com/watch?v=x7kGuhlXmFI\n"
        assertEquals(
            listOf(SourceSong(title = "Niye Jabe Ki", artist = "Xefer", durationMs = 200_000, link = "https://music.youtube.com/watch?v=x7kGuhlXmFI")),
            parseM3u(text),
        )
    }

    @Test
    fun `a CSV with a header finds its columns by name, whatever their order`() {
        val text = "﻿Track URI,Track Name,Album Name,Artist Name(s),Duration (ms)\r\n" +
            "spotify:track:1,\"Hello, Goodbye\",\"Magical Mystery Tour\",The Beatles,208000\r\n" +
            "spotify:track:2,\"The \"\"Real\"\" Slim Shady\",The Marshall Mathers LP,Eminem,284200\r\n"
        assertEquals(
            listOf(
                SourceSong(title = "Hello, Goodbye", artist = "The Beatles", album = "Magical Mystery Tour", durationMs = 208_000),
                SourceSong(title = "The \"Real\" Slim Shady", artist = "Eminem", album = "The Marshall Mathers LP", durationMs = 284_200),
            ),
            parseCsv(text),
        )
    }

    @Test
    fun `a CSV without a header reads title, artist and album in that order`() {
        val text = "Bohemian Rhapsody,Queen,A Night at the Opera\n\nOne More Time,Daft Punk\n"
        assertEquals(
            listOf(
                SourceSong(title = "Bohemian Rhapsody", artist = "Queen", album = "A Night at the Opera"),
                SourceSong(title = "One More Time", artist = "Daft Punk"),
            ),
            parseCsv(text),
        )
    }

    @Test
    fun `quoted CSV fields may hold the delimiter and line breaks`() {
        val text = "title,artist\n\"Line one\nline two\",\"Simon, Garfunkel\"\n"
        assertEquals(listOf(SourceSong(title = "Line one\nline two", artist = "Simon, Garfunkel")), parseCsv(text))
    }

    @Test
    fun `semicolon and tab separated files are read too`() {
        assertEquals(listOf(SourceSong(title = "Elephant", artist = "Tame Impala")), parseCsv("Title;Artist\nElephant;Tame Impala\n"))
        assertEquals(listOf(SourceSong(title = "Elephant", artist = "Tame Impala")), parseCsv("Song\tArtist\nElephant\tTame Impala\n"))
    }

    @Test
    fun `durations are read as milliseconds, seconds or minutes and seconds, and a link column is kept`() {
        val text = "title,artist,length,url\nA,X,3:25,https://youtu.be/x7kGuhlXmFI\nB,Y,1:02:03,\nC,Z,95,\nD,W,soon,\n"
        assertEquals(
            listOf(
                SourceSong(title = "A", artist = "X", durationMs = 205_000, link = "https://youtu.be/x7kGuhlXmFI"),
                SourceSong(title = "B", artist = "Y", durationMs = 3_723_000),
                SourceSong(title = "C", artist = "Z", durationMs = 95_000),
                SourceSong(title = "D", artist = "W"),
            ),
            parseCsv(text),
        )
    }

    @Test
    fun `rows without a title or a link are left out`() {
        assertEquals(listOf(SourceSong(title = "Elephant", artist = "")), parseCsv("title,artist\n,Nobody\nElephant,\n  ,  \n"))
    }

    @Test
    fun `the file kind comes from its name, or from its first line when the name does not say`() {
        val m3u = "#EXTM3U\n#EXTINF:10,A - B\nb.mp3\n"
        assertEquals(listOf(SourceSong(title = "B", artist = "A", durationMs = 10_000)), parsePlaylistFile("mix.m3u8", m3u))
        assertEquals(listOf(SourceSong(title = "B", artist = "A", durationMs = 10_000)), parsePlaylistFile("download", m3u))
        assertEquals(listOf(SourceSong(title = "B", artist = "A")), parsePlaylistFile("export.CSV", "B,A\n"))
    }
}
