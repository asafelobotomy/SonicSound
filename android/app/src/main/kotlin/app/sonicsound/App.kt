package app.sonicsound

import app.sonicsound.services.MediaBrowserService
import app.sonicsound.subsonic.SubsonicClient

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.util.*

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
        try {
            val interfaces: List<NetworkInterface> =
                Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.name.subSequence(
                        0,
                        2
                    ) == "rm" || (intf.name.length >= 6 && intf.name.subSequence(
                        0,
                        5
                    ) == "radio")
                ) {
                    continue
                }
                val addrs: MutableList<InterfaceAddress> = intf.interfaceAddresses
                for (addr in addrs) {
                    val address = addr.address ?: continue
                    val a: String = (address.hostAddress ?: continue).replace("/", "")
                    if (!address.isLoopbackAddress
                        && (a.startsWith("192.168")
                                || a.startsWith("10.")
                                || a.startsWith("172."))
                    ) {
                        localIp = a
                        localBroadcast = addr.broadcast?.hostAddress
                    }
                }
            }

        } catch (ex: Exception) {
            Log.e("SonicSound", ex.message ?: "LAN discovery failed")
        } // for now eat exceptions

        if (isTv) {
            server = MessageServer(30001)
            server!!.start()
        }
        if (localIp != null && localBroadcast != null) {
            udpServer = UDPServer(
                InetAddress.getByName(localIp),
                InetAddress.getByName(localBroadcast),
                isTv
            )
            if (isTv) {
                CoroutineScope(Dispatchers.IO).launch {
                    udpServer!!.receiveUDP()
                }
            }
        } else {
            Log.w("SonicSound", "No LAN IP found; UDP discovery disabled")
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
