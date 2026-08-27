/**
 * Compile-time feature gates. Flip to true when shipping deferred surfaces.
 * Prefer these over scattered UI hides so re-enable is one place.
 *
 * When enabling youtubeMusicVideos also:
 * - Set Features.YOUTUBE_MUSIC_VIDEOS = true in Android Features.kt
 * - Re-add /videos routes in App.tsx and Settings YouTube section if desired
 */
export const Features = {
    /** YouTube music-video search, Videos nav, and Now Playing MV chrome. */
    youtubeMusicVideos: false,
} as const;
