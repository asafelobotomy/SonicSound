package app.sonicsound.playback

import app.sonicsound.models.Settings
import org.videolan.libvlc.MediaPlayer

/**
 * LibVLC 10-band equalizer presets for common listening scenarios.
 * Band indices map to VLC's fixed bands (60 Hz … 16 kHz).
 */
object AudioProfile {
    const val OFF = "off"
    const val FLAT = "flat"
    const val BASS = "bass"
    const val TREBLE = "treble"
    const val VOCAL = "vocal"
    const val ROCK = "rock"
    const val ELECTRONIC = "electronic"
    const val CLASSICAL = "classical"
    const val POP = "pop"
    const val TV = "tv"
    const val HEADPHONES = "headphones"

    val ALL: List<String> = listOf(
        OFF,
        FLAT,
        BASS,
        TREBLE,
        VOCAL,
        ROCK,
        ELECTRONIC,
        CLASSICAL,
        POP,
        TV,
        HEADPHONES,
    )

    /** Migrates legacy [Settings.eqEnabled] to [FLAT] when no profile is stored. */
    fun resolve(settings: Settings): String {
        val stored = settings.audioProfile.trim()
        if (stored.isNotEmpty()) {
            return if (stored in ALL) stored else OFF
        }
        return if (settings.eqEnabled) FLAT else OFF
    }

    /** Keep [Settings.audioProfile] and [Settings.eqEnabled] consistent. */
    fun normalize(settings: Settings): Settings {
        val profile = resolve(settings)
        return settings.copy(
            audioProfile = profile,
            eqEnabled = profile != OFF,
        )
    }

    fun needsEqualizerFilter(profileId: String): Boolean = profileId != OFF && profileId in ALL

    fun apply(player: MediaPlayer?, profileId: String) {
        val p = player ?: return
        if (profileId == OFF || profileId !in ALL) {
            p.setEqualizer(null)
            return
        }
        val eq = buildEqualizer(profileId) ?: run {
            p.setEqualizer(null)
            return
        }
        p.setEqualizer(eq)
    }

    private fun buildEqualizer(profileId: String): MediaPlayer.Equalizer? = when (profileId) {
        FLAT -> fromPreset(0)
        BASS -> fromPreset(4)
        TREBLE -> fromPreset(6)
        ROCK -> fromPreset(13)
        ELECTRONIC -> fromPreset(17)
        CLASSICAL -> fromPreset(1)
        POP -> fromPreset(11)
        HEADPHONES -> fromPreset(7)
        VOCAL -> customEqualizer(
            preAmp = -1.5f,
            amps = floatArrayOf(-2f, -1f, 2f, 4f, 5f, 6f, 4f, 2f, 1f, 0f),
        )
        TV -> customEqualizer(
            preAmp = -2f,
            amps = floatArrayOf(4f, 3f, 1f, 0f, 2f, 3f, 2f, 1f, 0f, -1f),
        )
        else -> fromPreset(0)
    }

    private fun fromPreset(index: Int): MediaPlayer.Equalizer? {
        val count = runCatching { MediaPlayer.Equalizer.getPresetCount() }.getOrDefault(0)
        if (count <= 0) return MediaPlayer.Equalizer.create()
        val safe = index.coerceIn(0, count - 1)
        return MediaPlayer.Equalizer.createFromPreset(safe)
    }

    private fun customEqualizer(preAmp: Float, amps: FloatArray): MediaPlayer.Equalizer {
        val eq = MediaPlayer.Equalizer.create()
        eq.setPreAmp(preAmp)
        amps.forEachIndexed { index, amp ->
            eq.setAmp(index, amp.coerceIn(-20f, 20f))
        }
        return eq
    }
}
