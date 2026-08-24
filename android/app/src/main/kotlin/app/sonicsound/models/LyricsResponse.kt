package app.sonicsound.models

class Lyrics(
    val value: String? = null,
    val artist: String? = null,
    val title: String? = null
)

class LyricsResponse(val lyrics: Lyrics? = null) : SubsonicResponse()
