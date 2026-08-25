package app.sonicsound.library

import app.sonicsound.models.Album
import app.sonicsound.models.Artist

enum class AlbumSort {
    NAME_ASC,
    NAME_DESC,
    YEAR_ASC,
    YEAR_DESC,
    ARTIST_ASC,
}

enum class ArtistSort {
    NAME_ASC,
    NAME_DESC,
    ALBUMS_ASC,
    ALBUMS_DESC,
}

fun sortAlbums(albums: List<Album>, sort: AlbumSort): List<Album> = when (sort) {
    AlbumSort.NAME_ASC -> albums.sortedBy { it.name.lowercase() }
    AlbumSort.NAME_DESC -> albums.sortedByDescending { it.name.lowercase() }
    AlbumSort.YEAR_ASC -> albums.sortedBy { it.year }
    AlbumSort.YEAR_DESC -> albums.sortedByDescending { it.year }
    AlbumSort.ARTIST_ASC -> albums.sortedBy { it.artist.lowercase() }
}

fun sortArtists(artists: List<Artist>, sort: ArtistSort): List<Artist> = when (sort) {
    ArtistSort.NAME_ASC -> artists.sortedBy { it.name.lowercase() }
    ArtistSort.NAME_DESC -> artists.sortedByDescending { it.name.lowercase() }
    ArtistSort.ALBUMS_ASC -> artists.sortedBy { it.albumCount }
    ArtistSort.ALBUMS_DESC -> artists.sortedByDescending { it.albumCount }
}
