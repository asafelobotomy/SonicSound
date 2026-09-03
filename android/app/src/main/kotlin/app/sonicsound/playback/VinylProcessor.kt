package app.sonicsound.playback

import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * Realtime vinyl playback emulation for the LibVLC PCM tap path.
 *
 * Authenticity lessons (ideas rewritten, not vendored):
 * - ToneArm (MIT): sparse random crackle/pop *placements* with shaped ticks —
 *   not a steady Bernoulli rain; wow at platter-related rates.
 * - Patina / vinylfy style: surface reacts with the music; brief gain dips on
 *   pops; RIAA-ish / cartridge tonal tilt; stereo narrowing.
 * - Corrupter Vinyl Sim: worn LP age, rumble/hiss beds, exponential envelopes.
 * - Vinyl Desktop (EntroPi): turntable *UI* / pitch — not a surface FX engine;
 *   we keep DSP focus here.
 *
 * Wear events use log-uniform gaps + rare clusters so ticks feel dispersed,
 * not metronomic. Soft music dips ride with loud pops without burying the song.
 */
object VinylProcessor {
    private const val TAG = "VinylProcessor"

    @Volatile private var fxActiveLogged = false

    private val dsp = VinylDspEngine()
    private val processLock = Any()

    init {
        synchronized(processLock) {
            dsp.ensureBuffers(VinylDspEngine.BUFFER_RATE_HZ)
        }
    }

    fun isEnabled(): Boolean = dsp.enabled

    fun configure(
        profileId: String,
        conditionId: String,
        rateHz: Int = dsp.sampleRate,
        forceDisableFx: Boolean = false,
    ) {
        val wantVinyl = profileId == AudioProfile.VINYL && !forceDisableFx
        val cond = VinylCondition.resolve(conditionId)
        val rate = rateHz.coerceAtLeast(8000)
        synchronized(processLock) {
            val was = dsp.enabled
            val wasSkips = dsp.skipsEnabled
            dsp.enabled = wantVinyl
            dsp.intensity = VinylCondition.intensity(cond)
            dsp.skipsEnabled = VinylCondition.skipsEnabled(cond)
            dsp.sampleRate = rate
            if (rate > VinylDspEngine.BUFFER_RATE_HZ || dsp.wowCapFrames == 0 || dsp.histCapFrames == 0) {
                dsp.ensureBuffers(max(rate, VinylDspEngine.BUFFER_RATE_HZ))
            }
            if (!dsp.enabled) {
                dsp.resetState()
                fxActiveLogged = false
            } else if (!was) {
                dsp.resetState()
                dsp.scheduleNextSkip()
                dsp.scheduleNextWear(cluster = false)
            } else if (wasSkips && !dsp.skipsEnabled) {
                dsp.skipRemaining = 0
                dsp.samplesSinceSkip = 0
            } else if (!wasSkips && dsp.skipsEnabled) {
                dsp.scheduleNextSkip()
            }
            when {
                profileId == AudioProfile.VINYL && forceDisableFx -> {
                    if (!fxActiveLogged) {
                        Log.w(TAG, "Vinyl selected but PCM tap inactive — EQ warmth only")
                        fxActiveLogged = true
                    }
                }
                dsp.enabled -> {
                    if (!was || !fxActiveLogged) {
                        val color = (0.42f + 0.58f * dsp.intensity).coerceIn(0f, 1f)
                        val ageBlend = (0.18f + 0.38f * color).coerceIn(0.16f, 0.68f)
                        val dust = 0.0018f + 0.014f * dsp.intensity
                        Log.i(
                            TAG,
                            "Vinyl FX active condition=$cond intensity=${dsp.intensity} " +
                                "color=$color ageBlend=$ageBlend dust=$dust rate=$rate",
                        )
                    }
                    fxActiveLogged = true
                }
            }
        }
    }

    fun publishClock(posMs: Long, durMs: Long) {
        dsp.positionMs = posMs.coerceAtLeast(0L)
        dsp.durationMs = durMs.coerceAtLeast(0L)
    }

    fun reset() {
        synchronized(processLock) {
            dsp.resetState()
            if (dsp.skipsEnabled) dsp.scheduleNextSkip()
            if (dsp.enabled) dsp.scheduleNextWear(cluster = false)
        }
    }

    fun processInPlace(pcm: ByteArray, len: Int, channels: Int, frames: Int, rate: Int): Boolean {
        if (!dsp.enabled || len < 2 || frames <= 0 || channels <= 0) return false
        val ch = channels.coerceIn(1, VinylDspEngine.MAX_CHANNELS)
        val fr = frames
        val byteLen = min(len, fr * ch * 2)
        if (byteLen < fr * ch * 2) return false

        synchronized(processLock) {
            if (!dsp.enabled) return false
            dsp.processFrames(pcm, fr, ch, rate)
        }
        return true
    }
}
