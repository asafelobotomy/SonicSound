package app.sonicsound.subsonic

import android.net.Uri
import app.sonicsound.models.Artist
import app.sonicsound.models.ArtistInfo
import com.getcapacitor.JSObject
import okhttp3.Request
import okhttp3.Response

/** Spotify / cover-art resolution helpers for [SubsonicLibrary]. */
internal class SubsonicArtistArt(
    private val http: SubsonicHttp,
    private val coverCache: SubsonicCoverCache,
    private val getArtist: (String) -> Artist,
    private val getArtistInfo: (String) -> ArtistInfo,
    private val getSpotifyToken: () -> String,
) {
    fun getSpotifyArtistArt(name: String): String? {
        val uriBuilder = Uri.parse("https://api.spotify.com/v1/search").buildUpon()
        uriBuilder.appendQueryParameter("q", name)
        uriBuilder.appendQueryParameter("type", "artist")
        val request: Request = Request.Builder()
            .url(uriBuilder.build().toString())
            .get()
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer ${getSpotifyToken()}")
            .build()
        val response: Response = http.execute(request)
        if (!response.isSuccessful) throw Exception(response.message)
        val body = response.body?.string()
        val realResponse =
            JSObject(body).getJSObject("artists")?.getJSONArray("items")
                ?: return null
        val first = realResponse.getJSONObject(0) ?: return null
        if (first.getString("name") == name) {
            val images = first.getJSONArray("images")
            if (images.length() == 0) return null
            return images.getJSONObject(0)?.getString("url")
        }
        return null
    }

    fun getArtistArt(id: String): String {
        val artist = getArtist(id)
        val cover = artist.coverArt
        val fromHttp = cover.takeIf { it.startsWith("http", ignoreCase = true) }
        val fromInfo = runCatching { getArtistInfo(id).largeImageUrl }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        val fromCoverId = when {
            cover.isNotBlank() && !cover.startsWith("http", ignoreCase = true) ->
                coverCache.getAlbumArt(cover)
            else -> coverCache.getAlbumArt(id)
        }
        val fromSpotify = runCatching { getSpotifyArtistArt(artist.name) }.getOrNull()
        val art = fromHttp ?: fromCoverId.takeIf { it.isNotBlank() } ?: fromInfo ?: fromSpotify
            ?: return ""
        if (art.startsWith("http", ignoreCase = true) &&
            !art.contains("/rest/getCoverArt")
        ) {
            coverCache.cacheRemoteImage(
                art,
                coverCache.getArtistArtsDirectory(),
                coverCache.getLocalArtistArtUri(id),
            )
        }
        return art
    }
}
