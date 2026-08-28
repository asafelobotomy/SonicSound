package app.sonicsound.models

data class GenreItem(val value: String, val songCount: Int = 0)

data class GenresResponse(val genres: GenresWrapper?)

data class GenresWrapper(val genre: List<GenreItem>?)

data class Starred2Response(val starred2: Starred2Wrapper?)

data class Starred2Wrapper(
    val artist: List<Artist>? = null,
    val album: List<Album>? = null,
    val song: List<Song>? = null,
)

data class SongsByGenreResponse(val songsByGenre: SongsWrapper?)

data class SongsWrapper(val song: List<Song>?)

data class OpenSubsonicExtensionsResponse(val openSubsonicExtensions: OpenSubsonicExtensionsWrapper?)

data class OpenSubsonicExtensionsWrapper(val extension: List<OpenSubsonicExtension>?)

data class OpenSubsonicExtension(val name: String?, val versions: List<Int>?)

data class ServerCapabilities(
    val playbackReport: Boolean = false,
    val sonicSimilarity: Boolean = false,
    val playQueue: Boolean = false,
)
