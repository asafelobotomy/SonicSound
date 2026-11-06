package app.sonicsound.remote

import android.content.res.AssetManager
import android.util.Log
import app.sonicsound.QuickConnectExchange
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Serves the Quick Connect browser page (assets/quickconnect) and its same-origin API on the
 * LAN while the TV login screen is open. Credentials arrive already sealed (QuickConnectCrypto).
 */
class QuickConnectHttpServer(
    private val assets: AssetManager,
    private val port: Int = QuickConnect.HTTP_PORT,
) {
    private var socket: ServerSocket? = null
    private val gson = Gson()

    private val routes = mapOf(
        "/" to Asset("quickconnect/index.html", "text/html; charset=utf-8"),
        "/index.html" to Asset("quickconnect/index.html", "text/html; charset=utf-8"),
        "/qc.js" to Asset("quickconnect/qc.js", "text/javascript; charset=utf-8"),
    )

    private data class Asset(val path: String, val contentType: String)

    @Synchronized
    fun start() {
        if (socket != null) return
        val server = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't start Quick Connect page: ${e.message}")
            return
        }
        socket = server
        thread(name = "QuickConnectHttp", isDaemon = true) {
            while (!server.isClosed) {
                try {
                    val client = server.accept()
                    thread(isDaemon = true) { handle(client) }
                } catch (_: SocketException) {
                    break
                } catch (e: Exception) {
                    Log.w(TAG, e.message ?: "accept failed")
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        socket?.close()
        socket = null
    }

    private fun handle(client: Socket) {
        client.use { s ->
            s.soTimeout = 5_000
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0)
            val path = parts.getOrNull(1)?.substringBefore('?')?.substringBefore('#')
            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                }
            }
            val out = s.getOutputStream()
            fun respond(code: Int, reason: String, type: String, body: ByteArray) {
                out.write(header(code, reason, type, body.size).toByteArray())
                out.write(body)
                out.flush()
            }
            fun json(reply: QuickConnectExchange.Reply) =
                respond(200, "OK", "application/json", gson.toJson(reply).toByteArray())

            when {
                method == "GET" && path == "/api/qc/hello" -> json(QuickConnectExchange.hello())
                method == "POST" && path == "/api/qc/login" -> {
                    if (contentLength !in 1..MAX_BODY) {
                        return respond(413, "Payload Too Large", "text/plain", ByteArray(0))
                    }
                    val buf = CharArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val n = reader.read(buf, read, contentLength - read)
                        if (n < 0) break
                        read += n
                    }
                    json(awaitLogin(String(buf, 0, read)))
                }
                method == "GET" && routes[path] != null -> {
                    val asset = routes.getValue(path!!)
                    val body = try {
                        assets.open(asset.path).use { it.readBytes() }
                    } catch (_: Exception) {
                        ByteArray(0)
                    }
                    respond(200, "OK", asset.contentType, body)
                }
                else -> respond(404, "Not Found", "text/plain", "Not found".toByteArray())
            }
        }
    }

    /** Blocks until the TV reports the server-login outcome (or times out). */
    private fun awaitLogin(body: String): QuickConnectExchange.Reply {
        val latch = CountDownLatch(1)
        var final = QuickConnectExchange.Reply("error", "The TV didn't finish signing in.")
        QuickConnectExchange.login(body) { reply ->
            if (reply.status != "pending") {
                final = reply
                latch.countDown()
            }
        }
        latch.await(LOGIN_WAIT_SECONDS, TimeUnit.SECONDS)
        return final
    }

    private fun header(code: Int, reason: String, type: String, length: Int): String =
        "HTTP/1.1 $code $reason\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: $length\r\n" +
            "Cache-Control: no-store\r\n" +
            "Referrer-Policy: no-referrer\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Connection: close\r\n\r\n"

    companion object {
        private const val TAG = "QuickConnectHttp"
        private const val MAX_BODY = 8 * 1024
        private const val LOGIN_WAIT_SECONDS = 25L
    }
}
