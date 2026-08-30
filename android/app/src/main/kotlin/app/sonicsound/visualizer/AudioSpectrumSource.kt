package app.sonicsound.visualizer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Live FFT magnitudes (0..1 per band) and waveform samples (−1..1) for WMP visualizers.
 * Data comes only from the platform [Visualizer] attached to the output mix — never synthesized.
 */
class AudioSpectrumSource {
    private var visualizer: Visualizer? = null
    private var captureSize = 0
    private var fftRaw = ByteArray(0)
    private var waveRaw = ByteArray(0)

    /** Smoothed bar magnitudes in 0..1 (index 0 = DC / lowest band). */
    private var bands = FloatArray(0)
    private var wave = FloatArray(0)

    private var playing = false
    private var silentFrames = 0

    val active: Boolean get() = visualizer?.enabled == true
    val bandCount: Int get() = bands.size
    val waveCount: Int get() = wave.size

    /**
     * True when the Visualizer is attached but has produced no usable energy while playing
     * long enough that LibVLC likely recreated underneath (or passthrough bypassed the mix).
     */
    fun shouldReattach(): Boolean = playing && active && silentFrames >= SILENT_REATTACH_FRAMES

    fun band(index: Int): Float {
        if (bands.isEmpty()) return 0f
        return bands[index.coerceIn(0, bands.lastIndex)]
    }

    fun waveAt(index: Int): Float {
        if (wave.isEmpty()) return 0f
        return wave[index.coerceIn(0, wave.lastIndex)]
    }

    /** Average energy across low bands (bass / kick). */
    fun bass(): Float = averageBands(1, (bandCount / 8).coerceAtLeast(2))

    /** Mid-range energy. */
    fun mids(): Float {
        val start = (bandCount / 8).coerceAtLeast(2)
        val end = (bandCount / 2).coerceAtLeast(start + 1)
        return averageBands(start, end)
    }

    /** Overall RMS-ish energy 0..1. */
    fun energy(): Float = averageBands(1, bandCount.coerceAtLeast(2))

    fun setPlaying(playing: Boolean) {
        this.playing = playing
        if (!playing) silentFrames = 0
    }

    /**
     * Attach to the global output mix. Requires [Manifest.permission.RECORD_AUDIO].
     * Prefers AudioTrack-routed playback (see VlcEngine aout) so the mix is visible.
     */
    fun start(context: Context): Boolean {
        stop()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO not granted — visualizer idle")
            return false
        }
        return try {
            val viz = Visualizer(0)
            viz.enabled = false
            val range = Visualizer.getCaptureSizeRange()
            // 512 is enough for 48 bars; larger sizes cost more per frame with little gain.
            captureSize = range[1].coerceAtMost(512).coerceAtLeast(range[0])
            // Snap to supported size (power of two in range).
            var size = captureSize
            while (size > range[0] && size and (size - 1) != 0) size /= 2
            captureSize = size.coerceIn(range[0], range[1])
            viz.captureSize = captureSize
            runCatching { viz.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED) }
            fftRaw = ByteArray(captureSize)
            waveRaw = ByteArray(captureSize)
            // FFT layout: DC, Nyquist, then (n/2 - 1) complex pairs → ~captureSize/2 magnitudes.
            bands = FloatArray(captureSize / 2)
            wave = FloatArray(captureSize)
            viz.enabled = true
            visualizer = viz
            silentFrames = 0
            true
        } catch (e: Exception) {
            Log.w(TAG, "Visualizer unavailable", e)
            releaseVisualizer()
            false
        }
    }

    fun stop() {
        releaseVisualizer()
        bands.fill(0f)
        wave.fill(0f)
    }

    /**
     * Poll capture buffers. Call once per display frame while the view is visible.
     * When paused, magnitudes decay toward zero (no fake motion).
     */
    fun tick() {
        val viz = visualizer
        if (viz == null || !viz.enabled) {
            decayBands(0.22f)
            return
        }
        val gotFft = runCatching { viz.getFft(fftRaw) == Visualizer.SUCCESS }.getOrDefault(false)
        val gotWave = runCatching { viz.getWaveForm(waveRaw) == Visualizer.SUCCESS }.getOrDefault(false)
        if (!gotFft && !gotWave) {
            decayBands(0.18f)
            return
        }
        if (gotWave) {
            for (i in wave.indices) {
                wave[i] = ((waveRaw[i].toInt() and 0xFF) - 128) / 128f
            }
        }
        if (gotFft) {
            updateBandsFromFft()
        } else if (!playing) {
            decayBands(0.2f)
        }

        if (playing && energy() < 0.02f) {
            silentFrames++
            if (silentFrames == SILENT_WARN_FRAMES) {
                Log.w(TAG, "Visualizer attached but no signal — check aout / passthrough")
            }
        } else {
            silentFrames = 0
        }

        if (!playing) {
            decayBands(0.15f)
        }
    }

    private fun updateBandsFromFft() {
        val n = bands.size
        if (n == 0) return
        // Model: fft[0]=DC real, fft[1]=Nyquist real; then re/im pairs.
        bands[0] = abs(fftRaw[0].toInt()).toFloat() / 128f
        if (n > 1) {
            bands[n - 1] = abs(fftRaw[1].toInt()).toFloat() / 128f
        }
        var i = 1
        var bin = 2
        while (i < n - 1 && bin + 1 < fftRaw.size) {
            val re = fftRaw[bin].toInt().toFloat()
            val im = fftRaw[bin + 1].toInt().toFloat()
            val mag = sqrt(re * re + im * im) / 128f
            // Attack fast, release slower — reads as musical without inventing energy.
            val prev = bands[i]
            bands[i] = if (mag > prev) {
                prev + (mag - prev) * 0.55f
            } else {
                prev + (mag - prev) * 0.22f
            }
            i++
            bin += 2
        }
        for (b in bands.indices) {
            bands[b] = bands[b].coerceIn(0f, 1f)
        }
    }

    private fun decayBands(amount: Float) {
        for (i in bands.indices) {
            bands[i] = (bands[i] * (1f - amount)).coerceAtLeast(0f)
        }
        for (i in wave.indices) {
            wave[i] *= (1f - amount)
        }
    }

    private fun averageBands(start: Int, endExclusive: Int): Float {
        if (bands.isEmpty()) return 0f
        val a = start.coerceIn(0, bands.lastIndex)
        val b = endExclusive.coerceIn(a + 1, bands.size)
        var sum = 0f
        for (i in a until b) sum += bands[i]
        return (sum / (b - a)).coerceIn(0f, 1f)
    }

    private fun releaseVisualizer() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {
        }
        visualizer = null
        captureSize = 0
    }

    companion object {
        private const val TAG = "AudioSpectrumSource"
        private const val SILENT_WARN_FRAMES = 90
        /** ~2s at 60fps — recreate capture after LibVLC aout swap leaves a dead Visualizer. */
        private const val SILENT_REATTACH_FRAMES = 120
    }
}
