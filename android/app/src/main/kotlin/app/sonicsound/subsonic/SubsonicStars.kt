package app.sonicsound.subsonic

import app.sonicsound.models.SubsonicResponse

/** Subsonic/Navidrome star (like) API. */
class SubsonicStars(
    private val http: SubsonicHttp,
    private val paramsProvider: () -> HashMap<String, String>,
) {
    fun star(id: String) {
        val p = paramsProvider()
        p["id"] = id
        http.makeSubsonicRequest<SubsonicResponse>(listOf("rest", "star"), p, true)
    }

    fun unstar(id: String) {
        val p = paramsProvider()
        p["id"] = id
        http.makeSubsonicRequest<SubsonicResponse>(listOf("rest", "unstar"), p, true)
    }
}
