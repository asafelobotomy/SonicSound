package app.sonicsound.visualizer

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import kotlin.math.exp
import kotlin.math.pow

/**
 * UI-facing spectrum handle backed by [PlaybackSpectrum].
 *
 * Each frame:
 * 1. Refresh volume + output-latency estimate (Bluetooth / HDMI / speakers)
 * 2. Present the delayed spectrum snapshot that matches audible audio
 * 3. Smooth toward that accurate target with tempo-aware attack/release
 */
class AudioSpectrumSource(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val active: Boolean get() = PlaybackSpectrum.active
    val bandCount: Int get() = PlaybackSpectrum.bandCount
    val waveCount: Int get() = PlaybackSpectrum.waveCount

    private var playing = false
    private var volumeScale = 1f
    private var poll = 0
    private var lastLoggedDelay = -1
    private var lastTickNanos = 0L

    private val smoothBands = FloatArray(PlaybackSpectrum.bandCount)
    private val smoothWave = FloatArray(PlaybackSpectrum.waveCount)
    private var smoothBass = 0f
    private var smoothMids = 0f
    private var smoothEnergy = 0f
    private var smoothLeft = 0f
    private var smoothRight = 0f
    private var smoothSide = 0f
    private var smoothSurround = 0f
    private var smoothLfe = 0f

    var attackHz: Float = 28f
        private set
    var releaseHz: Float = 11f
        private set
    var bpm: Float = 110f
        private set

    fun band(index: Int): Float {
        val i = index.coerceIn(0, smoothBands.lastIndex)
        return smoothBands[i] * volumeScale
    }

    fun waveAt(index: Int): Float {
        val i = index.coerceIn(0, smoothWave.lastIndex)
        return smoothWave[i] * volumeScale
    }

    fun bass(): Float = smoothBass * volumeScale
    fun mids(): Float = smoothMids * volumeScale
    fun energy(): Float = smoothEnergy * volumeScale
    fun left(): Float = smoothLeft * volumeScale
    fun right(): Float = smoothRight * volumeScale
    fun side(): Float = smoothSide * volumeScale
    fun surround(): Float = smoothSurround * volumeScale
    fun lfe(): Float = smoothLfe * volumeScale

    fun setPlaying(playing: Boolean) {
        this.playing = playing
    }

    fun tick(playingOverride: Boolean = playing) {
        if ((poll++ and 7) == 0) {
            volumeScale = readVolumeScale()
            val delay = AudioOutputLatency.estimateMs(appContext)
            PlaybackSpectrum.setDisplayDelayMs(delay.toLong())
            if (delay != lastLoggedDelay && (poll and 63) == 0) {
                lastLoggedDelay = delay
                AudioOutputLatency.logEstimate(appContext)
            }
        }
        PlaybackSpectrum.presentForDisplay()
        PlaybackSpectrum.tickDecay(playingOverride)

        val now = SystemClock.elapsedRealtimeNanos()
        val dt = if (lastTickNanos == 0L) {
            1f / 60f
        } else {
            ((now - lastTickNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        }
        lastTickNanos = now

        attackHz = PlaybackSpectrum.attackHz()
        releaseHz = PlaybackSpectrum.releaseHz()
        bpm = PlaybackSpectrum.estimatedBpm()

        val aUp = 1f - exp(-dt * attackHz)
        val aDown = 1f - exp(-dt * releaseHz)
        for (i in smoothBands.indices) {
            val target = PlaybackSpectrum.band(i)
            val cur = smoothBands[i]
            smoothBands[i] = cur + (target - cur) * if (target > cur) aUp else aDown
        }
        val waveAUp = 1f - exp(-dt * (attackHz * 0.85f))
        val waveADown = 1f - exp(-dt * (releaseHz * 1.25f))
        for (i in smoothWave.indices) {
            val target = PlaybackSpectrum.waveAt(i)
            val cur = smoothWave[i]
            val a = if (kotlin.math.abs(target) > kotlin.math.abs(cur)) waveAUp else waveADown
            smoothWave[i] = cur + (target - cur) * a
        }
        fun smoothToward(cur: Float, target: Float): Float =
            cur + (target - cur) * if (target > cur) aUp else aDown

        smoothBass = smoothToward(smoothBass, PlaybackSpectrum.bass())
        smoothMids = smoothToward(smoothMids, PlaybackSpectrum.mids())
        smoothEnergy = smoothToward(smoothEnergy, PlaybackSpectrum.energy())
        smoothLeft = smoothToward(smoothLeft, PlaybackSpectrum.left())
        smoothRight = smoothToward(smoothRight, PlaybackSpectrum.right())
        smoothSide = smoothToward(smoothSide, PlaybackSpectrum.side())
        smoothSurround = smoothToward(smoothSurround, PlaybackSpectrum.surround())
        smoothLfe = smoothToward(smoothLfe, PlaybackSpectrum.lfe())
    }

    fun start(): Boolean {
        lastTickNanos = 0L
        return true
    }

    fun stop() {
        lastTickNanos = 0L
    }

    private fun readVolumeScale(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, max)
        val f = vol.toFloat() / max
        if (f <= 0f) return 0f
        return (0.22f + 0.78f * f.pow(0.55f)).coerceIn(0f, 1f)
    }
}
