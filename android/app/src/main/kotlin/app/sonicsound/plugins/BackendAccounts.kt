package app.sonicsound.plugins

import app.sonicsound.KeyValueStorage.Companion.getAccounts
import app.sonicsound.KeyValueStorage.Companion.getActiveAccount
import app.sonicsound.KeyValueStorage.Companion.getOfflineMode
import app.sonicsound.KeyValueStorage.Companion.getSettings
import app.sonicsound.KeyValueStorage.Companion.setAccounts
import app.sonicsound.KeyValueStorage.Companion.setActiveAccount
import app.sonicsound.KeyValueStorage.Companion.setOfflineMode
import app.sonicsound.KeyValueStorage.Companion.setSettings
import app.sonicsound.models.Account
import app.sonicsound.models.ParameterException
import app.sonicsound.models.Settings
import app.sonicsound.playback.AudioProfile
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
            call.resolve(responses.ok(account.withoutPassword()))
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

    fun deleteAccount(call: PluginCall) {
        try {
            val url = call.getString("url") ?: throw ParameterException("url")
            val remaining = getAccounts().filterNot { it.url == url }
            setAccounts(remaining)
            val active = getActiveAccount()
            if (active.url == url) {
                setActiveAccount(Account(null, "", "", "", false))
            }
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun getCameraPermissionStatus(call: PluginCall) {
        call.resolve(responses.error("Camera disabled"))
    }

    fun getCameraPermission(call: PluginCall) {
        call.resolve(responses.error("Camera disabled"))
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
            call.resolve(responses.ok(getActiveAccount().withoutPassword()))
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
            val audioProfile = call.getString("audioProfile") ?: current.audioProfile
            val vinylCondition = call.getString("vinylCondition") ?: current.vinylCondition
            val replayGainEnabled =
                call.getBoolean("replayGainEnabled") ?: current.replayGainEnabled
            val youtubeApiKey = call.getString("youtubeApiKey") ?: current.youtubeApiKey
            val youtubeVideosEnabled =
                call.getBoolean("youtubeVideosEnabled") ?: current.youtubeVideosEnabled
            val youtubeAllowAnyChannel =
                call.getBoolean("youtubeAllowAnyChannel") ?: current.youtubeAllowAnyChannel
            // Blank from JS means "keep native" — getSettings redacts secrets, so a round-trip
            // must not wipe OAuth credentials stored only on the native side.
            val youtubeOauthClientId =
                nonBlankOr(call.getString("youtubeOauthClientId"), current.youtubeOauthClientId)
            val youtubeOauthClientSecret =
                nonBlankOr(
                    call.getString("youtubeOauthClientSecret"),
                    current.youtubeOauthClientSecret,
                )
            val youtubeAccessToken =
                if (call.hasOption("youtubeAccessToken")) {
                    nonBlankOr(call.getString("youtubeAccessToken"), current.youtubeAccessToken)
                } else {
                    current.youtubeAccessToken
                }
            val youtubeRefreshToken =
                if (call.hasOption("youtubeRefreshToken")) {
                    nonBlankOr(call.getString("youtubeRefreshToken"), current.youtubeRefreshToken)
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
                    audioProfile = audioProfile,
                    vinylCondition = vinylCondition,
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
                ).let { AudioProfile.normalize(it) }
            )
            call.resolve(responses.ok(""))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun getSettings(call: PluginCall) {
        try {
            call.resolve(responses.ok(getSettings().withoutSecrets()))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    fun getAccounts(call: PluginCall) {
        try {
            call.resolve(responses.okArray(getAccounts().map { it.withoutPassword() }))
        } catch (e: Exception) {
            call.resolve(responses.error(e.message))
        }
    }

    private fun Account.withoutPassword(): Account =
        Account(username, "", url, type, usePlaintext)

    private fun Settings.withoutSecrets(): Settings =
        copy(
            youtubeOauthClientSecret = "",
            youtubeAccessToken = "",
            youtubeRefreshToken = "",
        )

    private fun nonBlankOr(incoming: String?, fallback: String): String =
        incoming?.takeIf { it.isNotBlank() } ?: fallback
}
