package app.sonicsound

import android.os.Build
import app.sonicsound.models.WebSocketMessage
import app.sonicsound.remote.QuickConnect
import app.sonicsound.remote.QuickConnectSealed
import com.google.gson.Gson
import org.java_websocket.WebSocket

/**
 * Quick Connect exchange shared by both transports:
 * - websocket (phone app): → qcHello ← qcHello {tvPub, deviceName}; → qcLogin {sealed} ← qcResult
 * - HTTP (TV-served browser page): GET /api/qc/hello, POST /api/qc/login (same origin as the page)
 *
 * qcResult is "pending" once the code checks out, then "ok" / "error" after the TV tried the server.
 */
object QuickConnectExchange {
    const val ACTION_LOGIN = "QCLOGIN"
    const val ACTION_RESULT_OK = "QCRESULT_OK"
    const val ACTION_RESULT_ERROR = "QCRESULT_ERROR"

    data class Reply(val status: String, val data: String)

    private val gson = Gson()

    @Volatile
    private var waiting: ((Reply) -> Unit)? = null

    fun hello(): Reply {
        val tvPub = QuickConnect.publicKey() ?: return Reply("error", "Quick Connect is not open on the TV.")
        val deviceName = KeyValueStorage.getRemoteDeviceName()
            .ifBlank { Build.MODEL ?: "Android TV" }
        return Reply("ok", gson.toJson(mapOf("tvPub" to tvPub, "deviceName" to deviceName)))
    }

    /** Checks the code; [onReply] gets "pending" right away on success, then the final outcome. */
    fun login(data: String, onReply: (Reply) -> Unit) {
        val sealed = try {
            gson.fromJson(data, QuickConnectSealed::class.java)
        } catch (_: Exception) {
            null
        }
        if (sealed == null) return onReply(Reply("error", "Malformed Quick Connect request"))
        when (val result = QuickConnect.open(sealed)) {
            is QuickConnect.OpenResult.Rejected -> onReply(Reply("error", result.message))
            is QuickConnect.OpenResult.Ok -> {
                waiting = onReply
                onReply(Reply("pending", "Code accepted. The TV is signing in…"))
                Globals.NotifyObservers(ACTION_LOGIN, result.payload)
            }
        }
    }

    /** Relays the TV's server-login outcome back to whoever sent the credentials. */
    fun onObserverUpdate(action: String?, value: String?) {
        val reply = when (action) {
            ACTION_RESULT_OK -> Reply("ok", "Signed in on the TV.")
            ACTION_RESULT_ERROR -> Reply("error", value ?: "The server rejected the login.")
            else -> return
        }
        val target = waiting ?: return
        waiting = null
        target(reply)
    }
}

/** Websocket transport for [QuickConnectExchange]. */
class MessageServerQuickConnect(private val gson: Gson) {
    fun hello(conn: WebSocket) {
        val reply = QuickConnectExchange.hello()
        conn.send(gson.toJson(WebSocketMessage(reply.data, "qcHello", reply.status)))
    }

    fun login(conn: WebSocket, data: String) {
        QuickConnectExchange.login(data) { reply ->
            if (conn.isOpen) conn.send(gson.toJson(WebSocketMessage(reply.data, "qcResult", reply.status)))
        }
    }

    fun onObserverUpdate(action: String?, value: String?) =
        QuickConnectExchange.onObserverUpdate(action, value)
}
