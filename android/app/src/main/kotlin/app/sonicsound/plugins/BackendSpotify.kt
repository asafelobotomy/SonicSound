package app.sonicsound.plugins

import com.getcapacitor.PluginCall

/**
 * Spotify similarity helpers.
 *
 * Client-credentials token minting requires a client secret, which must not be
 * baked into the APK. Native Spotify art is disabled until a server-side proxy
 * (or another non-secret flow) is available.
 */
class BackendSpotify(private val responses: BackendResponses) {
    fun getSpotifyToken(call: PluginCall) {
        call.resolve(
            responses.error(
                "Spotify similarity requires a server-side token proxy; " +
                    "client secrets are not shipped in the APK",
            ),
        )
    }
}
