package app.sonicsound.plugins

import android.util.Log
import app.sonicsound.Globals
import app.sonicsound.KeyValueStorage.Companion.getActiveAccount
import app.sonicsound.WebSocketNotification
import app.sonicsound.models.ParameterException
import app.sonicsound.models.SetPlaylistAndPlayRequest
import app.sonicsound.models.WebSocketCommand
import app.sonicsound.models.WebSocketMessage
import app.sonicsound.services.MusicService.LocalBinder
import com.getcapacitor.JSObject
import com.getcapacitor.PluginCall
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.lang.Integer.parseInt

/**
 * Jukebox / QR websocket connection state and message handling.
 */
class BackendWebsocket(
    private val responses: BackendResponses,
    private val gsonProvider: () -> Gson,
    private val client: OkHttpClient,
    private val binderProvider: () -> LocalBinder?,
    private val boundProvider: () -> Boolean,
    private val notifyListeners: (String, JSObject?) -> Unit
) {
    private val gson: Gson get() = gsonProvider()
    private val binder: LocalBinder? get() = binderProvider()
    private val mBound: Boolean get() = boundProvider()

    var webSocketConnected: Boolean = false
        private set
    var mWebSocket: WebSocket? = null
        private set
    var lastIp: String? = null
    var reconnect: Boolean = false

    fun getWebsocketStatus(call: PluginCall) {
        call.resolve(responses.ok(webSocketConnected))
    }

    fun disconnectWebsocket(call: PluginCall) {
        try {
            lastIp = null
            reconnect = false
            setWebsocketConnectionStatus(false)
            mWebSocket!!.close(1000, "")
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun sendUdpBroadcast(call: PluginCall) {
        Globals.NotifyObservers("SENDUDP", "")
        call.resolve(responses.ok(""))
    }

    fun qrLogin(call: PluginCall) {
        try {
            var ip = call.getString("ip") ?: throw ParameterException("id")
            var jukebox = false
            if (ip.endsWith('j')) {
                jukebox = true
                ip = ip.substringBefore('j')
            }
            if (!isIp(ip)) {
                call.resolve(responses.error("The QR code is not an IP address. Please try again."))
            }
            val account = getActiveAccount()
            val jsonAccount = gson.toJson(account)
            val message = WebSocketMessage(jsonAccount, if (jukebox) "jukebox" else "login", "ok")
            val json = gson.toJson(message)
            if (webSocketConnected) {
                mWebSocket!!.close(1000, "")
            }
            try {
                connectWebSocket("ws://$ip:30001", true)
            } catch (e: Exception) {
                Globals.NotifyObservers("EX", "Failed to open websocket connection")
                return
            }
            mWebSocket!!.send(json)

            if (jukebox && mBound && binder!!.getCurrentState().playing) {
                val playlist = binder!!.getPlaylist()
                val request = SetPlaylistAndPlayRequest(
                    playlist,
                    playlist.entry.indexOf(binder!!.getCurrentState().currentTrack),
                    binder!!.getCurrentState().position,
                    binder!!.getCurrentState().playing
                )
                val setPlaylistCommand =
                    WebSocketCommand("setPlaylistAndPlay", gson.toJson(request))
                val setPlaylistMessage =
                    WebSocketMessage(gson.toJson(setPlaylistCommand), "command", "ok")
                mWebSocket!!.send(gson.toJson(setPlaylistMessage))
                binder!!.pause()
            }

            if (jukebox) {
                lastIp = ip
            } else {
                mWebSocket!!.close(1000, "")
                setWebsocketConnectionStatus(false)
            }
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun onObserverUpdate(action: String, value: String?) {
        try {
            if (action == "EX") {
                notifyListeners("EX", JSObject("{\"error\":\"$value\"}"))
            } else if (action == "WS") {
                val ret = JSObject()
                ret.put("connected", value == "true")
                notifyListeners("webSocketConnection", ret)
            } else if (action == "RESUMED") {
                if (reconnect && lastIp != null) {
                    val account = getActiveAccount()
                    val jsonAccount = gson.toJson(account)
                    val message = WebSocketMessage(jsonAccount, "jukebox", "ok")
                    val json = gson.toJson(message)
                    if (webSocketConnected) {
                        mWebSocket!!.close(1000, "")
                    }
                    try {
                        connectWebSocket("ws://$lastIp:30001", false)
                    } catch (e: Exception) {
                        return
                    }
                    mWebSocket!!.send(json)
                    if (mBound && binder!!.getCurrentState().playing) {
                        binder!!.pause()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SonicSound", e.message!!)
        }
    }

    fun setWebsocketConnectionStatus(status: Boolean) {
        val ret = JSObject()
        ret.put("connected", status)
        webSocketConnected = status
        notifyListeners("webSocketConnection", ret)
    }

    fun connectWebSocket(url: String, notifyOnConnect: Boolean) {
        val request: Request = Request.Builder().url(url).build()
        val listener = EchoWebSocketListener(notifyOnConnect)
        mWebSocket = client.newWebSocket(request, listener)
    }

    private fun isIp(ip: String): Boolean {
        val splitIp = ip.split('.')
        return try {
            (splitIp.size == 4) && splitIp.all { parseInt(it) in 0..255 }
        } catch (e: Exception) {
            false
        }
    }

    private inner class EchoWebSocketListener(val notifyOnConnect: Boolean) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            setWebsocketConnectionStatus(true)
            if (notifyOnConnect) {
                Globals.NotifyObservers(
                    "EX",
                    "You're connected! Tap the TV icon on the top right to disconnect your phone."
                )
            }
            reconnect = true
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text == "sonicsound") {
                webSocket.close(1000, "")
                mWebSocket = null
                setWebsocketConnectionStatus(false)
                reconnect = false
            } else {
                try {
                    val message: WebSocketMessage =
                        gson.fromJson(text, WebSocketMessage::class.java)
                    if (message.type == "notification") {
                        val notification =
                            gson.fromJson(message.data, WebSocketNotification::class.java)
                        if (notification.value != null) {
                            notifyListeners(notification.action, JSObject(notification.value))
                        } else {
                            notifyListeners(notification.action, null)
                        }
                    } else if (message.type == "message") {
                        Globals.NotifyObservers("EX", message.data)
                    } else if (message.type == "acceptedConnection") {
                        Globals.NotifyObservers("EX", "JUKEBOX MODE ON, WE ARE GO")
                    }
                } catch (e: Exception) {
                    Globals.NotifyObservers("EX", e.message)
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            reconnect = false
            mWebSocket = null
            setWebsocketConnectionStatus(false)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Globals.NotifyObservers("EX", "${t.message} ${response?.message}")
            reconnect = false
            if (mWebSocket != null) {
                try {
                    mWebSocket!!.close(1000, "")
                } catch (e: Exception) {
                    // Swallow this
                }
                setWebsocketConnectionStatus(false)
            }
        }
    }
}
