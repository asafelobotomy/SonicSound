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
import app.sonicsound.models.Account
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

    fun logout(call: PluginCall) {
        try {
            val cleared = Account(null, "", "", "", false)
            setActiveAccount(cleared)
            call.resolve(responses.ok(cleared))
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
            MainActivity.requestPermissionLauncher?.launch("android.permission.CAMERA")
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
            val current = getSettings()
            val eqEnabled = call.getBoolean("eqEnabled") ?: current.eqEnabled
            val replayGainEnabled =
                call.getBoolean("replayGainEnabled") ?: current.replayGainEnabled
            val youtubeApiKey = call.getString("youtubeApiKey") ?: current.youtubeApiKey
            val youtubeVideosEnabled =
                call.getBoolean("youtubeVideosEnabled") ?: current.youtubeVideosEnabled
            val youtubeAllowAnyChannel =
                call.getBoolean("youtubeAllowAnyChannel") ?: current.youtubeAllowAnyChannel
            val youtubeOauthClientId =
                call.getString("youtubeOauthClientId") ?: current.youtubeOauthClientId
            val youtubeOauthClientSecret =
                call.getString("youtubeOauthClientSecret") ?: current.youtubeOauthClientSecret
            val youtubeAccessToken =
                if (call.hasOption("youtubeAccessToken")) {
                    call.getString("youtubeAccessToken").orEmpty()
                } else {
                    current.youtubeAccessToken
                }
            val youtubeRefreshToken =
                if (call.hasOption("youtubeRefreshToken")) {
                    call.getString("youtubeRefreshToken").orEmpty()
                } else {
                    current.youtubeRefreshToken
                }
            val youtubeTokenExpiryMs =
                call.getLong("youtubeTokenExpiryMs") ?: current.youtubeTokenExpiryMs
            val fullscreenVisualizer =
                call.getString("fullscreenVisualizer") ?: current.fullscreenVisualizer
            val fullscreenSolidColor =
                call.getString("fullscreenSolidColor") ?: current.fullscreenSolidColor
            val dvdSpeed = call.getString("dvdSpeed") ?: current.dvdSpeed
            val fullscreenShowClock =
                call.getBoolean("fullscreenShowClock") ?: current.fullscreenShowClock
            val fullscreenShowDate =
                call.getBoolean("fullscreenShowDate") ?: current.fullscreenShowDate
            setSettings(
                Settings(
                    cacheSize = cacheSize,
                    transcoding = transcoding,
                    eqEnabled = eqEnabled,
                    replayGainEnabled = replayGainEnabled,
                    youtubeApiKey = youtubeApiKey,
                    youtubeVideosEnabled = youtubeVideosEnabled,
                    youtubeAllowAnyChannel = youtubeAllowAnyChannel,
                    youtubeOauthClientId = youtubeOauthClientId,
                    youtubeOauthClientSecret = youtubeOauthClientSecret,
                    youtubeAccessToken = youtubeAccessToken,
                    youtubeRefreshToken = youtubeRefreshToken,
                    youtubeTokenExpiryMs = youtubeTokenExpiryMs,
                    fullscreenVisualizer = fullscreenVisualizer,
                    fullscreenSolidColor = fullscreenSolidColor,
                    dvdSpeed = dvdSpeed,
                    fullscreenShowClock = fullscreenShowClock,
                    fullscreenShowDate = fullscreenShowDate,
                )
            )
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
