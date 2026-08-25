package app.sonicsound.discovery

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import app.sonicsound.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Probes the local /24 for Subsonic-compatible servers on common ports.
 */
object SubsonicLanDiscovery {
    private val PORTS = listOf(4533, 4040, 80)
    private const val TIMEOUT_MS = 400

    suspend fun discover(context: Context): List<String> = withContext(Dispatchers.IO) {
        val subnetPrefix = resolveSubnetPrefix(context) ?: return@withContext emptyList()
        coroutineScope {
            (1..254).flatMap { host ->
                PORTS.map { port ->
                    async {
                        val base = "http://$subnetPrefix.$host:$port"
                        if (pingOk(base)) base else null
                    }
                }
            }.awaitAll().filterNotNull().distinct()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveSubnetPrefix(context: Context): String? {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcp = wifi?.dhcpInfo
            if (dhcp != null && dhcp.gateway != 0) {
                val gateway = Formatter.formatIpAddress(dhcp.gateway)
                val parts = gateway.split(".")
                if (parts.size == 4) {
                    return "${parts[0]}.${parts[1]}.${parts[2]}"
                }
            }
            val info = wifi?.connectionInfo
            if (info != null && info.ipAddress != 0) {
                val ip = Formatter.formatIpAddress(info.ipAddress)
                val parts = ip.split(".")
                if (parts.size == 4) {
                    return "${parts[0]}.${parts[1]}.${parts[2]}"
                }
            }
        } catch (_: Exception) {
            // fall through to App.localIp
        }
        val local = App.localIp ?: return null
        val parts = local.split(".")
        return if (parts.size == 4) {
            "${parts[0]}.${parts[1]}.${parts[2]}"
        } else {
            null
        }
    }

    private fun pingOk(baseUrl: String): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(
                "$baseUrl/rest/ping.view?v=1.16.0&c=SonicSound&f=json"
            )
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
            }
            if (connection.responseCode !in 200..299) {
                return false
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val response = root.optJSONObject("subsonic-response") ?: return false
            response.optString("status") == "ok"
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }
}
