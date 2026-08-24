package app.sonicsound.plugins

import android.net.Uri
import app.sonicsound.BuildConfig
import com.getcapacitor.JSObject
import com.getcapacitor.PluginCall
import okhttp3.Credentials.basic
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Spotify client-credentials token helper (uses BuildConfig secrets). */
class BackendSpotify(private val responses: BackendResponses) {
    private var spotifyToken: String? = ""

    @Throws(IOException::class)
    fun getSpotifyToken(call: PluginCall) {
        if (spotifyToken == "") {
            val clientId = BuildConfig.SPOTIFY_CLIENT_ID
            val clientSecret = BuildConfig.SPOTIFY_CLIENT_SECRET
            if (clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) {
                call.reject("Spotify is not configured")
                return
            }
            val uriBuilder = Uri.Builder()
                .scheme("https")
                .authority("accounts.spotify.com")
                .appendPath("api")
                .appendPath("token")
            val body: RequestBody = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .build()
            val request: Request = Request.Builder()
                .url(uriBuilder.build().toString())
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Authorization", basic(clientId, clientSecret))
                .post(body)
                .build()
            val client: OkHttpClient = OkHttpClient.Builder()
                .readTimeout(5000, TimeUnit.MILLISECONDS)
                .writeTimeout(5000, TimeUnit.MILLISECONDS)
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                try {
                    val responseBody = response.body?.string()
                        ?: throw Exception("Spotify returned an empty body")
                    spotifyToken = JSObject(responseBody).getString("access_token")
                } catch (e: Exception) {
                    call.resolve(responses.error(e.message))
                    return
                }
            } else {
                call.resolve(responses.error(response.message))
                return
            }
        }
        call.resolve(responses.ok(spotifyToken))
    }
}
