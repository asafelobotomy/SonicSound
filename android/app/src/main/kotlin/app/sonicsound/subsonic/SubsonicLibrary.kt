package app.sonicsound.subsonic

import android.net.Uri
import app.sonicsound.KeyValueStorage
import app.sonicsound.models.Account
import app.sonicsound.models.Album
import app.sonicsound.models.AlbumResponse
import app.sonicsound.models.AlbumWithSongs
import app.sonicsound.models.AlbumsResponse
import app.sonicsound.models.Artist
import app.sonicsound.models.ArtistInfo
import app.sonicsound.models.ArtistInfoResponse
import app.sonicsound.models.ArtistSubsonicResponse
import app.sonicsound.models.ArtistsSubsonicResponse
import app.sonicsound.models.InternetRadioStation
import app.sonicsound.models.InternetRadioStationsResponse
import app.sonicsound.models.LyricsResponse
import app.sonicsound.models.Playlist
import app.sonicsound.models.RandomSongsResponse
import app.sonicsound.models.GenreItem
import app.sonicsound.models.GenresResponse
import app.sonicsound.models.OpenSubsonicExtensionsResponse
import app.sonicsound.models.ServerCapabilities
import app.sonicsound.models.SongsByGenreResponse
import app.sonicsound.models.Starred2Response
import app.sonicsound.models.SearchResponse
import app.sonicsound.models.SearchResult
import app.sonicsound.models.SimilarSongsResponse
import app.sonicsound.models.Song
import app.sonicsound.models.SongResponse
import app.sonicsound.models.SubsonicResponse

/**
 * Remote Subsonic library API and Spotify artist-art helpers.
 */
class SubsonicLibrary(
    private val http: SubsonicHttp,
    private val coverCache: SubsonicCoverCache,
    private val accountProvider: () -> Account,
    private val paramsProvider: () -> HashMap<String, String>
) {
    private val account: Account
        get() = accountProvider()

    private fun params(): HashMap<String, String> = paramsProvider()

    private val playlists = SubsonicPlaylists(http, paramsProvider)
    private val artistArt = SubsonicArtistArt(
        http = http,
        coverCache = coverCache,
        getArtist = { getArtist(it) },
        getArtistInfo = { getArtistInfo(it) },
        getSpotifyToken = { getSpotifyToken() },
    )

    fun search(query: String): SearchResult {
        val p = params()
        p["query"] = query
        return http.makeSubsonicRequest<SearchResponse>(listOf("rest", "search3"), p)!!.searchResult3
    }

    fun getArtists(): List<Artist> {
        val artistsResponse = http.makeSubsonicRequest<ArtistsSubsonicResponse>(
            listOf("rest", "getArtists"),
            params()
        )
        val ret: MutableList<Artist> = mutableListOf()
        artistsResponse?.artists?.index.orEmpty().forEach { artistIndex ->
            ret.addAll(artistIndex.artist.orEmpty().map { artistItem ->
                Artist(artistItem.id, artistItem.name, artistItem.albumCount).also { artist ->
                    val http = artistItem.artistImageUrl?.takeIf {
                        it.startsWith("http", ignoreCase = true)
                    }
                    artist.coverArt = http
                        ?: artistItem.coverArt?.takeIf { it.isNotBlank() }
                        ?: artistItem.artistImageUrl.orEmpty()
                }
            })
        }
        return ret
    }

    fun getArtist(id: String): Artist {
        val p = params()
        p["id"] = id
        return http.makeSubsonicRequest<ArtistSubsonicResponse>(
            listOf("rest", "getArtist"), p
        )!!.artist
    }

    fun getAlbums(): List<Album> {
        var page = 0
        var more = true
        val ret = mutableListOf<Album>()
        while (more) {
            val p = params()
            p["type"] = "alphabeticalByName"
            p["size"] = "500"
            p["offset"] = (page * 500).toString()
            ret.addAll(
                http.makeSubsonicRequest<AlbumsResponse>(
                    listOf("rest", "getAlbumList2"), p
                )?.albumList2?.album.orEmpty()
            )
            if (ret.size % 500 != 0) more = false
            page++
        }
        return ret
    }

    fun getAlbum(id: String): AlbumWithSongs {
        val p = params()
        p["id"] = id
        return http.makeSubsonicRequest<AlbumResponse>(listOf("rest", "getAlbum"), p)!!.album
    }

    fun getArtistInfo(id: String): ArtistInfo {
        val p = params()
        p["id"] = id
        return http.makeSubsonicRequest<ArtistInfoResponse>(
            listOf("rest", "getArtistInfo2"), p
        )!!.artistInfo2
    }

    fun getTopAlbums(type: String = "frequent", size: Int = 10): List<Album> {
        val p = params()
        p["type"] = type
        p["size"] = size.toString()
        return http.makeSubsonicRequest<AlbumsResponse>(
            listOf("rest", "getAlbumList2"), p
        )?.albumList2?.album.orEmpty()
    }

    fun scrobble(id: String) {
        val p = params()
        p["id"] = id
        http.makeSubsonicRequest<SubsonicResponse>(listOf("rest", "scrobble"), p, true)
    }

    fun getRandomSongs(): List<Song> = getRandomSongsFiltered(10)

    fun getRandomSongsFiltered(
        size: Int = 50,
        genre: String? = null,
        fromYear: Int? = null,
        toYear: Int? = null,
        musicFolderId: String? = null,
    ): List<Song> {
        val p = params()
        p["size"] = size.coerceIn(1, 500).toString()
        if (!genre.isNullOrBlank()) p["genre"] = genre
        if (fromYear != null && fromYear > 0) p["fromYear"] = fromYear.toString()
        if (toYear != null && toYear > 0) p["toYear"] = toYear.toString()
        if (!musicFolderId.isNullOrBlank()) p["musicFolderId"] = musicFolderId
        return http.makeSubsonicRequest<RandomSongsResponse>(
            listOf("rest", "getRandomSongs"), p
        )?.randomSongs?.song.orEmpty()
    }

    fun getGenres(): List<GenreItem> {
        return http.makeSubsonicRequest<GenresResponse>(
            listOf("rest", "getGenres"), params()
        )?.genres?.genre.orEmpty()
    }

    fun getSongsByGenre(genre: String, count: Int = 50, offset: Int = 0): List<Song> {
        val p = params()
        p["genre"] = genre
        p["count"] = count.coerceIn(1, 500).toString()
        p["offset"] = offset.coerceAtLeast(0).toString()
        return http.makeSubsonicRequest<SongsByGenreResponse>(
            listOf("rest", "getSongsByGenre"), p
        )?.songsByGenre?.song.orEmpty()
    }

    fun getStarred2Songs(): List<Song> {
        val starred = http.makeSubsonicRequest<Starred2Response>(
            listOf("rest", "getStarred2"), params()
        )?.starred2
        val songs = starred?.song.orEmpty().toMutableList()
        if (songs.isEmpty()) {
            starred?.album.orEmpty().forEach { album ->
                try {
                    songs.addAll(getAlbum(album.id).song.orEmpty())
                } catch (_: Exception) {
                    // skip album
                }
            }
        }
        return songs
    }

    fun getOpenSubsonicExtensions(): ServerCapabilities {
        val response = http.makeSubsonicRequest<OpenSubsonicExtensionsResponse>(
            listOf("rest", "getOpenSubsonicExtensions"), params()
        )
        val names = response?.openSubsonicExtensions?.extension.orEmpty()
            .mapNotNull { it.name?.lowercase() }
            .toSet()
        return ServerCapabilities(
            playbackReport = "playbackreport" in names,
            sonicSimilarity = "sonicsimilarity" in names,
            playQueue = true,
        )
    }

    fun reportPlayback(
        mediaId: String,
        positionMs: Long,
        state: String,
        playbackRate: Float = 1f,
    ) {
        val p = params()
        p["mediaId"] = mediaId
        p["mediaType"] = "song"
        p["positionMs"] = positionMs.toString()
        p["state"] = state
        p["playbackRate"] = playbackRate.toString()
        http.makeSubsonicRequest<SubsonicResponse>(listOf("rest", "reportPlayback"), p, true)
    }

    fun savePlayQueue(songIds: List<String>, currentId: String?, positionMs: Long = 0) {
        val p = params()
        songIds.forEach { p["id"] = it }
        if (!currentId.isNullOrBlank()) {
            p["current"] = currentId
            p["position"] = positionMs.toString()
        }
        http.makeSubsonicRequest<SubsonicResponse>(listOf("rest", "savePlayQueue"), p, true)
    }

    fun getSimilarSongs(id: String): List<Song> {
        val p = params()
        p["id"] = id
        return http.makeSubsonicRequest<SimilarSongsResponse>(
            listOf("rest", "getSimilarSongs2"), p
        )?.similarSongs2?.song.orEmpty()
    }

    fun getSong(id: String): Song {
        val p = params()
        p["id"] = id
        return http.makeSubsonicRequest<SongResponse>(listOf("rest", "getSong"), p)!!.song
    }

    fun getPlaylist(id: String): Playlist = playlists.getPlaylist(id)
    fun getPlaylists(): List<Playlist> = playlists.getPlaylists()
    fun removePlaylist(id: String) = playlists.removePlaylist(id)
    fun removeFromPlaylist(id: String, track: Int) = playlists.removeFromPlaylist(id, track)
    fun addToPlaylist(id: String, songId: String) = playlists.addToPlaylist(id, songId)
    fun updatePlaylist(playlist: Playlist): Playlist = playlists.updatePlaylist(playlist)
    fun renamePlaylist(id: String, name: String, comment: String?, public: Boolean) =
        playlists.renamePlaylist(id, name, comment, public)
    fun createPlaylist(ids: List<String>, name: String): Playlist =
        playlists.createPlaylist(ids, name)

    fun getSongUri(song: Song?, connectivityMetered: Boolean): String? {
        if (song == null) return null
        val uriBuilder = Uri.parse(account.url).buildUpon()
            .appendPath("rest")
            .appendPath("stream")
        for ((key, value) in params()) {
            uriBuilder.appendQueryParameter(key, value)
        }
        uriBuilder.appendQueryParameter("id", song.id)
        uriBuilder.appendQueryParameter("estimateContentLength", "true")
        if (KeyValueStorage.getSettings().transcoding != "" && connectivityMetered) {
            uriBuilder.appendQueryParameter("format", KeyValueStorage.getSettings().transcoding)
        }
        return uriBuilder.build().toString()
    }

    /**
     * Client-credentials Spotify tokens require a secret that must not ship in the APK.
     * Artist-art callers wrap this in runCatching; similarity stays off until a proxy exists.
     */
    fun getSpotifyToken(): String {
        throw Exception(
            "Spotify similarity requires a server-side token proxy; " +
                "client secrets are not shipped in the APK",
        )
    }

    fun getSpotifyArtistArt(name: String): String? = artistArt.getSpotifyArtistArt(name)
    fun getArtistArt(id: String): String = artistArt.getArtistArt(id)

    fun getLyrics(artist: String, title: String): String {
        val p = params()
        p["artist"] = artist
        p["title"] = title
        val response = http.makeSubsonicRequest<LyricsResponse>(
            listOf("rest", "getLyrics"),
            p
        )
        return response?.lyrics?.value ?: ""
    }

    fun getInternetRadioStations(): List<InternetRadioStation> {
        return http.makeSubsonicRequest<InternetRadioStationsResponse>(
            listOf("rest", "getInternetRadioStations"),
            params()
        )?.internetRadioStations?.internetRadioStation.orEmpty()
    }

    fun createInternetRadioStation(name: String, streamUrl: String, homePageUrl: String?) {
        val p = params()
        p["name"] = name
        p["streamUrl"] = streamUrl
        homePageUrl?.takeIf { it.isNotBlank() }?.let { p["homepageUrl"] = it }
        http.makeSubsonicRequest<SubsonicResponse>(
            listOf("rest", "createInternetRadioStation"), p, true
        )
    }

    fun updateInternetRadioStation(id: String, name: String, streamUrl: String, homePageUrl: String?) {
        val p = params()
        p["id"] = id
        p["name"] = name
        p["streamUrl"] = streamUrl
        homePageUrl?.takeIf { it.isNotBlank() }?.let { p["homepageUrl"] = it }
        http.makeSubsonicRequest<SubsonicResponse>(
            listOf("rest", "updateInternetRadioStation"), p, true
        )
    }

    fun deleteInternetRadioStation(id: String) {
        val p = params()
        p["id"] = id
        http.makeSubsonicRequest<SubsonicResponse>(
            listOf("rest", "deleteInternetRadioStation"), p, true
        )
    }
}
