package app.sonicsound.models

/** Fullscreen Now Playing visualizer modes. */
object FullscreenVisualizer {
    const val ART_BACKGROUND = "art_background"
    const val ART_BLACK = "art_black"
    const val ART_SOLID = "art_solid"
    const val DVD = "dvd"

    const val SPEED_SLOW = "slow"
    const val SPEED_DEFAULT = "default"
    const val SPEED_FAST = "fast"

    /** Default primary solid color (red). */
    const val DEFAULT_SOLID = "#E53935"
}

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
    /** Fullscreen visualizer: art_background | art_black | art_solid | dvd */
    val fullscreenVisualizer: String = FullscreenVisualizer.ART_BACKGROUND,
    /** Hex color used when fullscreenVisualizer is art_solid. */
    val fullscreenSolidColor: String = FullscreenVisualizer.DEFAULT_SOLID,
    /** DVD bounce speed: slow | default | fast */
    val dvdSpeed: String = FullscreenVisualizer.SPEED_DEFAULT,
    /** Show wall-clock time (top-left) in fullscreen. */
    val fullscreenShowClock: Boolean = false,
    /** Show calendar date (top-left) in fullscreen. */
    val fullscreenShowDate: Boolean = false,
)
