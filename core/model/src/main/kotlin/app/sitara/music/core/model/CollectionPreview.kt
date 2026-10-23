package app.sitara.music.core.model

/** An album, playlist or artist a long press asked to preview: enough to draw the sheet's header before its songs load. */
data class CollectionPreview(val kind: Kind, val id: String, val title: String, val subtitle: String, val artwork: Artwork) {
    enum class Kind { ALBUM, PLAYLIST, ARTIST, OWN_PLAYLIST }
}
