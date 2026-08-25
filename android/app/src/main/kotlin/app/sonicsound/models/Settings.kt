package app.sonicsound.models

data class Settings(
    val cacheSize: Int = 0,
    val transcoding: String = "",
    val eqEnabled: Boolean = false,
    val replayGainEnabled: Boolean = false,
    val youtubeApiKey: String = "",
    val youtubeVideosEnabled: Boolean = false,
    /** When false (default), only VEVO / official / artist channels are accepted. */
    val youtubeAllowAnyChannel: Boolean = false,
    /** Google Cloud OAuth client (TVs and Limited Input devices). */
    val youtubeOauthClientId: String = "",
    val youtubeOauthClientSecret: String = "",
    val youtubeAccessToken: String = "",
    val youtubeRefreshToken: String = "",
    val youtubeTokenExpiryMs: Long = 0L,
)
