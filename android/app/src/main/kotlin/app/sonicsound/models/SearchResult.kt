@file:Suppress("unused")

package app.sonicsound.models


class SearchResult(
    val album: List<Album>?,
    val artist: List<ArtistListItem>?,
    val song: List<Song>?
)
