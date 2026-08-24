package app.sonicsound.subsonic

import android.net.Uri
import app.sonicsound.BuildConfig
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
import app.sonicsound.models.Playlist
import app.sonicsound.models.PlaylistResponse
import app.sonicsound.models.PlaylistsResponse
import app.sonicsound.models.RandomSongsResponse
import app.sonicsound.models.SearchResponse
import app.sonicsound.models.SearchResult
import app.sonicsound.models.SimilarSongsResponse
import app.sonicsound.models.Song
import app.sonicsound.models.SongResponse
import app.sonicsound.models.SubsonicResponse
import com.getcapacitor.JSObject
import okhttp3.Credentials.basic
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

/**
 * Remote Subsonic library API and Spotify artist-art helpers.
 */
class SubsonicLibrary(
    private val http: SubsonicHttp,
    private val coverCache: SubsonicCoverCache,
    private val accountProvider: () -> Account,
    private val paramsProvider: () -> HashMap<String, String>
) {
    private var spotifyToken: String = ""

    private val account: Account
        get() = accountProvider()

    private fun params(): HashMap<String, String> = paramsProvider()

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
        artistsResponse!!.artists.index.forEach { artistIndex ->
            ret.addAll(artistIndex.artist.map { artistItem ->
                Artist(artistItem.id, artistItem.name, artistItem.albumCount)
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
                )!!.albumList2.album
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
        )!!.albumList2.album
    }

    fun scrobble(id: String) {
        val p = params()
        p["id"] = id
        http.makeSubsonicRequest<SubsonicResponse>(listOf("rest", "scrobble"), p, true)
    }

    fun getRandomSongs(): List<Song> {
        val p = params()
        p["size"] = "10"
        return http.makeSubsonicRequest<RandomSongsResponse>(
            listOf("rest", "getRandomSongs"), p
        )!!.randomSongs.song
    }

    fun getSimilarSongs(id: String): List<Song> {
        val p = params()
        p["id"] = id
        return http.makeSubsonicRequest<SimilarSongsResponse>(
            listOf("rest", "getSimilarSongs2"), p
        )!!.similarSongs2.song
    }

    fun getSong(id: String): Song {
        val p = params()
        p["id"] = id
        return http.makeSubsonicRequest<SongResponse>(listOf("rest", "getSong"), p)!!.song
    }

    fun getPlaylist(id: String): Playlist {
        val p = params()
        p["id"] = id
        return http.makeSubsonicRequest<PlaylistResponse>(listOf("rest", "getPlaylist"), p)!!.playlist
    }

    fun getPlaylists(): List<Playlist> {
        return http.makeSubsonicRequest<PlaylistsResponse>(
            listOf("rest", "getPlaylists"), params()
        )!!.playlists.playlist
    }

    fun removePlaylist(id: String) {
        val p = params()
        p["id"] = id
        http.makeSubsonicRequest<PlaylistResponse>(listOf("rest", "deletePlaylist"), p, true)
    }

    fun removeFromPlaylist(id: String, track: Int) {
        val p = params()
        p["playlistId"] = id
        p["songIndexToRemove"] = track.toString()
        http.makeSubsonicRequest<PlaylistResponse>(listOf("rest", "updatePlaylist"), p, true)
    }

    fun addToPlaylist(id: String, songId: String) {
        val p = params()
        p["playlistId"] = id
        p["songIdToAdd"] = songId
        http.makeSubsonicRequest<PlaylistResponse>(listOf("rest", "updatePlaylist"), p, true)
    }

    fun updatePlaylist(playlist: Playlist): Playlist {
        val p = params()
        p["name"] = playlist.name
        p["comment"] = playlist.comment ?: ""
        p["public"] = if (playlist.public) "true" else "false"
        p["playlistId"] = playlist.id
        http.makeSubsonicRequest<PlaylistResponse>(listOf("rest", "updatePlaylist"), p, true)
        val songsParams = params()
        songsParams["playlistId"] = playlist.id
        songsParams["songId"] = playlist.entry.joinToString(",") { it.id }
        return http.makeSubsonicRequest<PlaylistResponse>(
            listOf("rest", "createPlaylist"), songsParams
        )!!.playlist
    }

    fun createPlaylist(ids: List<String>, name: String): Playlist {
        val songsParams = params()
        songsParams["name"] = name
        songsParams["songId"] = ids.joinToString(",")
        return http.makeSubsonicRequest<PlaylistResponse>(
            listOf("rest", "createPlaylist"), songsParams
        )!!.playlist
    }

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

    fun getSpotifyToken(): String {
        if (spotifyToken == "") {
            val clientId = BuildConfig.SPOTIFY_CLIENT_ID
            val clientSecret = BuildConfig.SPOTIFY_CLIENT_SECRET
            if (clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) {
                throw Exception("Spotify is not configured")
            }
            val uriBuilder = Uri.Builder()
                .scheme("https")
                .authority("accounts.spotify.com")
                .appendPath("api")
                .appendPath("token")
            val body: RequestBody = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .build()
            val request: Request = Request.Builder()
                .url(uriBuilder.build().toString())
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Authorization", basic(clientId, clientSecret))
                .post(body)
                .build()
            val response = http.execute(request)
            if (response.isSuccessful) {
                spotifyToken = JSObject(
                    response.body?.string() ?: "{\"access_token\": \"\"}"
                ).getString("access_token").toString()
            } else {
                throw Exception(response.message)
            }
        }
        return spotifyToken
    }

    fun getSpotifyArtistArt(name: String): String? {
        val uriBuilder = Uri.parse("https://api.spotify.com/v1/search").buildUpon()
        uriBuilder.appendQueryParameter("q", name)
        uriBuilder.appendQueryParameter("type", "artist")
        val request: Request = Request.Builder()
            .url(uriBuilder.build().toString())
            .get()
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer ${getSpotifyToken()}")
            .build()
        val response: Response = http.execute(request)
        if (!response.isSuccessful) throw Exception(response.message)
        val body = response.body?.string()
        val realResponse =
            JSObject(body).getJSObject("data")?.getJSObject("artists")?.getJSONArray("items")
                ?: return null
        val first = realResponse.getJSONObject(0) ?: return null
        if (first.getString("name") == name) {
            return first.getJSONArray("images").getJSONObject(0)?.getString("url")
        }
        return null
    }

    fun getArtistArt(id: String): String {
        val artist = getArtist(id)
        val art = getSpotifyArtistArt(artist.name) ?: getArtistInfo(id).largeImageUrl
        coverCache.cacheRemoteImage(
            art,
            coverCache.getArtistArtsDirectory(),
            coverCache.getLocalArtistArtUri(id)
        )
        return art
    }
}
