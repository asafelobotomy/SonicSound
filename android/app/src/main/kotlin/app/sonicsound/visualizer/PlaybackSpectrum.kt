package app.sonicsound.visualizer

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import java.nio.ByteOrder
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Live spectrum fed from LibVLC decoded PCM ([app.sonicsound.playback.VlcPcmOutput]).
 *
 * Pipeline:
 * 1. Audio callback [onPcmS16] demuxes into a circular window + channel meters (fast)
 * 2. On each FFT hop, a window snapshot is queued to a dedicated analyzer thread
 * 3. Analyzer runs FFT / AGC / TrackCharacter and pushes a latency history ring
 * 4. UI [presentForDisplay] reads `now - outputLatency`
 */
object PlaybackSpectrum {
    private const val FFT_SIZE = 1024
    private const val BAND_COUNT = 64
    private const val WAVE_COUNT = 128
    private const val FFT_HOP = FFT_SIZE / 4
    private const val HISTORY = 200
    private const val STAGING_SLOTS = 4

    private val window = FloatArray(FFT_SIZE)
    private val fftRe = FloatArray(FFT_SIZE)
    private val fftIm = FloatArray(FFT_SIZE)
    private val hann = FloatArray(FFT_SIZE) { i ->
        (0.5f * (1.0 - cos(2.0 * PI * i / (FFT_SIZE - 1)))).toFloat()
    }
    private val bandBinStart = IntArray(BAND_COUNT)
    private val bandBinEnd = IntArray(BAND_COUNT)
    private val rawBands = FloatArray(BAND_COUNT)
    private val bandsInternal = FloatArray(BAND_COUNT)
    private val waveInternal = FloatArray(WAVE_COUNT)
    private val bandsPub = FloatArray(BAND_COUNT)
    private val wavePub = FloatArray(WAVE_COUNT)

    private val histBands = Array(HISTORY) { FloatArray(BAND_COUNT) }
    private val histWave = Array(HISTORY) { FloatArray(WAVE_COUNT) }
    private val histLeft = FloatArray(HISTORY)
    private val histRight = FloatArray(HISTORY)
    private val histSide = FloatArray(HISTORY)
    private val histSurround = FloatArray(HISTORY)
    private val histLfe = FloatArray(HISTORY)
    private val histTimeMs = LongArray(HISTORY)
    private var histWrite = 0
    private var histCount = 0

    /** Chronological mono snapshots waiting for FFT (oldest sample at index 0). */
    private val stagingWindows = Array(STAGING_SLOTS) { FloatArray(FFT_SIZE) }
    private val analyzeScratch = FloatArray(FFT_SIZE)
    private var stagingRead = 0
    private var stagingPending = 0

    private var writePos = 0
    private var framesSinceFft = 0
    private var sampleRate = 44_100
    private var bandPlanRate = -1
    private var agcGain = 1f
    private var pcmPeak = 0f
    private val character = TrackCharacter()
    private var currentSongId: String? = null

    private var leftInternal = 0f
    private var rightInternal = 0f
    private var sideInternal = 0f
    private var surroundInternal = 0f
    private var lfeInternal = 0f
    private var leftPub = 0f
    private var rightPub = 0f
    private var sidePub = 0f
    private var surroundPub = 0f
    private var lfePub = 0f
    private var channelCount = 2

    @Volatile
    private var displayDelayMs = 0L
    @Volatile
    private var decayGraceUntilMs = 0L
    /** Shared by ingest / analyzer / UI. UI present uses tryLock so FLAC bursts don't stall frames. */
    private val lock = ReentrantLock()
    /** Demux scratch for the audio callback — filled outside [lock], committed under it. */
    private var midScratch = FloatArray(4096)

    @Volatile
    private var hasSignal = false

    @Volatile private var pubBpm = 110f
    @Volatile private var pubAttackHz = 28f
    @Volatile private var pubReleaseHz = 11f
    @Volatile private var pubIntensity = 0.5f
    @Volatile private var pubDynamicRange = 0.5f

    private val analyzerThread = HandlerThread("ss-spectrum", Process.THREAD_PRIORITY_AUDIO).apply { start() }
    private val analyzerHandler = Handler(analyzerThread.looper)
    private val analyzeRunnable = Runnable { drainStaging() }
    /** Band magnitudes from unlocked FFT — published under [lock]. */
    private val magScratch = FloatArray(BAND_COUNT)

    val active: Boolean get() = hasSignal
    val bandCount: Int get() = BAND_COUNT
    val waveCount: Int get() = WAVE_COUNT
    val lastPcmPeak: Float get() = lock.withLock { pcmPeak }
    val displayDelayMsValue: Long get() = displayDelayMs
    val pcmChannelCount: Int get() = lock.withLock { channelCount }

    fun estimatedBpm(): Float = pubBpm
    fun attackHz(): Float = pubAttackHz
    fun releaseHz(): Float = pubReleaseHz
    fun intensity(): Float = pubIntensity
    fun dynamicRange(): Float = pubDynamicRange

    fun left(): Float = leftPub
    fun right(): Float = rightPub
    fun side(): Float = sidePub
    fun surround(): Float = surroundPub
    fun lfe(): Float = lfePub

    fun setDisplayDelayMs(ms: Long) {
        displayDelayMs = ms.coerceIn(0L, 900L)
    }

    fun prepareForTrack(songId: String) {
        lock.withLock {
            val prevId = currentSongId
            val sameTrack = prevId != null && prevId == songId
            if (!sameTrack && prevId != null && character.confidence > 0.2f) {
                TrackCharacterPrefetch.remember(prevId, character.capture())
            }
            currentSongId = songId
            if (!sameTrack) {
                val snap = TrackCharacterPrefetch.consumeForPlayback(songId)
                character.reset()
                if (snap != null) character.seed(snap)
                histWrite = 0
                histCount = 0
                clearStagingLocked()
            }
            window.fill(0f)
            writePos = 0
            framesSinceFft = 0
            pcmPeak *= if (sameTrack) 0.55f else 0.35f
            val scale = if (sameTrack) 0.55f else 0.35f
            for (i in bandsInternal.indices) {
                bandsInternal[i] *= scale
                rawBands[i] *= scale
            }
            leftInternal *= scale
            rightInternal *= scale
            sideInternal *= scale
            surroundInternal *= scale
            lfeInternal *= scale
            if (!sameTrack) {
                pushHistoryLocked()
                presentLocked(0L)
            }
            publishCharacterUnlocked()
            decayGraceUntilMs = SystemClock.uptimeMillis() + 1_800L
        }
        analyzerHandler.removeCallbacks(analyzeRunnable)
    }

    fun band(index: Int): Float {
        val i = index.coerceIn(0, BAND_COUNT - 1)
        return bandsPub[i]
    }

    fun waveAt(index: Int): Float {
        val i = index.coerceIn(0, WAVE_COUNT - 1)
        return wavePub[i]
    }

    fun bass(): Float {
        val bandBass = average(1, (BAND_COUNT / 8).coerceAtLeast(2))
        return (bandBass * 0.72f + lfePub.coerceIn(0f, 1f) * 0.55f).coerceIn(0f, 1f)
    }

    fun mids(): Float {
        val start = (BAND_COUNT / 8).coerceAtLeast(2)
        val end = (BAND_COUNT / 2).coerceAtLeast(start + 1)
        return average(start, end)
    }

    fun energy(): Float = average(1, BAND_COUNT)

    fun presentForDisplay() {
        // Never block the frame loop behind a large FLAC PCM ingest / FFT publish.
        if (!lock.tryLock()) return
        try {
            presentLocked(displayDelayMs)
        } finally {
            lock.unlock()
        }
    }

    fun reset() {
        analyzerHandler.removeCallbacks(analyzeRunnable)
        lock.withLock {
            window.fill(0f)
            bandsInternal.fill(0f)
            rawBands.fill(0f)
            waveInternal.fill(0f)
            writePos = 0
            framesSinceFft = 0
            hasSignal = false
            pcmPeak = 0f
            agcGain = 1f
            character.reset()
            currentSongId = null
            leftInternal = 0f
            rightInternal = 0f
            sideInternal = 0f
            surroundInternal = 0f
            lfeInternal = 0f
            leftPub = 0f
            rightPub = 0f
            sidePub = 0f
            surroundPub = 0f
            lfePub = 0f
            decayGraceUntilMs = 0L
            bandsPub.fill(0f)
            wavePub.fill(0f)
            histWrite = 0
            histCount = 0
            clearStagingLocked()
            publishCharacterUnlocked()
        }
    }

    fun softReset() {
        analyzerHandler.removeCallbacks(analyzeRunnable)
        lock.withLock {
            window.fill(0f)
            writePos = 0
            framesSinceFft = 0
            pcmPeak *= 0.45f
            clearStagingLocked()
            for (i in bandsInternal.indices) {
                bandsInternal[i] *= 0.45f
                rawBands[i] *= 0.45f
            }
            for (i in waveInternal.indices) waveInternal[i] *= 0.2f
            leftInternal *= 0.45f
            rightInternal *= 0.45f
            sideInternal *= 0.45f
            surroundInternal *= 0.45f
            lfeInternal *= 0.45f
            pushHistoryLocked()
            presentLocked(displayDelayMs)
            hasSignal = bandsInternal.any { it > 0.002f }
            decayGraceUntilMs = SystemClock.uptimeMillis() + 1_800L
        }
    }

    /**
     * Fast PCM ingest for the audio callback. Demux runs outside [lock]; only the
     * window/staging commit is locked so UI [presentForDisplay] is not blocked by
     * large FLAC decode bursts from LibVLC.
     */
    fun onPcmS16(
        pcm: ByteArray,
        bytes: Int,
        channels: Int,
        frames: Int,
        order: ByteOrder,
        rateHz: Int = 44_100,
    ) {
        if (bytes < 2 || channels < 1 || frames < 1) return
        val ch = channels.coerceIn(1, 8)
        val little = order == ByteOrder.LITTLE_ENDIAN
        val frameCount = (bytes / (2 * ch)).coerceAtMost(frames)
        if (frameCount <= 0) return

        var scheduleAnalyze = false
        var batchPeak = 0f
        var sumL = 0f
        var sumR = 0f
        var sumSide = 0f
        var sumSur = 0f
        var sumLfe = 0f
        var f0 = 0
        while (f0 < frameCount) {
            val n = min(frameCount - f0, midScratch.size)
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

                sumL += kotlin.math.abs(s0)
                sumR += kotlin.math.abs(s1)
                sumSide += kotlin.math.abs(side)
                sumSur += surround
                sumLfe += lfe
                val abs = kotlin.math.abs(mid)
                if (abs > batchPeak) batchPeak = abs
                midScratch[i] = mid
            }
            lock.withLock {
                hasSignal = true
                channelCount = ch
                if (rateHz >= 8000) sampleRate = rateHz
                ensureBandPlanLocked()
                for (i in 0 until n) {
                    window[writePos] = midScratch[i]
                    writePos = (writePos + 1) % FFT_SIZE
                    framesSinceFft++
                    if (framesSinceFft >= FFT_HOP) {
                        framesSinceFft = 0
                        offerStagingLocked()
                        scheduleAnalyze = true
                    }
                }
            }
            f0 += n
        }
        lock.withLock {
            val inv = 1f / frameCount
            val a = 0.22f
            leftInternal = leftInternal * (1f - a) + (sumL * inv) * a
            rightInternal = rightInternal * (1f - a) + (sumR * inv) * a
            sideInternal = sideInternal * (1f - a) + (sumSide * inv) * a
            surroundInternal = surroundInternal * (1f - a) + (sumSur * inv) * a
            lfeInternal = lfeInternal * (1f - a) + (sumLfe * inv) * a
            pcmPeak = pcmPeak * 0.85f + batchPeak * 0.15f
            leftPub = leftInternal
            rightPub = rightInternal
            sidePub = sideInternal
            surroundPub = surroundInternal
            lfePub = lfeInternal
        }
        if (scheduleAnalyze) {
            analyzerHandler.removeCallbacks(analyzeRunnable)
            analyzerHandler.post(analyzeRunnable)
        }
    }

    private fun offerStagingLocked() {
        if (stagingPending == STAGING_SLOTS) {
            stagingRead = (stagingRead + 1) % STAGING_SLOTS
            stagingPending--
        }
        val slot = (stagingRead + stagingPending) % STAGING_SLOTS
        val dest = stagingWindows[slot]
        for (i in 0 until FFT_SIZE) {
            dest[i] = window[(writePos + i) % FFT_SIZE]
        }
        stagingPending++
    }

    private fun clearStagingLocked() {
        stagingRead = 0
        stagingPending = 0
    }

    private fun drainStaging() {
        while (true) {
            lock.withLock {
                if (stagingPending <= 0) return
                System.arraycopy(stagingWindows[stagingRead], 0, analyzeScratch, 0, FFT_SIZE)
                stagingRead = (stagingRead + 1) % STAGING_SLOTS
                stagingPending--
                ensureBandPlanLocked()
            }
            // FFT stays off the ingest/UI lock — this was stalling Choreographer (~70ms+ tails).
            computeBandMagnitudes(analyzeScratch, magScratch)
            lock.withLock {
                applyAgcAndEnvelopesLocked(magScratch)
                updateWaveFromStagingLocked(analyzeScratch)
                pushHistoryLocked()
                presentLocked(displayDelayMs)
                publishCharacterUnlocked()
            }
        }
    }

    private fun publishCharacterUnlocked() {
        pubBpm = character.bpm
        pubAttackHz = character.attackHz
        pubReleaseHz = character.releaseHz
        pubIntensity = character.intensity
        pubDynamicRange = character.dynamicRange
    }

    private fun readS16(pcm: ByteArray, index: Int, bytes: Int, little: Boolean): Float {
        if (index + 1 >= bytes) return 0f
        val sample = if (little) {
            ((pcm[index].toInt() and 0xFF) or (pcm[index + 1].toInt() shl 8)).toShort()
        } else {
            ((pcm[index + 1].toInt() and 0xFF) or (pcm[index].toInt() shl 8)).toShort()
        }
        return sample / 32768f
    }

    fun tickDecay(playing: Boolean) {
        if (playing) return
        if (SystemClock.uptimeMillis() < decayGraceUntilMs) return
        if (!lock.tryLock()) return
        try {
            for (i in bandsInternal.indices) {
                bandsInternal[i] *= 0.88f
                if (bandsInternal[i] < 0.001f) bandsInternal[i] = 0f
            }
            for (i in waveInternal.indices) {
                waveInternal[i] *= 0.88f
            }
            pushHistoryLocked()
            presentLocked(displayDelayMs)
            if (bandsInternal.all { it == 0f }) hasSignal = false
        } finally {
            lock.unlock()
        }
    }

    private fun updateWaveFromStagingLocked(staging: FloatArray) {
        val waveGain = (2.8f + agcGain * 1.2f).coerceIn(2f, 12f)
        val base = FFT_SIZE - WAVE_COUNT
        for (i in 0 until WAVE_COUNT) {
            waveInternal[i] = (staging[base + i] * waveGain).coerceIn(-1f, 1f)
        }
    }

    private fun pushHistoryLocked() {
        val slot = histWrite
        System.arraycopy(bandsInternal, 0, histBands[slot], 0, BAND_COUNT)
        System.arraycopy(waveInternal, 0, histWave[slot], 0, WAVE_COUNT)
        histLeft[slot] = leftInternal
        histRight[slot] = rightInternal
        histSide[slot] = sideInternal
        histSurround[slot] = surroundInternal
        histLfe[slot] = lfeInternal
        histTimeMs[slot] = SystemClock.elapsedRealtime()
        histWrite = (histWrite + 1) % HISTORY
        if (histCount < HISTORY) histCount++
    }

    private fun presentLocked(delayMs: Long) {
        if (histCount == 0) {
            System.arraycopy(bandsInternal, 0, bandsPub, 0, BAND_COUNT)
            System.arraycopy(waveInternal, 0, wavePub, 0, WAVE_COUNT)
            leftPub = leftInternal
            rightPub = rightInternal
            sidePub = sideInternal
            surroundPub = surroundInternal
            lfePub = lfeInternal
            return
        }
        val delay = delayMs.coerceIn(0L, 900L)
        val best = if (delay <= 0L) {
            (histWrite - 1 + HISTORY) % HISTORY
        } else {
            val target = SystemClock.elapsedRealtime() - delay
            var found = -1
            var i = 0
            while (i < histCount) {
                val idx = (histWrite - 1 - i + HISTORY * 2) % HISTORY
                if (histTimeMs[idx] <= target) {
                    found = idx
                    break
                }
                i++
            }
            if (found < 0) (histWrite - histCount + HISTORY) % HISTORY else found
        }
        System.arraycopy(histBands[best], 0, bandsPub, 0, BAND_COUNT)
        System.arraycopy(histWave[best], 0, wavePub, 0, WAVE_COUNT)
        leftPub = histLeft[best]
        rightPub = histRight[best]
        sidePub = histSide[best]
        surroundPub = histSurround[best]
        lfePub = histLfe[best]
    }

    private fun ensureBandPlanLocked() {
        if (bandPlanRate == sampleRate) return
        bandPlanRate = sampleRate
        val usable = FFT_SIZE / 2
        val nyquist = sampleRate * 0.5f
        val fMin = 40f
        val fMax = (nyquist * 0.92f).coerceAtLeast(fMin * 2f)
        val logMin = ln(fMin.toDouble())
        val logMax = ln(fMax.toDouble())
        for (b in 0 until BAND_COUNT) {
            val t0 = b.toDouble() / BAND_COUNT
            val t1 = (b + 1).toDouble() / BAND_COUNT
            val freq0 = kotlin.math.exp(logMin + (logMax - logMin) * t0).toFloat()
            val freq1 = kotlin.math.exp(logMin + (logMax - logMin) * t1).toFloat()
            val start = (freq0 * FFT_SIZE / sampleRate).toInt().coerceIn(1, usable - 1)
            val end = (freq1 * FFT_SIZE / sampleRate).toInt().coerceIn(start + 1, usable)
            bandBinStart[b] = start
            bandBinEnd[b] = end
        }
    }

    private fun computeBandMagnitudes(staging: FloatArray, outMags: FloatArray) {
        for (i in 0 until FFT_SIZE) {
            fftRe[i] = staging[i] * hann[i]
            fftIm[i] = 0f
        }
        fftRadix2(fftRe, fftIm)

        for (b in 0 until BAND_COUNT) {
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

    private fun applyAgcAndEnvelopesLocked(mags: FloatArray) {
        var framePeak = 0f
        var frameSum = 0f
        for (b in 0 until BAND_COUNT) {
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

        for (b in 0 until BAND_COUNT) {
            rawBands[b] *= agcGain
        }
        character.observe(rawBands, SystemClock.elapsedRealtime())

        val mean = (frameSum * agcGain / BAND_COUNT).coerceAtLeast(1e-8f)
        val hopSec = FFT_HOP.toFloat() / sampleRate.coerceAtLeast(8_000).toFloat()
        for (b in 0 until BAND_COUNT) {
            val mag = rawBands[b]
            var norm = character.normalize(mag)
            val relative = (mag / mean).coerceIn(0.12f, 10f)
            val contrastPow = (0.42f + character.dynamicRange * 0.12f).coerceIn(0.35f, 0.55f)
            val contrast = relative.pow(contrastPow).coerceIn(0.4f, 2.6f)
            norm = (norm * contrast).coerceIn(0f, 1f)
            bandsInternal[b] = character.envelope(bandsInternal[b], norm, hopSec)
        }
    }

    private fun average(start: Int, endExclusive: Int): Float {
        val a = start.coerceIn(0, BAND_COUNT - 1)
        val b = endExclusive.coerceIn(a + 1, BAND_COUNT)
        var sum = 0f
        for (i in a until b) sum += bandsPub[i]
        return (sum / (b - a)).coerceIn(0f, 1f)
    }

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
