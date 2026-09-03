package app.sonicsound.visualizer

import android.os.SystemClock
import kotlin.math.pow

/** AGC + character envelopes for [PlaybackSpectrum]. */
internal object SpectrumAgc {
    fun apply(
        mags: FloatArray,
        rawBands: FloatArray,
        bandsInternal: FloatArray,
        character: TrackCharacter,
        bandCount: Int,
        fftHop: Int,
        sampleRate: Int,
        pcmPeak: Float,
        agcGainIn: Float,
    ): Float {
        var agcGain = agcGainIn
        var framePeak = 0f
        var frameSum = 0f
        for (b in 0 until bandCount) {
            val mag = mags[b]
            rawBands[b] = mag
            if (mag > framePeak) framePeak = mag
            frameSum += mag
        }

        if (framePeak > 1e-5f) {
            val targetLevel = (0.028f + character.dynamicRange * 0.03f - character.intensity * 0.008f)
                .coerceIn(0.018f, 0.06f)
            val target = targetLevel / framePeak
            val desired = target.coerceIn(0.15f, 28f)
            val adapt = (0.04f + character.dynamicRange * 0.05f).coerceIn(0.03f, 0.1f)
            agcGain = (agcGain * (1f - adapt) + desired * adapt).coerceIn(0.2f, 32f)
        } else if (pcmPeak > 0.002f) {
            agcGain = (agcGain * 1.02f).coerceIn(0.2f, 32f)
        }

        for (b in 0 until bandCount) {
            rawBands[b] *= agcGain
        }
        character.observe(rawBands, SystemClock.elapsedRealtime())

        val mean = (frameSum * agcGain / bandCount).coerceAtLeast(1e-8f)
        val hopSec = fftHop.toFloat() / sampleRate.coerceAtLeast(8_000).toFloat()
        for (b in 0 until bandCount) {
            val mag = rawBands[b]
            var norm = character.normalize(mag)
            val relative = (mag / mean).coerceIn(0.12f, 10f)
            val contrastPow = (0.42f + character.dynamicRange * 0.12f).coerceIn(0.35f, 0.55f)
            val contrast = relative.pow(contrastPow).coerceIn(0.4f, 2.6f)
            norm = (norm * contrast).coerceIn(0f, 1f)
            bandsInternal[b] = character.envelope(bandsInternal[b], norm, hopSec)
        }
        return agcGain
    }

    fun average(bandsPub: FloatArray, bandCount: Int, start: Int, endExclusive: Int): Float {
        val a = start.coerceIn(0, bandCount - 1)
        val b = endExclusive.coerceIn(a + 1, bandCount)
        var sum = 0f
        for (i in a until b) sum += bandsPub[i]
        return (sum / (b - a)).coerceIn(0f, 1f)
    }

    fun tickDecay(
        bandsInternal: FloatArray,
        waveInternal: FloatArray,
        push: () -> Unit,
        present: () -> Unit,
        clearSignal: () -> Unit,
    ) {
        for (i in bandsInternal.indices) {
            bandsInternal[i] *= 0.88f
            if (bandsInternal[i] < 0.001f) bandsInternal[i] = 0f
        }
        for (i in waveInternal.indices) waveInternal[i] *= 0.88f
        push()
        present()
        if (bandsInternal.all { it == 0f }) clearSignal()
    }
}
