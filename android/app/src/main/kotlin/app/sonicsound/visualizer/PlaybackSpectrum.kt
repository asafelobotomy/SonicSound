package app.sonicsound.visualizer

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import java.nio.ByteOrder
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min

/** Live spectrum from LibVLC PCM ([app.sonicsound.playback.VlcPcmOutput]). */
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
    private val hann = SpectrumFft.buildHann(FFT_SIZE)
    private val bandBinStart = IntArray(BAND_COUNT)
    private val bandBinEnd = IntArray(BAND_COUNT)
    private val rawBands = FloatArray(BAND_COUNT)
    private val bandsInternal = FloatArray(BAND_COUNT)
    private val waveInternal = FloatArray(WAVE_COUNT)
    private val bandsPub = FloatArray(BAND_COUNT)
    private val wavePub = FloatArray(WAVE_COUNT)

    private val historyRing = SpectrumHistory(BAND_COUNT, WAVE_COUNT, HISTORY)

    private val staging = SpectrumStaging(STAGING_SLOTS, FFT_SIZE)
    private val analyzeScratch = FloatArray(FFT_SIZE)

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

    @Volatile private var pubBpm = 110f; @Volatile private var pubAttackHz = 28f
    @Volatile private var pubReleaseHz = 11f; @Volatile private var pubIntensity = 0.5f
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
                historyRing.clear()
                staging.clear()
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
            historyRing.clear()
            staging.clear()
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
            staging.clear()
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

    /** Fast PCM ingest for the audio callback. */
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
        val meters = SpectrumPcmIngest.BatchMeters()
        var f0 = 0
        while (f0 < frameCount) {
            val n = min(frameCount - f0, midScratch.size)
            SpectrumPcmIngest.demuxBatch(pcm, bytes, ch, f0, n, little, midScratch, meters)
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
                        staging.offer(window, writePos)
                        scheduleAnalyze = true
                    }
                }
            }
            f0 += n
        }
        lock.withLock {
            val inv = 1f / frameCount
            val a = 0.22f
            leftInternal = leftInternal * (1f - a) + (meters.sumL * inv) * a
            rightInternal = rightInternal * (1f - a) + (meters.sumR * inv) * a
            sideInternal = sideInternal * (1f - a) + (meters.sumSide * inv) * a
            surroundInternal = surroundInternal * (1f - a) + (meters.sumSur * inv) * a
            lfeInternal = lfeInternal * (1f - a) + (meters.sumLfe * inv) * a
            pcmPeak = pcmPeak * 0.85f + meters.peak * 0.15f
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

    private fun drainStaging() {
        while (true) {
            lock.withLock {
                if (!staging.take(analyzeScratch)) return
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

    fun tickDecay(playing: Boolean) {
        if (playing) return
        if (SystemClock.uptimeMillis() < decayGraceUntilMs) return
        if (!lock.tryLock()) return
        try {
            SpectrumAgc.tickDecay(
                bandsInternal, waveInternal,
                push = { pushHistoryLocked() },
                present = { presentLocked(displayDelayMs) },
                clearSignal = { hasSignal = false },
            )
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
        historyRing.push(
            bandsInternal, waveInternal,
            leftInternal, rightInternal, sideInternal, surroundInternal, lfeInternal,
        )
    }

    private fun presentLocked(delayMs: Long) {
        historyRing.present(
            delayMs,
            bandsInternal, waveInternal,
            leftInternal, rightInternal, sideInternal, surroundInternal, lfeInternal,
            bandsPub, wavePub,
        ) { l, r, s, sur, lfe ->
            leftPub = l; rightPub = r; sidePub = s; surroundPub = sur; lfePub = lfe
        }
    }

    private fun ensureBandPlanLocked() {
        if (bandPlanRate == sampleRate) return
        bandPlanRate = sampleRate
        SpectrumFft.ensureBandPlan(sampleRate, FFT_SIZE, BAND_COUNT, bandBinStart, bandBinEnd)
    }

    private fun computeBandMagnitudes(staging: FloatArray, outMags: FloatArray) =
        SpectrumFft.computeBandMagnitudes(
            staging, outMags, fftRe, fftIm, hann, bandBinStart, bandBinEnd, FFT_SIZE, BAND_COUNT,
        )

    private fun applyAgcAndEnvelopesLocked(mags: FloatArray) {
        agcGain = SpectrumAgc.apply(
            mags, rawBands, bandsInternal, character, BAND_COUNT, FFT_HOP, sampleRate, pcmPeak, agcGain,
        )
    }

    private fun average(start: Int, endExclusive: Int): Float =
        SpectrumAgc.average(bandsPub, BAND_COUNT, start, endExclusive)
}
