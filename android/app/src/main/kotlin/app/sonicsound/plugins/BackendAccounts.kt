package app.sonicsound.plugins

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.sonicsound.App
import app.sonicsound.KeyValueStorage.Companion.getAccounts
import app.sonicsound.KeyValueStorage.Companion.getActiveAccount
import app.sonicsound.KeyValueStorage.Companion.getOfflineMode
import app.sonicsound.KeyValueStorage.Companion.getSettings
import app.sonicsound.KeyValueStorage.Companion.setActiveAccount
import app.sonicsound.KeyValueStorage.Companion.setOfflineMode
import app.sonicsound.KeyValueStorage.Companion.setSettings
import app.sonicsound.MainActivity
import app.sonicsound.models.ParameterException
import app.sonicsound.models.Settings
import app.sonicsound.subsonic.SubsonicClient
import com.getcapacitor.PluginCall

/** Account, settings, offline mode, and camera permission helpers. */
class BackendAccounts(
    private val responses: BackendResponses,
    private val clientProvider: () -> SubsonicClient?
) {
    fun login(call: PluginCall) {
        val data = call.data
        val username = data.getString("username") ?: throw ParameterException("username")
        val password = data.getString("password") ?: throw Exception("password")
        val url = data.getString("url") ?: throw Exception("url")
        val usePlaintext = data.getBoolean("usePlaintext")
        try {
            val account = clientProvider()!!.login(username, password, url, usePlaintext)
            setActiveAccount(account)
            call.resolve(responses.ok(account))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun getCameraPermissionStatus(call: PluginCall) {
        if (ContextCompat.checkSelfPermission(
                App.context,
                "android.permission.CAMERA"
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            call.resolve(responses.ok(""))
        } else {
            call.resolve(
                responses.error(
                    "Please provide permission to use the camera. This is needed for the QR scanner to work."
                )
            )
        }
    }

    fun getCameraPermission(call: PluginCall) {
        if (ContextCompat.checkSelfPermission(
                App.context,
                "android.permission.CAMERA"
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            MainActivity.requestPermissionLauncher.launch("android.permission.CAMERA")
        }
        call.resolve(responses.ok(""))
    }

    fun getOfflineMode(call: PluginCall) {
        try {
            call.resolve(responses.ok(getOfflineMode()))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun setOfflineMode(call: PluginCall) {
        try {
            val mode = java.lang.Boolean.TRUE == call.getBoolean("value")
            setOfflineMode(mode)
            call.resolve(responses.ok(mode))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun getActiveAccount(call: PluginCall) {
        try {
            call.resolve(responses.ok(getActiveAccount()))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun setSettings(call: PluginCall) {
        try {
            val cacheSize = call.getInt("cacheSize") ?: throw ParameterException("cacheSize")
            val transcoding = call.getString("transcoding") ?: ""
            setSettings(Settings(cacheSize, transcoding))
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun getSettings(call: PluginCall) {
        try {
            call.resolve(responses.ok(getSettings()))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun getAccounts(call: PluginCall) {
        try {
            call.resolve(responses.okArray(getAccounts()))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }
}
