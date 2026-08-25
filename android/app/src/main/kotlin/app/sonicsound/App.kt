package app.sonicsound

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import app.sonicsound.services.MediaBrowserService
import app.sonicsound.subsonic.SubsonicClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

class App : Application() {
    private var udpServer: UDPServer? = null

    override fun onCreate() {
        super.onCreate()
        application = this
        CoroutineScope(Dispatchers.IO).launch {
            if (KeyValueStorage.getActiveAccount().username != null) {
                try {
                    val client = SubsonicClient(KeyValueStorage.getActiveAccount())
                    MediaBrowserService.warmCaches(client)
                } catch (e: Exception) {
                    Log.e("SonicSound", e.message ?: "cache warm failed")
                }
            }
        }
        val uiModeManager: UiModeManager =
            this.applicationContext.getSystemService(UI_MODE_SERVICE) as UiModeManager

        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            isTv = true
        }
        discoverLanAddress()

        if (isTv) {
            server = MessageServer(30001)
            server!!.start()
        }
        if (localIp != null) {
            val broadcast = localBroadcast ?: derivedBroadcast(localIp!!)
            try {
                udpServer = UDPServer(
                    InetAddress.getByName(localIp),
                    InetAddress.getByName(broadcast),
                    isTv
                )
                if (isTv) {
                    CoroutineScope(Dispatchers.IO).launch {
                        udpServer!!.receiveUDP()
                    }
                }
            } catch (e: Exception) {
                Log.w("SonicSound", "UDP discovery disabled: ${e.message}")
            }
        } else {
            Log.w("SonicSound", "No LAN IP found; UDP discovery disabled")
        }
    }

    private fun discoverLanAddress() {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            val preferred = interfaces.filter { iface ->
                val name = iface.name.lowercase()
                name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("en")
            }
            val candidates = preferred.ifEmpty {
                interfaces.filter { iface ->
                    val name = iface.name.lowercase()
                    !name.startsWith("rm") && !name.startsWith("radio")
                }
            }
            for (intf in candidates) {
                for (addr in intf.interfaceAddresses) {
                    val address = addr.address ?: continue
                    if (address.isLoopbackAddress || address.hostAddress?.contains(":") == true) {
                        continue
                    }
                    val a = (address.hostAddress ?: continue).replace("/", "")
                    if (a.startsWith("192.168") || a.startsWith("10.") || a.startsWith("172.")) {
                        localIp = a
                        localBroadcast = addr.broadcast?.hostAddress
                        return
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e("SonicSound", ex.message ?: "LAN discovery failed")
        }
    }

    private fun derivedBroadcast(ip: String): String {
        val parts = ip.split(".")
        return if (parts.size == 4) {
            "${parts[0]}.${parts[1]}.${parts[2]}.255"
        } else {
            ip
        }
    }

    companion object {
        var application: Application? = null
            private set

        @JvmStatic
        val context: Context
            get() = application!!.applicationContext
        var localIp: String? = null
        var localBroadcast: String? = null
        var server: MessageServer? = null
        var isTv: Boolean = false
    }
}
