package app.sonicsound.visualizer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/** FFT + log-band mapping helpers for [PlaybackSpectrum]. */
internal object SpectrumFft {
    fun buildHann(fftSize: Int): FloatArray = FloatArray(fftSize) { i ->
        (0.5f * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
    }

    fun ensureBandPlan(
        sampleRate: Int,
        fftSize: Int,
        bandCount: Int,
        bandBinStart: IntArray,
        bandBinEnd: IntArray,
    ) {
        val usable = fftSize / 2
        val nyquist = sampleRate * 0.5f
        val fMin = 40f
        val fMax = (nyquist * 0.92f).coerceAtLeast(fMin * 2f)
        val logMin = ln(fMin.toDouble())
        val logMax = ln(fMax.toDouble())
        for (b in 0 until bandCount) {
            val t0 = b.toDouble() / bandCount
            val t1 = (b + 1).toDouble() / bandCount
            val freq0 = kotlin.math.exp(logMin + (logMax - logMin) * t0).toFloat()
            val freq1 = kotlin.math.exp(logMin + (logMax - logMin) * t1).toFloat()
            val start = (freq0 * fftSize / sampleRate).toInt().coerceIn(1, usable - 1)
            val end = (freq1 * fftSize / sampleRate).toInt().coerceIn(start + 1, usable)
            bandBinStart[b] = start
            bandBinEnd[b] = end
        }
    }

    fun computeBandMagnitudes(
        staging: FloatArray,
        outMags: FloatArray,
        fftRe: FloatArray,
        fftIm: FloatArray,
        hann: FloatArray,
        bandBinStart: IntArray,
        bandBinEnd: IntArray,
        fftSize: Int,
        bandCount: Int,
    ) {
        for (i in 0 until fftSize) {
            fftRe[i] = staging[i] * hann[i]
            fftIm[i] = 0f
        }
        fftRadix2(fftRe, fftIm)

        for (b in 0 until bandCount) {
            var peak = 0f
            var sum = 0f
            var n = 0
            val start = bandBinStart[b]
            val end = bandBinEnd[b]
            for (k in start until end) {
                val mag = sqrt(fftRe[k] * fftRe[k] + fftIm[k] * fftIm[k])
                if (mag > peak) peak = mag
                sum += mag
                n++
            }
            val avg = if (n == 0) 0f else sum / n
            outMags[b] = peak * 0.72f + avg * 0.28f
        }
    }

    fun fftRadix2(re: FloatArray, im: FloatArray) {
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
