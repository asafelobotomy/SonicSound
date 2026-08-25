package app.sonicsound.models

class Settings(
    val cacheSize: Int = 0,
    val transcoding: String = "",
    val eqEnabled: Boolean = false,
    val replayGainEnabled: Boolean = false,
    val youtubeApiKey: String = "",
    val youtubeVideosEnabled: Boolean = false,
    /** When false (default), only VEVO / official / artist channels are accepted. */
    val youtubeAllowAnyChannel: Boolean = false,
)
