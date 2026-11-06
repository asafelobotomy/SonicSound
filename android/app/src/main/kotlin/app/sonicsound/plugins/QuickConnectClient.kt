package app.sonicsound.plugins

import android.os.Handler
import android.os.Looper
import app.sonicsound.KeyValueStorage
import app.sonicsound.models.WebSocketMessage
import app.sonicsound.remote.QuickConnectCrypto
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Phone side of Quick Connect: sends this phone's signed-in account to a TV showing [code].
 * Runs natively because the password never leaves native storage.
 */
class QuickConnectClient(private val client: OkHttpClient, private val gson: Gson) {

    fun signInTv(ip: String, port: Int, code: String, onDone: (ok: Boolean, message: String) -> Unit) {
        val account = KeyValueStorage.getActiveAccount()
        if (account.username.isNullOrBlank() || account.url.isBlank()) {
            onDone(false, "Sign in on this phone first.")
            return
        }
        var finished = false
        val handler = Handler(Looper.getMainLooper())
        lateinit var socket: WebSocket
        fun finish(ok: Boolean, message: String) {
            synchronized(this) {
                if (finished) return
                finished = true
            }
            handler.removeCallbacksAndMessages(null)
            socket.close(1000, null)
            onDone(ok, message)
        }

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(gson.toJson(WebSocketMessage("", "qcHello", "ok")))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = try {
                    gson.fromJson(text, WebSocketMessage::class.java)
                } catch (_: Exception) {
                    return
                }
                when (message.type) {
                    "qcHello" -> {
                        if (message.status != "ok") return finish(false, message.data)
                        val tvPub = JsonParser.parseString(message.data).asJsonObject["tvPub"].asString
                        val sealed = QuickConnectCrypto.seal(tvPub, code, gson.toJson(account))
                        webSocket.send(gson.toJson(WebSocketMessage(gson.toJson(sealed), "qcLogin", "ok")))
                    }
                    "qcResult" -> if (message.status != "pending") {
                        finish(message.status == "ok", message.data)
                    }
                    "message" -> if (message.status == "error") finish(false, message.data)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                finish(false, "Couldn't reach the TV: ${t.message ?: "connection failed"}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                finish(false, "The TV closed the connection.")
            }
        }
        socket = client.newWebSocket(Request.Builder().url("ws://$ip:$port").build(), listener)
        handler.postDelayed({ finish(false, "The TV didn't answer. Is Quick Connect still open?") }, 30_000)
    }
}
