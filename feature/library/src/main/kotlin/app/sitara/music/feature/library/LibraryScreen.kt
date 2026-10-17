package app.sitara.music.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.LargeTitle
import app.sitara.music.core.designsystem.TrackRow
import app.sitara.music.core.model.Library
import app.sitara.music.core.model.Track

enum class LibraryFilter(val label: String) { PLAYLISTS("Playlists"), ALBUMS("Albums"), ARTISTS("Artists"), SONGS("Liked songs") }

@Composable
fun LibraryScreen(
    library: Library,
    onPlay: (tracks: List<Track>, index: Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    initialFilter: LibraryFilter = LibraryFilter.PLAYLISTS,
) {
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { LargeTitle("Library") }
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryFilter.entries.forEach { f ->
                    FilterChip(selected = f == filter, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
        }
        when (filter) {
            LibraryFilter.PLAYLISTS -> items(library.playlists, key = { it.id }) { p ->
                TrackRow(p.tracks.first().copy(title = p.title, artist = "${p.tracks.size} songs · ${p.description}", artwork = p.artwork), onClick = { onPlay(p.tracks, 0) })
            }
            LibraryFilter.ALBUMS -> items(library.albums, key = { it.id }) { a ->
                TrackRow(a.tracks.first().copy(title = a.title, artist = "${a.artist} · ${a.year}", artwork = a.artwork), onClick = { onPlay(a.tracks, 0) })
            }
            LibraryFilter.ARTISTS -> items(library.artists, key = { it.id }) { ar ->
                TrackRow(Track(ar.id, ar.name, "Artist", "", 0, ar.artwork), onClick = {})
            }
            LibraryFilter.SONGS -> items(library.likedTracks, key = { it.id }) { t ->
                TrackRow(t, onClick = { onPlay(library.likedTracks, library.likedTracks.indexOf(t)) })
            }
        }
    }
}
