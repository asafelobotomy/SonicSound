package app.sonicsound.plugins

import app.sonicsound.App.Companion.isTv
import app.sonicsound.KeyValueStorage.Companion.getOfflineMode
import app.sonicsound.models.Album
import app.sonicsound.models.ParameterException
import app.sonicsound.models.Playlist
import app.sonicsound.models.Song
import app.sonicsound.subsonic.SubsonicClient
import com.getcapacitor.JSArray
import com.getcapacitor.PluginCall
import com.google.gson.Gson

/** Library browse/search/playlist/art operations for BackendPlugin. */
class BackendLibrary(
    private val responses: BackendResponses,
    private val clientProvider: () -> SubsonicClient?,
    private val gsonProvider: () -> Gson
) {
    private val client: SubsonicClient get() = clientProvider()!!
    private val gson: Gson get() = gsonProvider()

    fun getTopAlbums(call: PluginCall) {
        var type = call.getString("type") ?: "frequent"
        var size = call.getInt("size") ?: 10
        try {
            if (getOfflineMode()) {
                if (type == "newest") {
                    call.resolve(responses.okArray(client.getLocalAlbums(10, true)))
                } else {
                    call.resolve(responses.okArray(ArrayList<Album>()))
                }
            } else {
                call.resolve(responses.okArray(client.getTopAlbums(type, size)))
            }
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun getAlbums(call: PluginCall) {
        try {
            if (getOfflineMode()) {
                call.resolve(responses.okArray(client.getLocalAlbums(1000000, false)))
            } else {
                call.resolve(responses.okArray(client.getAlbums()))
            }
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun getAlbum(call: PluginCall) {
        try {
            val id = call.getString("id") ?: throw ParameterException("id")
            if (getOfflineMode()) {
                call.resolve(responses.ok(client.getLocalAlbumWithSongs(id)))
            } else {
                call.resolve(responses.ok(client.getAlbum(id)))
            }
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun getArtists(call: PluginCall) {
        try {
            if (getOfflineMode()) {
                call.resolve(responses.okArray(client.getLocalArtists()))
            } else {
                call.resolve(responses.okArray(client.getArtists()))
            }
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun getArtist(call: PluginCall) {
        try {
            val id = call.getString("id") ?: throw ParameterException("id")
            if (getOfflineMode()) {
                call.resolve(responses.ok(client.getLocalArtistWithAlbums(id)))
            } else {
                call.resolve(responses.ok(client.getArtist(id)))
            }
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun getRandomSongs(call: PluginCall) {
        try {
            if (getOfflineMode()) {
                call.resolve(responses.okArray(object : ArrayList<Song?>() {}))
            } else {
                call.resolve(responses.okArray(client.getRandomSongs()))
            }
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun getAlbumArt(call: PluginCall) {
        try {
            val id = call.getString("id") ?: ""
            if (getOfflineMode()) {
                call.resolve(responses.ok(client.getLocalAlbumArt(id)))
            } else {
                call.resolve(responses.ok(client.getAlbumArt(id)))
            }
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun getArtistArt(call: PluginCall) {
        try {
            val id = call.getString("id") ?: throw ParameterException("id")
            if (getOfflineMode()) {
                call.resolve(responses.ok(client.getLocalArtistArt(id)))
            } else {
                call.resolve(responses.ok(client.getArtistArt(id)))
            }
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun search(call: PluginCall) {
        try {
            val query = call.getString("query") ?: throw ParameterException("query")
            call.resolve(responses.ok(client.search(query)))
            return
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
        call.resolve(responses.ok(""))
    }

    fun getSongStatus(call: PluginCall) {
        try {
            val id = call.getString("id") ?: throw ParameterException("id")
            call.resolve(responses.ok(client.isCached(id)))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun downloadAlbum(call: PluginCall) {
        if (getOfflineMode()) {
            call.resolve(responses.error("Can't download albums in offline mode"))
            return
        }
        if (isTv) {
            call.resolve(responses.error("Can't download albums in Android TV"))
            return
        }
        try {
            val id = call.getString("id") ?: throw ParameterException("id")
            val album = client.getAlbum(id)
            client.downloadPlaylist(album.song, true)
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun getPlaylists(call: PluginCall) {
        try {
            call.resolve(responses.okArray(client.getPlaylists()))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun getPlaylist(call: PluginCall) {
        try {
            val id = call.getString("id") ?: throw ParameterException("id")
            call.resolve(responses.ok(client.getPlaylist(id)))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun removeFromPlaylist(call: PluginCall) {
        try {
            val id: String = call.getString("id") ?: throw ParameterException("id")
            val track: Int = call.getInt("track") ?: throw ParameterException("track")
            client.removeFromPlaylist(id, track)
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun addToPlaylist(call: PluginCall) {
        try {
            val id: String = call.getString("id") ?: ""
            val songId: String = call.getString("songId") ?: throw ParameterException("songId")
            if (id.isEmpty()) {
                client.createPlaylist(listOf(songId), "New playlist")
            } else {
                client.addToPlaylist(id, songId)
            }
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun createPlaylist(call: PluginCall) {
        try {
            val name: String = call.getString("name") ?: throw ParameterException("name")
            val jsIds: JSArray = call.getArray("songId") ?: throw ParameterException("songId")
            val ids: MutableList<String> = jsIds.toList()
            call.resolve(responses.ok(client.createPlaylist(ids, name)))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun updatePlaylist(call: PluginCall) {
        try {
            val playlist: Playlist =
                gson.fromJson(call.getObject("playlist").toString(), Playlist::class.java)
            call.resolve(responses.ok(client.updatePlaylist(playlist)))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun removePlaylist(call: PluginCall) {
        try {
            val id = call.getString("id") ?: throw ParameterException("id")
            client.removePlaylist(id)
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun clearCoverCache(call: PluginCall) {
        try {
            val freed = client.clearCoverCache()
            call.resolve(responses.ok(mapOf("freedBytes" to freed)))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun getCoverCacheSize(call: PluginCall) {
        try {
            call.resolve(responses.ok(mapOf("bytes" to client.getCoverCacheSizeBytes())))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun getLyrics(call: PluginCall) {
        try {
            val artist = call.getString("artist") ?: throw ParameterException("artist")
            val title = call.getString("title") ?: throw ParameterException("title")
            call.resolve(responses.ok(client.getLyrics(artist, title)))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun getInternetRadioStations(call: PluginCall) {
        try {
            call.resolve(responses.okArray(client.getInternetRadioStations()))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun createInternetRadioStation(call: PluginCall) {
        try {
            val name = call.getString("name") ?: throw ParameterException("name")
            val streamUrl = call.getString("streamUrl") ?: throw ParameterException("streamUrl")
            val homePageUrl = call.getString("homePageUrl")
            client.createInternetRadioStation(name, streamUrl, homePageUrl)
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun updateInternetRadioStation(call: PluginCall) {
        try {
            val id = call.getString("id") ?: throw ParameterException("id")
            val name = call.getString("name") ?: throw ParameterException("name")
            val streamUrl = call.getString("streamUrl") ?: throw ParameterException("streamUrl")
            val homePageUrl = call.getString("homePageUrl")
            client.updateInternetRadioStation(id, name, streamUrl, homePageUrl)
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun deleteInternetRadioStation(call: PluginCall) {
        try {
            val id = call.getString("id") ?: throw ParameterException("id")
            client.deleteInternetRadioStation(id)
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun star(call: PluginCall) {
        try {
            val id = call.getString("id") ?: throw ParameterException("id")
            client.star(id)
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun unstar(call: PluginCall) {
        try {
            val id = call.getString("id") ?: throw ParameterException("id")
            client.unstar(id)
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }
}
