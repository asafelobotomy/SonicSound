package app.sonicsound.subsonic

import app.sonicsound.models.Playlist
import app.sonicsound.models.PlaylistResponse
import app.sonicsound.models.PlaylistsResponse

/** Playlist CRUD helpers for [SubsonicLibrary]. */
internal class SubsonicPlaylists(
    private val http: SubsonicHttp,
    private val paramsProvider: () -> HashMap<String, String>,
) {
    private fun params(): HashMap<String, String> = paramsProvider()

    fun getPlaylist(id: String): Playlist {
        val p = params()
        p["id"] = id
        return http.makeSubsonicRequest<PlaylistResponse>(listOf("rest", "getPlaylist"), p)!!.playlist
    }

    fun getPlaylists(): List<Playlist> {
        return http.makeSubsonicRequest<PlaylistsResponse>(
            listOf("rest", "getPlaylists"), params()
        )?.playlists?.playlist.orEmpty()
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
        songsParams["songId"] = playlist.entry.orEmpty().joinToString(",") { it.id }
        return http.makeSubsonicRequest<PlaylistResponse>(
            listOf("rest", "createPlaylist"), songsParams
        )!!.playlist
    }

    fun renamePlaylist(id: String, name: String, comment: String?, public: Boolean) {
        val p = params()
        p["playlistId"] = id
        p["name"] = name
        p["comment"] = comment ?: ""
        p["public"] = if (public) "true" else "false"
        http.makeSubsonicRequest<PlaylistResponse>(listOf("rest", "updatePlaylist"), p, true)
    }

    fun createPlaylist(ids: List<String>, name: String): Playlist {
        val songsParams = params()
        songsParams["name"] = name
        songsParams["songId"] = ids.joinToString(",")
        return http.makeSubsonicRequest<PlaylistResponse>(
            listOf("rest", "createPlaylist"), songsParams
        )!!.playlist
    }
}
