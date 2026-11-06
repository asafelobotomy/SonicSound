package app.sonicsound.remote

import com.google.gson.Gson

/** JSON payloads for LAN Remote discovery (UDP). */
data class RemoteBeacon(
    val type: String = TYPE,
    val role: String,
    val serverUrl: String = "",
    val accountFingerprint: String = "",
    val deviceName: String = "",
    val wsPort: Int = 30001,
    val authProtocol: Int = RemoteAuth.PROTOCOL_VERSION,
    /** True while the TV shows a Quick Connect code and accepts sign-in. */
    val quickConnect: Boolean = false,
) {
    companion object {
        const val TYPE = "sonicsound-remote"
        const val ROLE_TV = "tv"
        const val ROLE_PROBE = "phone-probe"

        private val gson = Gson()

        fun tvBeacon(
            serverUrl: String,
            accountFingerprint: String,
            deviceName: String,
            wsPort: Int = 30001,
            authProtocol: Int = RemoteAuth.PROTOCOL_VERSION,
            quickConnect: Boolean = false,
        ): RemoteBeacon = RemoteBeacon(
            role = ROLE_TV,
            serverUrl = serverUrl,
            accountFingerprint = accountFingerprint,
            deviceName = deviceName,
            wsPort = wsPort,
            authProtocol = authProtocol,
            quickConnect = quickConnect,
        )

        fun probe(): RemoteBeacon = RemoteBeacon(role = ROLE_PROBE)

        fun toJson(beacon: RemoteBeacon): String = gson.toJson(beacon)

        fun fromJson(raw: String): RemoteBeacon? = try {
            gson.fromJson(raw, RemoteBeacon::class.java)
        } catch (_: Exception) {
            null
        }

        fun isLegacyClientPacket(raw: String): Boolean = raw == "sonicsoundClient"

        fun isLegacyServerPacket(raw: String): Boolean = raw == "sonicsoundServer"
    }
}

data class RemoteDevice(
    val ip: String,
    val deviceName: String,
    val serverUrl: String,
    val accountFingerprint: String,
    val wsPort: Int = 30001,
    val quickConnect: Boolean = false,
)
