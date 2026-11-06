package app.sonicsound.plugins

import app.sonicsound.models.ParameterException
import app.sonicsound.remote.RemoteDevice
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.PluginCall

/** Plugin methods for signing a TV in with its Quick Connect code. */
class BackendQuickConnect(
    private val responses: BackendResponses,
    private val client: QuickConnectClient,
    private val devicesProvider: () -> Collection<RemoteDevice>,
) {
    private val ipPattern =
        Regex("""^(25[0-5]|2[0-4]\d|[01]?\d\d?)(\.(25[0-5]|2[0-4]\d|[01]?\d\d?)){3}$""")

    fun quickConnectTv(call: PluginCall) {
        try {
            val ip = call.getString("ip")?.trim() ?: throw ParameterException("ip")
            val code = (call.getString("code") ?: throw ParameterException("code")).filter { it.isDigit() }
            if (!ipPattern.matches(ip)) throw ParameterException("ip")
            if (code.length != 6) {
                call.resolve(responses.error("Enter the 6-digit code shown on the TV."))
                return
            }
            val port = call.getInt("wsPort") ?: 30001
            client.signInTv(ip, port, code) { ok, message ->
                call.resolve(if (ok) responses.ok(message) else responses.error(message))
            }
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun getQuickConnectTvs(call: PluginCall) {
        call.resolve(responses.ok(devicesJson()))
    }

    /** TVs currently advertising an open Quick Connect session. */
    fun devicesJson(): JSArray {
        val arr = JSArray()
        devicesProvider().filter { it.quickConnect }.forEach { device ->
            val obj = JSObject()
            obj.put("ip", device.ip)
            obj.put("deviceName", device.deviceName)
            obj.put("wsPort", device.wsPort)
            arr.put(obj)
        }
        return arr
    }
}
