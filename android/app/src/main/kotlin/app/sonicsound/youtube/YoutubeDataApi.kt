package app.sonicsound.youtube

import app.sonicsound.models.YoutubeVideo
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Official YouTube Data API v3 search (OAuth Bearer and/or API key). */
object YoutubeDataApi {
    fun search(
        query: String,
        maxResults: Int = 20,
        accessToken: String = "",
        apiKey: String = "",
    ): List<YoutubeVideo> {
        if (query.isBlank()) return emptyList()
        if (accessToken.isBlank() && apiKey.isBlank()) return emptyList()
        val q = URLEncoder.encode(query, Charsets.UTF_8.name())
        val authQs = if (apiKey.isNotBlank()) {
            "&key=${URLEncoder.encode(apiKey, Charsets.UTF_8.name())}"
        } else {
            ""
        }
        val url =
            "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video" +
                "&maxResults=$maxResults&q=$q$authQs"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            if (accessToken.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
        }
        try {
            if (conn.responseCode !in 200..299) return emptyList()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val items = JSONObject(body).optJSONArray("items") ?: return emptyList()
            val out = mutableListOf<YoutubeVideo>()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val id = item.getJSONObject("id").optString("videoId")
                if (id.isBlank()) continue
                val sn = item.getJSONObject("snippet")
                val thumbs = sn.optJSONObject("thumbnails")
                val thumb = thumbs?.optJSONObject("medium")?.optString("url")
                    ?: thumbs?.optJSONObject("default")?.optString("url")
                    ?: ""
                out.add(
                    YoutubeVideo(
                        id = id,
                        title = sn.optString("title"),
                        channel = sn.optString("channelTitle"),
                        thumbnailUrl = thumb,
                    )
                )
            }
            return out
        } finally {
            conn.disconnect()
        }
    }

    fun searchAuthed(
        query: String,
        maxResults: Int = 20,
    ): List<YoutubeVideo> {
        val token = YoutubeOAuth.validAccessToken()
        val key = app.sonicsound.KeyValueStorage.getSettings().youtubeApiKey
        return search(query, maxResults, accessToken = token, apiKey = key)
    }

    fun searchMusicVideo(
        artist: String,
        title: String,
        allowAnyChannel: Boolean = false,
    ): YoutubeVideo? {
        val queries = listOf(
            "$artist $title Official Music Video",
            "$artist $title official video",
            "\"$artist\" \"$title\"",
        )
        val candidates = linkedMapOf<String, YoutubeVideo>()
        for (q in queries) {
            for (v in searchAuthed(q, maxResults = 8)) {
                candidates.putIfAbsent(v.id, v)
            }
            if (candidates.size >= 12) break
        }
        return candidates.values
            .map { it to score(it, artist, title, allowAnyChannel) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun score(
        video: YoutubeVideo,
        artist: String,
        title: String,
        allowAnyChannel: Boolean,
    ): Int {
        val vt = video.title.lowercase(Locale.US)
        val ch = video.channel.lowercase(Locale.US)
        val a = artist.lowercase(Locale.US).trim()
        val t = title.lowercase(Locale.US).trim()
        if (t.isBlank() || !vt.contains(t)) return 0
        val junk = listOf(
            "lyric", "lyrics", "audio only", "cover", "karaoke", "reaction",
            "fan made", "fanmade", "nightcore", "slowed", "sped up", "instrumental",
        )
        if (!allowAnyChannel && junk.any { vt.contains(it) }) return 0
        val officialTitle = listOf(
            "official music video", "official video", "official mv",
        ).any { vt.contains(it) }
        val vevo = ch.endsWith("vevo") || ch.contains("vevo")
        val artistChannel = a.isNotBlank() && (
            ch == a || ch.startsWith("$a ") || ch.contains("$a -") ||
                ch.contains("$a official") || ch.contains("official $a")
            )
        val officialInChannel = ch.contains("official")
        val trusted = vevo || officialTitle || artistChannel || officialInChannel
        if (!allowAnyChannel && !trusted) return 0
        var s = 40
        if (a.isNotBlank() && (vt.contains(a) || ch.contains(a))) s += 25
        if (officialTitle) s += 35
        if (vevo) s += 40
        if (artistChannel) s += 30
        if (officialInChannel) s += 15
        if (ch.contains("- topic")) s -= 40
        return s
    }
}
