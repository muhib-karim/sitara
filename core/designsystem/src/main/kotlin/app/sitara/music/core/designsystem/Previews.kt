package app.sitara.music.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.CollectionPreview
import app.sitara.music.core.model.Playlist
import app.sitara.music.core.model.PlaylistSummary

/** Opens the long-press preview of an album, playlist or artist; the app shell provides it, screens call it from their tiles. */
val LocalCollectionPreview = staticCompositionLocalOf<(CollectionPreview) -> Unit> { {} }

fun Album.preview() = CollectionPreview(CollectionPreview.Kind.ALBUM, id, title, byline, artwork)

@Composable
fun Playlist.preview(): CollectionPreview {
    val fallback = stringResource(R.string.ds_playlist)
    return CollectionPreview(CollectionPreview.Kind.PLAYLIST, id, title, curator.ifEmpty { fallback }, artwork)
}

@Composable
fun Artist.preview() = CollectionPreview(CollectionPreview.Kind.ARTIST, id, name, stringResource(R.string.ds_artist), artwork)

@Composable
fun PlaylistSummary.preview() = CollectionPreview(CollectionPreview.Kind.OWN_PLAYLIST, id.toString(), name, stringResource(R.string.ds_your_playlist), covers.firstOrNull() ?: EmptyCollectionArtwork)

/** A long-press action that opens [preview]. */
@Composable
fun previewOnLongClick(preview: CollectionPreview): () -> Unit {
    val open = LocalCollectionPreview.current
    return { open(preview) }
}
