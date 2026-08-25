package app.sonicsound.youtube

import app.sonicsound.KeyValueStorage
import app.sonicsound.models.Settings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Google OAuth 2.0 device-code flow for Android TV / limited-input devices.
 * User signs in at google.com/device; tokens authorize YouTube Data API calls.
 */
object YoutubeOAuth {
    const val SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
    private const val CODE_URL = "https://oauth2.googleapis.com/device/code"
    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"

    data class DeviceAuth(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val intervalSec: Int,
        val expiresInSec: Int,
    )

    data class Tokens(
        val accessToken: String,
        val refreshToken: String?,
        val expiresInSec: Int,
    )

    fun startDeviceAuth(clientId: String): DeviceAuth {
        require(clientId.isNotBlank()) { "YouTube OAuth client ID is required" }
        val json = postForm(
            CODE_URL,
            mapOf("client_id" to clientId, "scope" to SCOPE),
        )
        return DeviceAuth(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verificationUrl = json.optString(
                "verification_url",
                "https://www.google.com/device"
            ),
            intervalSec = json.optInt("interval", 5).coerceAtLeast(3),
            expiresInSec = json.optInt("expires_in", 1800),
        )
    }

    fun pollToken(
        clientId: String,
        clientSecret: String,
        deviceCode: String,
    ): Tokens? {
        val params = mutableMapOf(
            "client_id" to clientId,
            "device_code" to deviceCode,
            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
        )
        if (clientSecret.isNotBlank()) params["client_secret"] = clientSecret
        val json = try {
            postForm(TOKEN_URL, params)
        } catch (_: Exception) {
            return null
        }
        if (json.has("error")) {
            val err = json.optString("error")
            if (err == "authorization_pending" || err == "slow_down") return null
            throw Exception(json.optString("error_description", err))
        }
        return Tokens(
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token").ifBlank { null },
            expiresInSec = json.optInt("expires_in", 3600),
        )
    }

    fun refreshAccessToken(
        clientId: String,
        clientSecret: String,
        refreshToken: String,
    ): Tokens {
        val params = mutableMapOf(
            "client_id" to clientId,
            "refresh_token" to refreshToken,
            "grant_type" to "refresh_token",
        )
        if (clientSecret.isNotBlank()) params["client_secret"] = clientSecret
        val json = postForm(TOKEN_URL, params)
        return Tokens(
            accessToken = json.getString("access_token"),
            refreshToken = refreshToken,
            expiresInSec = json.optInt("expires_in", 3600),
        )
    }

    fun validAccessToken(): String {
        val s = KeyValueStorage.getSettings()
        if (s.youtubeAccessToken.isBlank()) return ""
        val now = System.currentTimeMillis()
        if (now < s.youtubeTokenExpiryMs - 60_000) return s.youtubeAccessToken
        if (s.youtubeRefreshToken.isBlank() || s.youtubeOauthClientId.isBlank()) {
            return s.youtubeAccessToken
        }
        return try {
            val t = refreshAccessToken(
                s.youtubeOauthClientId,
                s.youtubeOauthClientSecret,
                s.youtubeRefreshToken,
            )
            persistTokens(s, t)
            t.accessToken
        } catch (_: Exception) {
            s.youtubeAccessToken
        }
    }

    fun persistTokens(current: Settings, tokens: Tokens) {
        KeyValueStorage.setSettings(
            current.copy(
                youtubeAccessToken = tokens.accessToken,
                youtubeRefreshToken = tokens.refreshToken ?: current.youtubeRefreshToken,
                youtubeTokenExpiryMs =
                    System.currentTimeMillis() + tokens.expiresInSec * 1000L,
            )
        )
    }

    fun clearTokens(current: Settings) {
        KeyValueStorage.setSettings(
            current.copy(
                youtubeAccessToken = "",
                youtubeRefreshToken = "",
                youtubeTokenExpiryMs = 0L,
            )
        )
    }

    fun hasSession(): Boolean {
        val s = KeyValueStorage.getSettings()
        return s.youtubeAccessToken.isNotBlank() || s.youtubeRefreshToken.isNotBlank()
    }

    private fun postForm(url: String, params: Map<String, String>): JSONObject {
        val encoded = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            conn.outputStream.use { it.write(encoded.toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) {
                conn.inputStream
            } else {
                conn.errorStream ?: conn.inputStream
            }
            val body = stream.bufferedReader().use { it.readText() }
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }
}
