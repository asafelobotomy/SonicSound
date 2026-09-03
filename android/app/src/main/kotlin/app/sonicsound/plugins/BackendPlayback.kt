package app.sonicsound.plugins

import app.sonicsound.KeyValueStorage.Companion.getOfflineMode
import app.sonicsound.models.ParameterException
import app.sonicsound.playback.PlaybackFacade
import app.sonicsound.services.MusicService
import app.sonicsound.services.MusicService.LocalBinder
import com.getcapacitor.PluginCall
import com.google.gson.Gson
import app.sonicsound.models.WebSocketCommand
import app.sonicsound.models.WebSocketMessage
import okhttp3.WebSocket
import org.json.JSONException

/** Playback control helpers (local service + jukebox websocket). */
class BackendPlayback(
    private val responses: BackendResponses,
    private val gsonProvider: () -> Gson,
    private val binderProvider: () -> LocalBinder?,
    private val boundProvider: () -> Boolean,
    private val webSocketConnectedProvider: () -> Boolean,
    private val webSocketProvider: () -> WebSocket?
) {
    private val gson: Gson get() = gsonProvider()
    private val binder: LocalBinder? get() = binderProvider()
    private val mBound: Boolean get() = boundProvider()
    private val webSocketConnected: Boolean get() = webSocketConnectedProvider()
    private val mWebSocket: WebSocket? get() = webSocketProvider()
    private val startService = PlaybackFacade::defaultStartService

    private fun wsCommand(command: String, data: String): WebSocketCommand =
        WebSocketCommand(command, data)

    private fun wsMessage(data: String, status: String = "ok"): WebSocketMessage =
        WebSocketMessage(data, "command", status)

    private fun sendWs(command: String, data: String = "") {
        val message = wsMessage(gson.toJson(wsCommand(command, data)))
        mWebSocket!!.send(gson.toJson(message))
    }

    fun shufflePlaylist(call: PluginCall) {
        try {
            if (webSocketConnected) {
                sendWs("shufflePlaylist")
                call.resolve(responses.ok(""))
                return
            }
            PlaybackFacade.shuffle(binder, mBound)
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun cycleRepeat(call: PluginCall) {
        try {
            if (webSocketConnected) {
                sendWs("cycleRepeat")
                call.resolve(responses.ok(""))
                return
            }
            PlaybackFacade.cycleRepeat(binder, mBound)
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun play(call: PluginCall) {
        if (webSocketConnected) {
            sendWs("play")
            call.resolve(responses.ok(""))
            return
        }
        PlaybackFacade.play(binder, mBound, startService)
        call.resolve(responses.ok(""))
    }

    fun pause(call: PluginCall) {
        if (webSocketConnected) {
            sendWs("pause")
            call.resolve(responses.ok(""))
            return
        }
        PlaybackFacade.pause(binder, mBound, startService)
        call.resolve(responses.ok(""))
    }

    fun seek(call: PluginCall) {
        try {
            val value = call.getFloat("time") ?: throw ParameterException("time")
            if (webSocketConnected) {
                sendWs("seek", value.toString())
                call.resolve(responses.ok(""))
                return
            }
            PlaybackFacade.seek(binder, mBound, value)
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun setVolume(call: PluginCall) {
        try {
            val value = call.getInt("volume", 100) ?: throw ParameterException("volume")
            if (webSocketConnected) {
                sendWs("setVolume", value.toString())
                call.resolve(responses.ok(""))
                return
            }
            PlaybackFacade.setVolume(binder, mBound, value)
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun playRadio(call: PluginCall) {
        if (getOfflineMode()) {
            call.resolve(responses.error("Not supported in offline mode"))
            return
        }
        try {
            val id = call.getString("song") ?: throw ParameterException("song")
            if (webSocketConnected) {
                sendWs("playRadio", id)
                call.resolve(responses.ok(""))
                return
            }
            PlaybackFacade.playRadio(binder, mBound, startService, id)
            call.resolve(responses.ok(""))
        } catch (e: NullPointerException) {
            call.resolve(responses.error("One of the parameters was null"))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun playInternetRadio(call: PluginCall) {
        try {
            val streamUrl = call.getString("streamUrl") ?: throw ParameterException("streamUrl")
            val name = call.getString("name") ?: "Radio"
            if (webSocketConnected) {
                sendWs("playInternetRadio", gson.toJson(mapOf("streamUrl" to streamUrl, "name" to name)))
                call.resolve(responses.ok(""))
                return
            }
            if (!PlaybackFacade.playInternetRadio(binder, mBound, streamUrl, name)) {
                call.resolve(responses.error("Music service not bound"))
                return
            }
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun playAlbum(call: PluginCall) {
        try {
            val id = call.getString("album") ?: throw ParameterException("album")
            val track = call.getInt("track") ?: throw ParameterException("track")
            if (webSocketConnected) {
                sendWs("playAlbum", "$id|$track")
                call.resolve(responses.ok(""))
                return
            }
            PlaybackFacade.playAlbum(binder, mBound, startService, id, track)
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun next(call: PluginCall) {
        if (webSocketConnected) {
            sendWs("next")
            call.resolve(responses.ok(""))
            return
        }
        // Prefer service intent for next/prev so MusicService handles unbound cold start.
        PlaybackFacade.next(null, false, startService)
        call.resolve(responses.ok(""))
    }

    fun prev(call: PluginCall) {
        if (webSocketConnected) {
            sendWs("prev")
            call.resolve(responses.ok(""))
            return
        }
        PlaybackFacade.prev(null, false, startService)
        call.resolve(responses.ok(""))
    }

    @Throws(JSONException::class)
    fun getCurrentState(call: PluginCall) {
        if (!mBound) {
            call.resolve(responses.error("Music service is not yet bound"))
            return
        }
        call.resolve(responses.ok(binder!!.getCurrentState()))
    }

    fun getCurrentPlaylist(call: PluginCall) {
        if (mBound) {
            call.resolve(responses.ok(binder!!.getPlaylist()))
        } else {
            call.resolve(responses.ok(MusicService.getDefaultPlaylist()))
        }
    }

    fun skipTo(call: PluginCall) {
        try {
            val track = call.getInt("track") ?: throw ParameterException("track")
            if (webSocketConnected) {
                sendWs("skipTo", track.toString())
                call.resolve(responses.ok(""))
                return
            }
            PlaybackFacade.skipTo(binder, mBound, track)
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun playPlaylist(call: PluginCall) {
        try {
            val track = call.getInt("track") ?: throw ParameterException("track")
            val id = call.getString("playlist") ?: throw ParameterException("playlist")
            if (webSocketConnected) {
                sendWs("playPlaylist", "$id|$track")
                call.resolve(responses.ok(""))
                return
            }
            PlaybackFacade.playPlaylist(binder, mBound, startService, id, track)
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun playJukeboxCollection(call: PluginCall) {
        try {
            val collection = call.getString("collection") ?: throw ParameterException("collection")
            if (webSocketConnected) {
                sendWs("playJukeboxCollection", collection)
                call.resolve(responses.ok(""))
                return
            }
            PlaybackFacade.playJukeboxCollection(binder, mBound, startService, collection)
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }
}
