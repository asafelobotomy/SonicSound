package app.sonicsound.subsonic

import android.net.Uri
import app.sonicsound.models.Account
import com.getcapacitor.JSObject
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Shared OkHttp helpers for Subsonic REST calls.
 */
class SubsonicHttp(
    private val accountProvider: () -> Account
) {
    val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(5000, TimeUnit.MILLISECONDS)
        .writeTimeout(5000, TimeUnit.MILLISECONDS)
        .build()

    val account: Account
        get() = accountProvider()

    fun buildUri(path: List<String>, parameters: HashMap<String, String>? = null): Uri {
        val uriBuilder = Uri.parse(account.url).buildUpon()
        for (p in path) {
            uriBuilder.appendPath(p)
        }
        if (parameters != null) {
            for (key in parameters.keys) {
                val value = parameters[key] ?: continue
                if (value.contains(",")) {
                    value.split(",").forEach {
                        uriBuilder.appendQueryParameter(key, it.trim())
                    }
                } else {
                    uriBuilder.appendQueryParameter(key, value)
                }
            }
        }
        return uriBuilder.build()
    }

    fun execute(request: Request): Response = client.newCall(request).execute()

    fun get(url: String): Response =
        execute(Request.Builder().url(url).get().build())

    inline fun <reified T : Any> makeSubsonicRequest(
        path: List<String>,
        parameters: HashMap<String, String>?,
        emptyResponse: Boolean = false
    ): T? {
        val request = Request.Builder()
            .url(buildUri(path, parameters).toString())
            .get()
            .build()
        val response = execute(request)
        if (!response.isSuccessful) {
            throw Exception(response.message)
        }
        val body = response.body?.string()
        val realResponse = JSObject(body).get("subsonic-response").toString()
        val status = JSObject(body).getJSObject("subsonic-response")?.getString("status")
            ?: throw Exception("There was an internal error in the server. Please check your server logs.")
        if (status == "failed") {
            throw Exception(
                JSObject(body).getJSObject("subsonic-response")?.getJSObject("error")
                    ?.getString("message")
            )
        }
        if (emptyResponse) {
            return null
        }
        return Gson().fromJson(realResponse, T::class.java)
    }
}
