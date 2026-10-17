package app.sitara.music.feature.home

import app.sitara.music.core.model.ListeningMood
import app.sitara.music.core.model.Shelf
import app.sitara.music.core.model.Track

internal fun quickPickPages(tracks: List<Track>): List<List<Track>> = tracks.chunked(4)

/** Mood membership belongs to the source's curated playlists, never title guessing. */
internal fun shelvesForMood(shelves: List<Shelf>, mood: ListeningMood?): List<Shelf> {
    if (mood == null) return shelves
    val playlists = shelves.flatMap { it.playlists }.filter { mood in it.moods }
    val trackIds = playlists.flatMap { it.tracks }.map { it.id }.toSet()
    return shelves.map { shelf ->
        shelf.copy(
            tracks = shelf.tracks.filter { it.id in trackIds },
            albums = emptyList(),
            playlists = shelf.playlists.filter { mood in it.moods },
        )
    }.filter { it.tracks.isNotEmpty() || it.playlists.isNotEmpty() }
}
