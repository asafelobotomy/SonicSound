package app.sonicsound

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import app.sonicsound.App.Companion.context
import app.sonicsound.models.Account
import app.sonicsound.models.RemoteConnectRequest
import app.sonicsound.remote.RemoteAuth
import app.sonicsound.models.WebSocketCommand
import app.sonicsound.models.WebSocketMessage
import app.sonicsound.services.MusicService
import app.sonicsound.services.MusicService.LocalBinder
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.nio.ByteBuffer

class MessageServer(port: Int) : WebSocketServer(InetSocketAddress(port)), IBroadcastObserver {

    private var mBound: Boolean = false
    private var binder: LocalBinder? = null

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            className: ComponentName,
            service: IBinder
        ) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            Log.i("ServiceBinder", "Binding service")
            binder = service as LocalBinder
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            Log.i("ServiceBinder", "Unbinding service")
            mBound = false
        }
    }

    init {
        Globals.RegisterObserver(this)
    }

    fun dispose() {
        Globals.UnregisterObserver(this)
    }

    private val clients: MutableList<WebSocket> = mutableListOf()
    private val pendingAuth: ConcurrentHashMap<WebSocket, String> = ConcurrentHashMap()
    private val authenticated: MutableSet<WebSocket> = ConcurrentHashMap.newKeySet()

    private val gson: Gson = Gson()

    private fun sendTyped(conn: WebSocket, type: String, data: String, status: String = "ok") {
        conn.send(gson.toJson(WebSocketMessage(data, type, status)))
    }

    private fun sendCurrentTrackIfPlaying(conn: WebSocket) {
        if (!mBound) return
        val currentTrack = binder!!.getCurrentState().currentTrack
        if (currentTrack.id.isNotEmpty()) {
            val currentTrackJson = gson.toJson(currentTrack)
            val webSocketNotification =
                WebSocketNotification("currentTrack", "{\"currentTrack\": $currentTrackJson}")
            val jsonNotification = gson.toJson(webSocketNotification)
            val webSocketMessage = WebSocketMessage(jsonNotification, "notification", "ok")
            conn.send(gson.toJson(webSocketMessage))
        }
    }

    private fun handleRemoteConnect(conn: WebSocket, data: String) {
        val active = KeyValueStorage.getActiveAccount()
        val request = try {
            gson.fromJson(data, RemoteConnectRequest::class.java)
        } catch (_: Exception) {
            // Legacy: full Account payload from older clients
            try {
                val account = gson.fromJson(data, Account::class.java)
                RemoteConnectRequest(account.url, account.username)
            } catch (e: Exception) {
                conn.send(constructMessage(e.message ?: "Malformed payload", "error"))
                return
            }
        }
        if (!RemoteAuth.accountsMatch(
                request.url,
                request.username,
                active.url,
                active.username,
            )
        ) {
            conn.send(
                constructMessage(
                    "Sign in to the same Navidrome account on both phone and TV to use Remote.",
                    "error",
                )
            )
            return
        }
        val nonce = UUID.randomUUID().toString()
        pendingAuth[conn] = nonce
        sendTyped(conn, "authChallenge", nonce)
    }

    private fun handleRemoteAuth(conn: WebSocket, proof: String) {
        val nonce = pendingAuth[conn]
        if (nonce == null) {
            conn.send(constructMessage("No pending auth challenge", "error"))
            return
        }
        val password = KeyValueStorage.getActiveAccount().password
        if (!RemoteAuth.verifyAuthProof(nonce, password, proof)) {
            conn.send(constructMessage("Remote authentication failed", "error"))
            pendingAuth.remove(conn)
            return
        }
        pendingAuth.remove(conn)
        authenticated.add(conn)
        sendTyped(conn, "acceptedConnection", "")
        sendCurrentTrackIfPlaying(conn)
        Globals.NotifyObservers("WS", "true")
    }

    private fun constructMessage(text: String, status: String = "ok"): String {
        val message = WebSocketMessage(text, "message", status)
        return gson.toJson(message)
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        clients.add(conn)
        Globals.NotifyObservers("WS", "true")
        // Do not push currentTrack until the client authenticates.
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
        clients.remove(conn)
        pendingAuth.remove(conn)
        authenticated.remove(conn)
        if (clients.size == 0) {
            Globals.NotifyObservers("WS", "false")
        }
    }

    override fun onMessage(conn: WebSocket, message: String) {
        try {
            val m = gson.fromJson(message, WebSocketMessage::class.java)
            when (m.type) {
                "login" -> {
                    if (!isPairingActive()) {
                        conn.send(
                            constructMessage(
                                "Pairing is not active. Open login on the TV to pair.",
                                "error",
                            )
                        )
                        return
                    }
                    Globals.NotifyObservers("WSLOGIN", m.data)
                    return
                }
                "remote", "jukebox" -> {
                    handleRemoteConnect(conn, m.data)
                    return
                }
                "remoteAuth" -> {
                    handleRemoteAuth(conn, m.data)
                    return
                }
                "command" -> {
                    if (!authenticated.contains(conn)) {
                        conn.send(constructMessage("Not authenticated", "error"))
                        return
                    }
                    val command = gson.fromJson(m.data, WebSocketCommand::class.java)
                    MessageServerCommands.handle(
                        command = command,
                        conn = conn,
                        binder = binder,
                        mBound = mBound,
                        gson = gson,
                        constructMessage = { text, status -> constructMessage(text, status) },
                    )
                }
            }
        } catch (e: Exception) {
            conn.send(
                constructMessage(
                    e.message!!,
                    "error"
                )
            )
        }
    }

    override fun onMessage(conn: WebSocket, message: ByteBuffer) {
//        broadcast(message.array())
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        ex.printStackTrace()
        Globals.NotifyObservers("EX", ex.message)
    }

    override fun onStart() {
        connectionLostTimeout = 0
        connectionLostTimeout = 100

        // Bind to the music service
        val intent = Intent(context, MusicService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun update(action: String?, value: String?) {
        if (action == "SLCANCEL") {
            if (mBound) {
                context.unbindService(connection)
                mBound = false
                binder = null
            }
        } else if (action != null && action.startsWith("MS")) {
            val webSocketNotification = WebSocketNotification(action.replace("MS", ""), value)
            val jsonNotification = gson.toJson(webSocketNotification)
            val webSocketMessage = WebSocketMessage(jsonNotification, "notification", "ok")
            val jsonMessage = gson.toJson(webSocketMessage)
            // Only push playback state to authenticated remotes.
            for (conn in authenticated) {
                if (conn.isOpen) {
                    conn.send(jsonMessage)
                }
            }
        }
    }

    companion object {
        @Volatile
        var pairingEnabledUntilMs: Long = 0

        fun enablePairing(durationMs: Long = 120_000) {
            pairingEnabledUntilMs = System.currentTimeMillis() + durationMs
        }

        fun disablePairing() {
            pairingEnabledUntilMs = 0
        }

        fun isPairingActive(): Boolean =
            System.currentTimeMillis() < pairingEnabledUntilMs
    }
}
