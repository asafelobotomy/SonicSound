package app.sonicsound.visualizer

import android.os.SystemClock
import java.nio.ByteOrder
import kotlin.math.min

/** PCM demux ingest for [PlaybackSpectrum]. */
internal object SpectrumPcmIngest {
    fun readS16(pcm: ByteArray, index: Int, bytes: Int, little: Boolean): Float {
        if (index + 1 >= bytes) return 0f
        val sample = if (little) {
            ((pcm[index].toInt() and 0xFF) or (pcm[index + 1].toInt() shl 8)).toShort()
        } else {
            ((pcm[index + 1].toInt() and 0xFF) or (pcm[index].toInt() shl 8)).toShort()
        }
        return sample / 32768f
    }

    data class BatchMeters(
        var peak: Float = 0f,
        var sumL: Float = 0f,
        var sumR: Float = 0f,
        var sumSide: Float = 0f,
        var sumSur: Float = 0f,
        var sumLfe: Float = 0f,
    )

    fun demuxBatch(
        pcm: ByteArray,
        bytes: Int,
        ch: Int,
        f0: Int,
        n: Int,
        little: Boolean,
        midScratch: FloatArray,
        meters: BatchMeters,
    ) {
        for (i in 0 until n) {
            val f = f0 + i
            val base = f * ch * 2
            val s0 = readS16(pcm, base, bytes, little)
            val s1 = if (ch > 1) readS16(pcm, base + 2, bytes, little) else s0
            val s2 = if (ch > 2) readS16(pcm, base + 4, bytes, little) else 0f
            val s3 = if (ch > 3) readS16(pcm, base + 6, bytes, little) else 0f
            val s4 = if (ch > 4) readS16(pcm, base + 8, bytes, little) else 0f
            val s5 = if (ch > 5) readS16(pcm, base + 10, bytes, little) else 0f
            val s6 = if (ch > 6) readS16(pcm, base + 12, bytes, little) else 0f
            val s7 = if (ch > 7) readS16(pcm, base + 14, bytes, little) else 0f

            val mid = when {
                ch >= 6 -> (s0 + s1 + s2 * 0.75f) / 2.75f
                ch >= 2 -> (s0 + s1) * 0.5f
                else -> s0
            }
            val side = (s0 - s1) * 0.5f
            val surround = when {
                ch >= 8 -> (kotlin.math.abs(s4) + kotlin.math.abs(s5) +
                    kotlin.math.abs(s6) + kotlin.math.abs(s7)) * 0.25f
                ch >= 6 -> (kotlin.math.abs(s4) + kotlin.math.abs(s5)) * 0.5f
                ch == 4 -> (kotlin.math.abs(s2) + kotlin.math.abs(s3)) * 0.5f
                else -> kotlin.math.abs(side)
            }
            val lfe = if (ch >= 6) kotlin.math.abs(s3) else 0f

            meters.sumL += kotlin.math.abs(s0)
            meters.sumR += kotlin.math.abs(s1)
            meters.sumSide += kotlin.math.abs(side)
            meters.sumSur += surround
            meters.sumLfe += lfe
            val abs = kotlin.math.abs(mid)
            if (abs > meters.peak) meters.peak = abs
            midScratch[i] = mid
        }
    }
}

/** Latency history ring for delayed spectrum display. */
internal class SpectrumHistory(
    private val bandCount: Int,
    private val waveCount: Int,
    private val history: Int,
) {
    private val histBands = Array(history) { FloatArray(bandCount) }
    private val histWave = Array(history) { FloatArray(waveCount) }
    private val histLeft = FloatArray(history)
    private val histRight = FloatArray(history)
    private val histSide = FloatArray(history)
    private val histSurround = FloatArray(history)
    private val histLfe = FloatArray(history)
    private val histTimeMs = LongArray(history)
    private var histWrite = 0
    private var histCount = 0

    fun clear() {
        histWrite = 0
        histCount = 0
    }

    fun push(
        bands: FloatArray,
        wave: FloatArray,
        left: Float,
        right: Float,
        side: Float,
        surround: Float,
        lfe: Float,
    ) {
        val slot = histWrite
        System.arraycopy(bands, 0, histBands[slot], 0, bandCount)
        System.arraycopy(wave, 0, histWave[slot], 0, waveCount)
        histLeft[slot] = left
        histRight[slot] = right
        histSide[slot] = side
        histSurround[slot] = surround
        histLfe[slot] = lfe
        histTimeMs[slot] = SystemClock.elapsedRealtime()
        histWrite = (histWrite + 1) % history
        if (histCount < history) histCount++
    }

    fun present(
        delayMs: Long,
        bandsInternal: FloatArray,
        waveInternal: FloatArray,
        leftInternal: Float,
        rightInternal: Float,
        sideInternal: Float,
        surroundInternal: Float,
        lfeInternal: Float,
        bandsPub: FloatArray,
        wavePub: FloatArray,
        onChannels: (Float, Float, Float, Float, Float) -> Unit,
    ) {
        if (histCount == 0) {
            System.arraycopy(bandsInternal, 0, bandsPub, 0, bandCount)
            System.arraycopy(waveInternal, 0, wavePub, 0, waveCount)
            onChannels(leftInternal, rightInternal, sideInternal, surroundInternal, lfeInternal)
            return
        }
        val delay = delayMs.coerceIn(0L, 900L)
        val best = if (delay <= 0L) {
            (histWrite - 1 + history) % history
        } else {
            val target = SystemClock.elapsedRealtime() - delay
            var found = -1
            var i = 0
            while (i < histCount) {
                val idx = (histWrite - 1 - i + history * 2) % history
                if (histTimeMs[idx] <= target) {
                    found = idx
                    break
                }
                i++
            }
            if (found < 0) (histWrite - histCount + history) % history else found
        }
        System.arraycopy(histBands[best], 0, bandsPub, 0, bandCount)
        System.arraycopy(histWave[best], 0, wavePub, 0, waveCount)
        onChannels(histLeft[best], histRight[best], histSide[best], histSurround[best], histLfe[best])
    }
}
