package app.sonicsound

import android.os.Build
import android.util.Log
import app.sonicsound.remote.RemoteAuth
import app.sonicsound.remote.RemoteBeacon
import app.sonicsound.remote.RemoteDevice
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UDPServer(
    val ipAddress: InetAddress,
    private val broadcastAddress: InetAddress,
    val server: Boolean,
) : IBroadcastObserver {
    private var broadcastSocket: DatagramSocket? = null
    private var receiving: Boolean = false
    private val gson = Gson()

    init {
        try {
            broadcastSocket = DatagramSocket(30002, InetAddress.getByName("0.0.0.0"))
            broadcastSocket!!.broadcast = true
            Globals.RegisterObserver(this)
        } catch (e: Exception) {
            broadcastSocket = null
            Globals.NotifyObservers("EX", "Couldn't start UDP Server")
        }
    }

    fun close() {
        broadcastSocket?.close()
        Globals.UnregisterObserver(this)
    }

    private fun packetText(packet: DatagramPacket): String {
        var realLength = packet.length - 1
        while (realLength >= 0 && packet.data[realLength].toInt() == 0) {
            realLength--
        }
        if (realLength < 0) return ""
        return String(packet.data, 0, realLength + 1, Charsets.UTF_8).trim()
    }

    private fun sendRaw(payload: String, address: InetAddress, port: Int) {
        if (broadcastSocket == null) return
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val packet = DatagramPacket(bytes, bytes.size, address, port)
        broadcastSocket!!.send(packet)
    }

    private fun tvBeaconJson(): String {
        val account = KeyValueStorage.getActiveAccount()
        val deviceName = KeyValueStorage.getRemoteDeviceName()
            .ifBlank { Build.MODEL ?: "Android TV" }
        return RemoteBeacon.toJson(
            RemoteBeacon.tvBeacon(
                serverUrl = account.url,
                accountFingerprint = RemoteAuth.accountFingerprint(account.url, account.username),
                deviceName = deviceName,
            )
        )
    }

    private fun sendProbe() {
        sendRaw(RemoteBeacon.toJson(RemoteBeacon.probe()), broadcastAddress, 30002)
    }

    private fun sendTvBeacon() {
        sendRaw(tvBeaconJson(), broadcastAddress, 30002)
    }

    private fun respondTvBeaconTo(replyTo: InetAddress) {
        val json = tvBeaconJson()
        sendRaw(json, broadcastAddress, 30002)
        sendRaw(json, replyTo, 30002)
    }

    private fun notifyPhoneDevice(ip: String, beacon: RemoteBeacon) {
        val device = RemoteDevice(
            ip = ip,
            deviceName = beacon.deviceName.ifBlank { ip },
            serverUrl = beacon.serverUrl,
            accountFingerprint = beacon.accountFingerprint,
            wsPort = beacon.wsPort,
        )
        Globals.NotifyObservers("REMOTE_DEVICE", gson.toJson(device))
    }

    fun receiveUDP() {
        if (broadcastSocket == null || receiving) return
        try {
            val buffer = ByteArray(1500)
            val packet = DatagramPacket(buffer, buffer.size)
            receiving = true
            broadcastSocket!!.receive(packet)
            receiving = false
            if (packet.address == ipAddress) {
                scheduleReceive()
                return
            }
            val text = packetText(packet)
            Log.i("SonicSound UDP", "Packet: $text from ${packet.address.hostAddress}")

            val beacon = RemoteBeacon.fromJson(text)
            when {
                server && (beacon?.role == RemoteBeacon.ROLE_PROBE || RemoteBeacon.isLegacyClientPacket(text)) -> {
                    respondTvBeaconTo(packet.address)
                }
                !server && beacon?.role == RemoteBeacon.ROLE_TV -> {
                    val ip = packet.address.hostAddress ?: return scheduleReceive()
                    notifyPhoneDevice(ip, beacon)
                }
                !server && RemoteBeacon.isLegacyServerPacket(text) -> {
                    val ip = packet.address.hostAddress
                    if (!ip.isNullOrBlank()) {
                        Globals.NotifyObservers("MStvPacket", "{\"ip\":\"$ip\"}")
                    }
                }
            }
            scheduleReceive()
        } catch (e: Exception) {
            receiving = false
            Log.w("SonicSound UDP", e.message ?: "receive failed")
        }
    }

    private fun scheduleReceive() {
        if (!server && broadcastSocket != null) {
            CoroutineScope(Dispatchers.IO).launch { receiveUDP() }
        } else if (server) {
            CoroutineScope(Dispatchers.IO).launch { receiveUDP() }
        }
    }

    override fun update(action: String?, value: String?) {
        when (action) {
            "SENDUDP" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    scheduleReceive()
                    if (server) {
                        respondTvBeaconTo(broadcastAddress)
                    } else {
                        sendProbe()
                    }
                }
            }
            "REMOTE_BEACON" -> if (server) {
                CoroutineScope(Dispatchers.IO).launch { sendTvBeacon() }
            }
        }
    }
}
