package app.sonicsound

/**
 * Compile-time feature gates. Flip to true when shipping deferred surfaces.
 * Prefer these over scattered visibility="gone" so re-enable is one place.
 *
 * When enabling YOUTUBE_MUSIC_VIDEOS also restore Settings YouTube UI if desired.
 * GET_ACCOUNTS / USE_CREDENTIALS stay in the manifest for Google account OAuth.
 */
object Features {
    /** YouTube music-video search, Videos nav, and Now Playing MV chrome. */
    const val YOUTUBE_MUSIC_VIDEOS = false
}
