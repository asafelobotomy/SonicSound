package app.sonicsound.models

/** Fullscreen Now Playing visualizer modes. */
object FullscreenVisualizer {
    const val ART_BACKGROUND = "art_background"
    const val ART_BLACK = "art_black"
    const val ART_SOLID = "art_solid"
    const val DVD = "dvd"

    // Legacy Windows Media Player visualizations
    const val WMP_BARS = "wmp_bars"
    const val WMP_SCOPE = "wmp_scope"
    const val WMP_OCEAN_MIST = "wmp_ocean_mist"
    const val WMP_FIRE_STORM = "wmp_fire_storm"
    const val WMP_BATTERY = "wmp_battery"
    const val WMP_ALCHEMY = "wmp_alchemy"
    const val WMP_AMBIENCE = "wmp_ambience"
    const val WMP_PARTICLE = "wmp_particle"
    const val WMP_PLENOPTIC = "wmp_plenoptic"
    const val WMP_SPIKES = "wmp_spikes"
    const val WMP_MUSICAL_COLORS = "wmp_musical_colors"
    const val WMP_BLAZING_COLORS = "wmp_blazing_colors"
    const val WMP_COLOR_CUBES = "wmp_color_cubes"
    const val WMP_PULSING_COLORS = "wmp_pulsing_colors"
    const val WMP_STARTIME = "wmp_startime"
    const val WMP_SNOWTIME = "wmp_snowtime"

    const val SPEED_SLOW = "slow"
    const val SPEED_DEFAULT = "default"
    const val SPEED_FAST = "fast"

    /** Default primary solid color (red). */
    const val DEFAULT_SOLID = "#E53935"

    fun isWmpMode(mode: String): Boolean = mode.startsWith("wmp_")

    val ALL_MODES: List<String> = listOf(
        ART_BACKGROUND,
        ART_BLACK,
        ART_SOLID,
        DVD,
        WMP_BARS,
        WMP_SCOPE,
        WMP_OCEAN_MIST,
        WMP_FIRE_STORM,
        WMP_BATTERY,
        WMP_ALCHEMY,
        WMP_AMBIENCE,
        WMP_PARTICLE,
        WMP_PLENOPTIC,
        WMP_SPIKES,
        WMP_MUSICAL_COLORS,
        WMP_BLAZING_COLORS,
        WMP_COLOR_CUBES,
        WMP_PULSING_COLORS,
        WMP_STARTIME,
        WMP_SNOWTIME,
    )
}

data class Settings(
    val cacheSize: Int = 0,
    val transcoding: String = "",
    val eqEnabled: Boolean = false,
    /** equalizer profile: off | flat | bass | treble | vocal | rock | electronic | classical | pop | tv | headphones */
    val audioProfile: String = "",
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
