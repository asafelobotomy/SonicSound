package app.sonicsound.plugins

import android.util.Log
import app.sonicsound.Globals
import app.sonicsound.KeyValueStorage
import app.sonicsound.KeyValueStorage.Companion.getActiveAccount
import app.sonicsound.WebSocketNotification
import app.sonicsound.models.ParameterException
import app.sonicsound.models.RemoteConnectRequest
import app.sonicsound.models.SetPlaylistAndPlayRequest
import app.sonicsound.models.WebSocketCommand
import app.sonicsound.models.WebSocketMessage
import app.sonicsound.remote.RemoteAuth
import app.sonicsound.remote.RemoteDevice
import app.sonicsound.services.MusicService.LocalBinder
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.PluginCall
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.lang.Integer.parseInt
import java.util.concurrent.ConcurrentHashMap

/** LAN Remote websocket connection and discovery. */
class BackendWebsocket(
    private val responses: BackendResponses,
    private val gsonProvider: () -> Gson,
    private val client: OkHttpClient,
    private val binderProvider: () -> LocalBinder?,
    private val boundProvider: () -> Boolean,
    private val notifyListeners: (String, JSObject?) -> Unit,
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

    private var pendingNonce: String? = null
    private var remoteMode: Boolean = false
    private var discoveryActive: Boolean = false
    private val discoveredRemotes = ConcurrentHashMap<String, RemoteDevice>()
    private var reconnectAttempt: Int = 0

    fun getWebsocketStatus(call: PluginCall) {
        call.resolve(responses.ok(webSocketConnected))
    }

    fun disconnectWebsocket(call: PluginCall) {
        try {
            disconnectInternal()
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun sendUdpBroadcast(call: PluginCall) {
        Globals.NotifyObservers("SENDUDP", "")
        call.resolve(responses.ok(""))
    }

    fun startRemoteDiscovery(call: PluginCall) {
        discoveryActive = true
        Globals.NotifyObservers("SENDUDP", "")
        call.resolve(responses.ok(""))
    }

    fun stopRemoteDiscovery(call: PluginCall) {
        discoveryActive = false
        call.resolve(responses.ok(""))
    }

    fun getDiscoveredRemotes(call: PluginCall) {
        val account = getActiveAccount()
        val fingerprint = RemoteAuth.accountFingerprint(account.url, account.username)
        val matched = discoveredRemotes.values.filter { it.accountFingerprint == fingerprint }
        val arr = JSArray()
        matched.forEach { device ->
            val obj = JSObject()
            obj.put("ip", device.ip)
            obj.put("deviceName", device.deviceName)
            obj.put("serverUrl", device.serverUrl)
            obj.put("accountFingerprint", device.accountFingerprint)
            obj.put("wsPort", device.wsPort)
            arr.put(obj)
        }
        call.resolve(responses.ok(arr))
    }

    fun connectRemote(call: PluginCall) {
        try {
            val ip = call.getString("ip") ?: throw ParameterException("ip")
            connectRemoteInternal(ip, call.getString("deviceName"))
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun qrLogin(call: PluginCall) {
        try {
            var ip = call.getString("ip") ?: throw ParameterException("ip")
            val mode = call.getString("mode")
            var remote = mode == "remote"
            if (ip.endsWith('j')) {
                remote = true
                ip = ip.substringBefore('j')
            }
            if (!isIp(ip)) {
                call.resolve(responses.error("The QR code is not an IP address. Please try again."))
                return
            }
            if (remote) {
                connectRemoteInternal(ip, null)
            } else {
                loginToTv(ip)
            }
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    private fun loginToTv(ip: String) {
        val account = getActiveAccount()
        val jsonAccount = gson.toJson(account)
        val message = WebSocketMessage(jsonAccount, "login", "ok")
        if (webSocketConnected) {
            mWebSocket!!.close(1000, "")
        }
        connectWebSocket("ws://$ip:30001", true)
        mWebSocket!!.send(gson.toJson(message))
        mWebSocket!!.close(1000, "")
        setWebsocketConnectionStatus(false)
    }

    private fun connectRemoteInternal(ip: String, deviceName: String?) {
        remoteMode = true
        lastIp = ip
        reconnect = true
        reconnectAttempt = 0
        if (!deviceName.isNullOrBlank()) {
            KeyValueStorage.setLastRemoteDeviceName(deviceName)
        }
        KeyValueStorage.setLastRemoteIp(ip)
        pendingNonce = null
        if (webSocketConnected) {
            mWebSocket!!.close(1000, "")
        }
        connectWebSocket("ws://$ip:30001", true)
        sendRemoteConnectRequest()
    }

    private fun sendRemoteConnectRequest() {
        val account = getActiveAccount()
        val request = RemoteConnectRequest(account.url, account.username)
        val message = WebSocketMessage(gson.toJson(request), "remote", "ok")
        mWebSocket?.send(gson.toJson(message))
    }

    private fun sendRemoteAuth(proof: String) {
        val message = WebSocketMessage(proof, "remoteAuth", "ok")
        mWebSocket?.send(gson.toJson(message))
    }

    private fun disconnectInternal() {
        lastIp = null
        reconnect = false
        remoteMode = false
        pendingNonce = null
        setWebsocketConnectionStatus(false)
        mWebSocket?.close(1000, "")
    }

    fun onObserverUpdate(action: String, value: String?) {
        try {
            when (action) {
                "EX" -> notifyListeners("EX", JSObject("{\"error\":\"$value\"}"))
                "WS" -> {
                    val ret = JSObject()
                    ret.put("connected", value == "true")
                    notifyListeners("webSocketConnection", ret)
                }
                "RESUMED" -> {
                    if (reconnect && lastIp != null) {
                        connectRemoteInternal(lastIp!!, KeyValueStorage.getLastRemoteDeviceName())
                    }
                }
                "REMOTE_DEVICE" -> {
                    if (value.isNullOrBlank()) return
                    val device = gson.fromJson(value, RemoteDevice::class.java)
                    discoveredRemotes[device.ip] = device
                    emitRemoteDevicesUpdated()
                    val account = getActiveAccount()
                    val fingerprint = RemoteAuth.accountFingerprint(account.url, account.username)
                    if (discoveryActive && device.accountFingerprint == fingerprint) {
                        val lastIp = KeyValueStorage.getLastRemoteIp()
                        if (lastIp == device.ip && !webSocketConnected) {
                            connectRemoteInternal(device.ip, device.deviceName)
                        }
                    }
                }
                "MStvPacket" -> {
                    if (value.isNullOrBlank()) return
                    val type = object : TypeToken<Map<String, String>>() {}.type
                    val map: Map<String, String> = gson.fromJson(value, type)
                    val ip = map["ip"] ?: return
                    discoveredRemotes[ip] = RemoteDevice(
                        ip = ip,
                        deviceName = ip,
                        serverUrl = "",
                        accountFingerprint = "",
                    )
                    emitRemoteDevicesUpdated()
                }
            }
        } catch (e: Exception) {
            Log.e("SonicSound", e.message ?: "observer error")
        }
    }

    private fun emitRemoteDevicesUpdated() {
        val account = getActiveAccount()
        val fingerprint = RemoteAuth.accountFingerprint(account.url, account.username)
        val matched = discoveredRemotes.values.filter { it.accountFingerprint == fingerprint || it.accountFingerprint.isEmpty() }
        val arr = JSArray()
        matched.forEach { device ->
            val obj = JSObject()
            obj.put("ip", device.ip)
            obj.put("deviceName", device.deviceName)
            obj.put("serverUrl", device.serverUrl)
            obj.put("wsPort", device.wsPort)
            arr.put(obj)
        }
        val payload = JSObject()
        payload.put("devices", arr)
        notifyListeners("remoteDevicesUpdated", payload)
    }

    fun setWebsocketConnectionStatus(status: Boolean) {
        val ret = JSObject()
        ret.put("connected", status)
        webSocketConnected = status
        notifyListeners("webSocketConnection", ret)
    }

    fun connectWebSocket(url: String, notifyOnConnect: Boolean) {
        val request: Request = Request.Builder().url(url).build()
        mWebSocket = client.newWebSocket(request, EchoWebSocketListener(notifyOnConnect))
    }

    private fun syncPlaylistToTvIfNeeded() {
        if (!remoteMode || !mBound) return
        if (!binder!!.getCurrentState().playing) return
        val playlist = binder!!.getPlaylist()
        val request = SetPlaylistAndPlayRequest(
            playlist,
            playlist.entry.orEmpty().indexOf(binder!!.getCurrentState().currentTrack),
            binder!!.getCurrentState().position,
            binder!!.getCurrentState().playing,
        )
        val setPlaylistCommand = WebSocketCommand("setPlaylistAndPlay", gson.toJson(request))
        val setPlaylistMessage = WebSocketMessage(gson.toJson(setPlaylistCommand), "command", "ok")
        mWebSocket?.send(gson.toJson(setPlaylistMessage))
        binder!!.pause()
    }

    private fun isIp(ip: String): Boolean {
        val splitIp = ip.split('.')
        return try {
            splitIp.size == 4 && splitIp.all { parseInt(it) in 0..255 }
        } catch (_: Exception) {
            false
        }
    }

    private inner class EchoWebSocketListener(val notifyOnConnect: Boolean) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            setWebsocketConnectionStatus(true)
            reconnectAttempt = 0
            if (notifyOnConnect && remoteMode) {
                Globals.NotifyObservers(
                    "EX",
                    "Connecting to TV…",
                )
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text == "sonicsound") {
                webSocket.close(1000, "")
                mWebSocket = null
                setWebsocketConnectionStatus(false)
                reconnect = false
                return
            }
            try {
                val message: WebSocketMessage = gson.fromJson(text, WebSocketMessage::class.java)
                when (message.type) {
                    "notification" -> {
                        val notification =
                            gson.fromJson(message.data, WebSocketNotification::class.java)
                        if (notification.value != null) {
                            notifyListeners(notification.action, JSObject(notification.value))
                        } else {
                            notifyListeners(notification.action, null)
                        }
                    }
                    "message" -> Globals.NotifyObservers("EX", message.data)
                    "authChallenge" -> {
                        pendingNonce = message.data
                        val account = getActiveAccount()
                        sendRemoteAuth(RemoteAuth.computeAuthProof(message.data, account.password))
                    }
                    "acceptedConnection" -> {
                        Globals.NotifyObservers("EX", "Remote connected")
                        syncPlaylistToTvIfNeeded()
                    }
                }
            } catch (e: Exception) {
                Globals.NotifyObservers("EX", e.message)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            onDisconnected()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Globals.NotifyObservers("EX", "${t.message} ${response?.message}")
            onDisconnected()
            scheduleReconnect()
        }

        private fun onDisconnected() {
            reconnectAttempt++
            mWebSocket = null
            setWebsocketConnectionStatus(false)
        }

        private fun scheduleReconnect() {
            if (!reconnect || lastIp == null || !remoteMode) return
            val delayMs = minOf(30_000, 1000L * (1 shl minOf(reconnectAttempt, 5)))
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (reconnect && lastIp != null && !webSocketConnected) {
                    try {
                        connectWebSocket("ws://$lastIp:30001", false)
                        sendRemoteConnectRequest()
                    } catch (_: Exception) {
                        // retry on next failure
                    }
                }
            }, delayMs)
        }
    }
}
