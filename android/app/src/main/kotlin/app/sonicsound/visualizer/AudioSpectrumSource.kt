package app.sonicsound.visualizer

import android.content.Context
import android.media.AudioManager
import kotlin.math.pow

/**
 * UI-facing spectrum handle backed by [PlaybackSpectrum] (LibVLC PCM → FFT).
 * Output is scaled by the device [AudioManager.STREAM_MUSIC] volume so quiet
 * listening yields small motion and loud volume drives larger movement.
 */
class AudioSpectrumSource(context: Context) {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val active: Boolean get() = PlaybackSpectrum.active
    val bandCount: Int get() = PlaybackSpectrum.bandCount
    val waveCount: Int get() = PlaybackSpectrum.waveCount

    private var playing = false
    private var volumeScale = 1f
    private var volPoll = 0

    fun band(index: Int): Float = PlaybackSpectrum.band(index) * volumeScale
    fun waveAt(index: Int): Float = PlaybackSpectrum.waveAt(index) * volumeScale
    fun bass(): Float = PlaybackSpectrum.bass() * volumeScale
    fun mids(): Float = PlaybackSpectrum.mids() * volumeScale
    fun energy(): Float = PlaybackSpectrum.energy() * volumeScale

    fun setPlaying(playing: Boolean) {
        this.playing = playing
    }

    fun tick() {
        if ((volPoll++ and 7) == 0) {
            volumeScale = readVolumeScale()
        }
        PlaybackSpectrum.tickDecay(playing)
    }

    fun start(): Boolean = true

    fun stop() = Unit

    /**
     * Map stream volume → display intensity.
     * Mute → none; low → subtle; max → full. Mild power curve for natural feel.
     */
    private fun readVolumeScale(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, max)
        val f = vol.toFloat() / max
        if (f <= 0f) return 0f
        // Keep a little life at low volume; expand toward 1.0 at full blast.
        return (0.06f + 0.94f * f.pow(0.72f)).coerceIn(0f, 1f)
    }
}
