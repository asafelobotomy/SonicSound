package app.sonicsound.subsonic

import android.net.Uri
import app.sonicsound.KeyValueStorage
import app.sonicsound.models.Account
import app.sonicsound.models.ArtistsSubsonicResponse
import app.sonicsound.models.BasicParams
import com.getcapacitor.JSObject
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Auth params, salt/token generation, and Subsonic API version negotiation.
 */
object SubsonicAuth {
    val API_VERSIONS = listOf("1.16.1", "1.15.0", "1.13.0", "1.12.0")

    /** Negotiated API version for the active session; defaults to newest. */
    @Volatile
    var apiVersion: String = API_VERSIONS.first()
        private set

    private val secureRandom = SecureRandom()

    fun randomSalt(length: Int = 12): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length)
            .map { alphabet[secureRandom.nextInt(alphabet.length)] }
            .joinToString("")
    }

    fun md5Token(password: String, salt: String): String {
        val saltedPassword = "$password$salt"
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(saltedPassword.toByteArray()))
            .toString(16)
            .padStart(32, '0')
    }

    fun getBasicParams(account: Account, version: String = apiVersion): BasicParams {
        val salt = randomSalt()
        val hash = md5Token(account.password, salt)
        return BasicParams(
            account.username ?: "",
            if (account.usePlaintext) null else hash,
            if (account.usePlaintext) null else salt,
            version,
            "sonicsound",
            "json",
            if (account.usePlaintext) account.password else null
        )
    }

    /**
     * Login / ping against the server, trying API versions newest-first until one succeeds.
     */
    fun login(
        client: OkHttpClient,
        username: String,
        password: String,
        url: String,
        usePlaintext: Boolean
    ): Account {
        var lastError: Exception? = null
        for (version in API_VERSIONS) {
            try {
                val account = attemptLogin(client, username, password, url, usePlaintext, version)
                apiVersion = version
                persistAccount(account)
                return account
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: Exception("Login failed for all API versions")
    }

    /**
     * Ping with version negotiation using an already-configured account.
     * Updates [apiVersion] on success.
     */
    fun ping(client: OkHttpClient, account: Account): Boolean {
        var lastError: Exception? = null
        for (version in API_VERSIONS) {
            try {
                val params = getBasicParams(account, version).asMap()
                val uriBuilder = Uri.parse(account.url).buildUpon()
                    .appendPath("rest")
                    .appendPath("ping")
                for ((key, value) in params) {
                    uriBuilder.appendQueryParameter(key, value)
                }
                val response = client.newCall(
                    Request.Builder().url(uriBuilder.build().toString()).get().build()
                ).execute()
                if (!response.isSuccessful) {
                    throw Exception(response.message)
                }
                val body = response.body?.string()
                    ?: throw Exception("Empty ping response")
                val status = JSObject(body).getJSObject("subsonic-response")?.getString("status")
                if (status != "ok") {
                    val message = JSObject(body).getJSObject("subsonic-response")
                        ?.getJSObject("error")?.getString("message")
                    throw Exception(message ?: "Ping failed")
                }
                apiVersion = version
                return true
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: Exception("Ping failed for all API versions")
    }

    private fun attemptLogin(
        client: OkHttpClient,
        username: String,
        password: String,
        url: String,
        usePlaintext: Boolean,
        version: String
    ): Account {
        val salt = randomSalt()
        val hash = md5Token(password, salt)
        val basicParams = BasicParams(
            username,
            if (usePlaintext) null else hash,
            if (usePlaintext) null else salt,
            version,
            "sonicsound",
            "json",
            if (usePlaintext) password else null
        )
        val uriBuilder = Uri.parse(url).buildUpon()
            .appendPath("rest")
            .appendPath("getArtists")
        for ((key, value) in basicParams.asMap()) {
            uriBuilder.appendQueryParameter(key, value)
        }

        val response = client.newCall(
            Request.Builder().url(uriBuilder.build().toString()).get().build()
        ).execute()
        if (!response.isSuccessful) {
            throw Exception("There was an error reaching the server. Please check your connection.")
        }
        val body = response.body?.string()
        val realResponse = JSObject(body).get("subsonic-response").toString()
        val ret = Gson().fromJson(realResponse, ArtistsSubsonicResponse::class.java)
        if (ret.status != "ok") {
            throw Exception(ret.error?.message)
        }
        return Account(username, password, url, ret.type ?: "Unknown Server", usePlaintext)
    }

    private fun persistAccount(account: Account) {
        KeyValueStorage.setActiveAccount(account)
        val exists = KeyValueStorage.getAccounts().any { it.url == account.url }
        val list: MutableList<Account> = if (exists) {
            KeyValueStorage.getAccounts().filter { it.url != account.url }.toMutableList()
        } else {
            KeyValueStorage.getAccounts().toMutableList()
        }
        list.add(account)
        KeyValueStorage.setAccounts(list)
    }
}
