package app.sonicsound.visualizer

import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Live spectrum fed from LibVLC decoded PCM ([app.sonicsound.playback.VlcPcmOutput]).
 * No microphone / Visualizer / RECORD_AUDIO — works on Shield TV.
 */
object PlaybackSpectrum {
    private const val FFT_SIZE = 512
    private const val BAND_COUNT = 64
    private const val WAVE_COUNT = 128

    private val window = FloatArray(FFT_SIZE)
    private val fftRe = FloatArray(FFT_SIZE)
    private val fftIm = FloatArray(FFT_SIZE)
    private val hann = FloatArray(FFT_SIZE) { i ->
        (0.5f * (1.0 - cos(2.0 * PI * i / (FFT_SIZE - 1)))).toFloat()
    }
    private val bandsInternal = FloatArray(BAND_COUNT)
    private val waveInternal = FloatArray(WAVE_COUNT)
    private val bandsPub = FloatArray(BAND_COUNT)
    private val wavePub = FloatArray(WAVE_COUNT)

    private var writePos = 0
    private var framesSinceFft = 0
    /** Slow adaptive gain so quiet speech and hot masters both drive the UI. */
    private var agcGain = 12f
    private var pcmPeak = 0f
    private val lock = Any()

    @Volatile
    private var hasSignal = false

    val active: Boolean get() = hasSignal
    val bandCount: Int get() = BAND_COUNT
    val waveCount: Int get() = WAVE_COUNT
    /** Last observed PCM peak (0..1), for diagnostics. */
    val lastPcmPeak: Float get() = synchronized(lock) { pcmPeak }

    fun band(index: Int): Float {
        val i = index.coerceIn(0, BAND_COUNT - 1)
        return bandsPub[i]
    }

    fun waveAt(index: Int): Float {
        val i = index.coerceIn(0, WAVE_COUNT - 1)
        return wavePub[i]
    }

    fun bass(): Float = average(1, (BAND_COUNT / 8).coerceAtLeast(2))

    fun mids(): Float {
        val start = (BAND_COUNT / 8).coerceAtLeast(2)
        val end = (BAND_COUNT / 2).coerceAtLeast(start + 1)
        return average(start, end)
    }

    fun energy(): Float = average(1, BAND_COUNT)

    fun reset() {
        synchronized(lock) {
            window.fill(0f)
            bandsInternal.fill(0f)
            waveInternal.fill(0f)
            writePos = 0
            framesSinceFft = 0
            hasSignal = false
            pcmPeak = 0f
            agcGain = 12f
            bandsPub.fill(0f)
            wavePub.fill(0f)
        }
    }

    /**
     * Ingest interleaved S16 PCM from the LibVLC play callback.
     * Runs on the VLC audio thread — keep work bounded.
     */
    fun onPcmS16(
        pcm: ByteArray,
        bytes: Int,
        channels: Int,
        frames: Int,
        order: ByteOrder,
    ) {
        if (bytes < 2 || channels < 1 || frames < 1) return
        val ch = channels.coerceAtLeast(1)
        val little = order == ByteOrder.LITTLE_ENDIAN
        synchronized(lock) {
            hasSignal = true
            val frameCount = (bytes / (2 * ch)).coerceAtMost(frames)
            var batchPeak = 0f
            for (f in 0 until frameCount) {
                var acc = 0
                val base = f * ch * 2
                for (c in 0 until ch) {
                    val i = base + c * 2
                    if (i + 1 >= bytes) break
                    val sample = if (little) {
                        ((pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)).toShort()
                    } else {
                        ((pcm[i + 1].toInt() and 0xFF) or (pcm[i].toInt() shl 8)).toShort()
                    }
                    acc += sample.toInt()
                }
                val mono = (acc / ch) / 32768f
                val abs = kotlin.math.abs(mono)
                if (abs > batchPeak) batchPeak = abs
                window[writePos] = mono
                writePos = (writePos + 1) % FFT_SIZE
            }
            pcmPeak = pcmPeak * 0.92f + batchPeak * 0.08f

            // Scope: last WAVE_COUNT mono samples from the ring (gain-boosted for display).
            val waveGain = (agcGain * 0.35f).coerceIn(1f, 24f)
            for (i in 0 until WAVE_COUNT) {
                val idx = (writePos - WAVE_COUNT + i + FFT_SIZE) % FFT_SIZE
                waveInternal[i] = (window[idx] * waveGain).coerceIn(-1f, 1f)
            }

            framesSinceFft += frameCount
            // ~90 FFT/s at 44.1kHz — plenty for 60fps UI without burning CPU.
            if (framesSinceFft >= FFT_SIZE / 4) {
                framesSinceFft = 0
                computeFftLocked()
            }

            // Publish snapshots for the UI thread.
            System.arraycopy(bandsInternal, 0, bandsPub, 0, BAND_COUNT)
            System.arraycopy(waveInternal, 0, wavePub, 0, WAVE_COUNT)
        }
    }

    /** Smooth decay toward silence when paused (called from the visualizer frame loop). */
    fun tickDecay(playing: Boolean) {
        if (playing) return
        synchronized(lock) {
            for (i in bandsInternal.indices) {
                bandsInternal[i] *= 0.85f
                if (bandsInternal[i] < 0.001f) bandsInternal[i] = 0f
            }
            for (i in waveInternal.indices) {
                waveInternal[i] *= 0.85f
            }
            System.arraycopy(bandsInternal, 0, bandsPub, 0, BAND_COUNT)
            System.arraycopy(waveInternal, 0, wavePub, 0, WAVE_COUNT)
            if (bandsInternal.all { it == 0f }) hasSignal = false
        }
    }

    private fun computeFftLocked() {
        // Copy ring into FFT buffers with Hann window, oldest→newest.
        for (i in 0 until FFT_SIZE) {
            val idx = (writePos + i) % FFT_SIZE
            fftRe[i] = window[idx] * hann[i]
            fftIm[i] = 0f
        }
        fftRadix2(fftRe, fftIm)

        // Fold into BAND_COUNT log-ish magnitude bins (skip DC-heavy bin 0 lightly).
        val usable = FFT_SIZE / 2
        for (b in 0 until BAND_COUNT) {
            val start = 1 + b * (usable - 1) / BAND_COUNT
            val end = 1 + (b + 1) * (usable - 1) / BAND_COUNT
            var sum = 0f
            var n = 0
            for (k in start until end.coerceAtLeast(start + 1)) {
                if (k >= usable) break
                val mag = sqrt(fftRe[k] * fftRe[k] + fftIm[k] * fftIm[k])
                sum += mag
                n++
            }
            val mag = if (n == 0) 0f else (sum / n)
            // Base scale + slow AGC so quiet material still moves the bars.
            val norm = (mag * 8f * agcGain).coerceIn(0f, 1f)
            val prev = bandsInternal[b]
            bandsInternal[b] = if (norm > prev) {
                prev + (norm - prev) * 0.65f
            } else {
                prev + (norm - prev) * 0.28f
            }
        }
        var peak = 0f
        for (v in bandsInternal) if (v > peak) peak = v
        if (peak > 0.02f) {
            val desired = (0.75f / peak).coerceIn(0.35f, 32f)
            agcGain = (agcGain * 0.92f + desired * 0.08f).coerceIn(2f, 40f)
        } else if (pcmPeak > 0.002f) {
            // Signal present but FFT bins soft — nudge gain up.
            agcGain = (agcGain * 1.03f).coerceIn(2f, 40f)
        }
    }

    private fun average(start: Int, endExclusive: Int): Float {
        val a = start.coerceIn(0, BAND_COUNT - 1)
        val b = endExclusive.coerceIn(a + 1, BAND_COUNT)
        var sum = 0f
        for (i in a until b) sum += bandsPub[i]
        return (sum / (b - a)).coerceIn(0f, 1f)
    }

    /** In-place radix-2 Cooley–Tukey FFT. */
    private fun fftRadix2(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wlenRe = cos(ang).toFloat()
            val wlenIm = sin(ang).toFloat()
            var i0 = 0
            while (i0 < n) {
                var wRe = 1f
                var wIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i0 + k]
                    val uIm = im[i0 + k]
                    val vRe = re[i0 + k + len / 2] * wRe - im[i0 + k + len / 2] * wIm
                    val vIm = re[i0 + k + len / 2] * wIm + im[i0 + k + len / 2] * wRe
                    re[i0 + k] = uRe + vRe
                    im[i0 + k] = uIm + vIm
                    re[i0 + k + len / 2] = uRe - vRe
                    im[i0 + k + len / 2] = uIm - vIm
                    val nextWRe = wRe * wlenRe - wIm * wlenIm
                    wIm = wRe * wlenIm + wIm * wlenRe
                    wRe = nextWRe
                }
                i0 += len
            }
            len = len shl 1
        }
    }
}
