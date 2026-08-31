package app.sonicsound.visualizer

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Online song character: BPM / tempo feel, loudness, and dynamic range.
 *
 * Settles in ~1–3s of audio (faster with a [seed] from next-track prefetch).
 */
class TrackCharacter {
    var bpm: Float = 110f
        private set
    var intensity: Float = 0.45f
        private set
    var dynamicRange: Float = 0.5f
        private set
    var punch: Float = 0.4f
        private set
    var attackHz: Float = 28f
        private set
    var releaseHz: Float = 11f
        private set
    var floorMag: Float = 1e-5f
        private set
    var ceilMag: Float = 0.05f
        private set

    /** 0..1 how trusted the current estimate is (prefetch starts high). */
    var confidence: Float = 0f
        private set

    private val fluxHistory = FloatArray(FLUX_LEN)
    private var fluxWrite = 0
    private var fluxCount = 0
    private var prevFlux = 0f
    private var energyEma = 0f
    private var energyVarEma = 0f
    private var peakEma = 0f
    private var troughEma = 0f
    private var frames = 0
    private var lastOnsetMs = 0L
    private val onsetGaps = FloatArray(ONSET_GAPS)
    private var onsetGapWrite = 0
    private var onsetGapCount = 0
    private var lastBandEnergy = FloatArray(0)
    /** First ~1.2s after (re)start: adapt aggressively. */
    private var bootstrapFrames = BOOTSTRAP_FRAMES

    data class Snapshot(
        val bpm: Float,
        val intensity: Float,
        val dynamicRange: Float,
        val punch: Float,
        val floorMag: Float,
        val ceilMag: Float,
        val energyEma: Float,
        val peakEma: Float,
        val troughEma: Float,
        val confidence: Float,
    )

    fun capture(): Snapshot = Snapshot(
        bpm, intensity, dynamicRange, punch, floorMag, ceilMag,
        energyEma, peakEma, troughEma, confidence,
    )

    fun reset() {
        bpm = 110f
        intensity = 0.45f
        dynamicRange = 0.5f
        punch = 0.4f
        attackHz = 28f
        releaseHz = 11f
        floorMag = 1e-5f
        ceilMag = 0.05f
        confidence = 0f
        fluxWrite = 0
        fluxCount = 0
        prevFlux = 0f
        energyEma = 0f
        energyVarEma = 0f
        peakEma = 0f
        troughEma = 0f
        frames = 0
        lastOnsetMs = 0L
        onsetGapWrite = 0
        onsetGapCount = 0
        fluxHistory.fill(0f)
        onsetGaps.fill(0f)
        lastBandEnergy = FloatArray(0)
        bootstrapFrames = BOOTSTRAP_FRAMES
        refreshEnvelopes()
    }

    /** Apply a prefetch / prior estimate so the first beat already looks right. */
    fun seed(snapshot: Snapshot) {
        bpm = snapshot.bpm.coerceIn(50f, 200f)
        intensity = snapshot.intensity.coerceIn(0f, 1f)
        dynamicRange = snapshot.dynamicRange.coerceIn(0f, 1f)
        punch = snapshot.punch.coerceIn(0f, 1f)
        floorMag = snapshot.floorMag.coerceAtLeast(1e-8f)
        ceilMag = snapshot.ceilMag.coerceAtLeast(floorMag * 2f)
        energyEma = snapshot.energyEma.coerceAtLeast(0f)
        peakEma = snapshot.peakEma.coerceAtLeast(energyEma)
        troughEma = snapshot.troughEma.coerceAtLeast(0f)
        confidence = snapshot.confidence.coerceIn(0.15f, 0.95f)
        // Short bootstrap — refine, don't relearn from scratch.
        bootstrapFrames = (BOOTSTRAP_FRAMES * (1f - confidence * 0.7f)).toInt().coerceIn(20, BOOTSTRAP_FRAMES)
        refreshEnvelopes()
    }

    fun observe(scaledBands: FloatArray, nowMs: Long) {
        frames++
        val boot = bootstrapFrames > 0
        if (boot) bootstrapFrames--

        var energy = 0f
        var peak = 0f
        var flux = 0f
        if (lastBandEnergy.size != scaledBands.size) {
            lastBandEnergy = FloatArray(scaledBands.size)
        }
        for (i in scaledBands.indices) {
            val m = scaledBands[i]
            energy += m
            if (m > peak) peak = m
            val d = m - lastBandEnergy[i]
            if (d > 0f) flux += d
            lastBandEnergy[i] = m
        }
        energy /= scaledBands.size.coerceAtLeast(1)

        // Loudness / range — fast during bootstrap, then stable.
        val eFast = if (boot) 0.18f else 0.06f
        val peakDecay = if (boot) 0.96f else 0.985f
        if (energyEma <= 1e-12f) {
            energyEma = energy
            peakEma = peak
            troughEma = energy
        } else {
            energyEma = energyEma * (1f - eFast) + energy * eFast
            peakEma = max(peakEma * peakDecay, peak)
            troughEma = if (energy < troughEma) {
                troughEma * 0.55f + energy * 0.45f
            } else {
                val tSlow = if (boot) 0.012f else 0.004f
                troughEma * (1f - tSlow) + energy * tSlow
            }
        }
        val delta = energy - energyEma
        val vFast = if (boot) 0.08f else 0.035f
        energyVarEma = energyVarEma * (1f - vFast) + delta * delta * vFast

        fluxHistory[fluxWrite] = flux
        fluxWrite = (fluxWrite + 1) % FLUX_LEN
        if (fluxCount < FLUX_LEN) fluxCount++

        val fluxMean = meanFlux()
        val threshold = fluxMean * 1.45f + peakEma * 0.015f + 1e-6f
        val rising = flux > threshold && flux > prevFlux * 1.04f
        prevFlux = flux
        // Accept onsets almost immediately (was frames > 20).
        if (rising && frames > 4) {
            if (lastOnsetMs > 0L) {
                val gapMs = (nowMs - lastOnsetMs).toFloat()
                if (gapMs in 180f..1_800f) {
                    onsetGaps[onsetGapWrite] = gapMs
                    onsetGapWrite = (onsetGapWrite + 1) % ONSET_GAPS
                    if (onsetGapCount < ONSET_GAPS) onsetGapCount++
                    updateBpmFromGaps(boot)
                }
            }
            lastOnsetMs = nowMs
        }

        val softFloor = (troughEma * 0.55f + energyEma * 0.08f).coerceAtLeast(1e-7f)
        val softCeil = (peakEma * 0.92f + energyEma * 2.2f).coerceAtLeast(softFloor * 4f)
        val fBlend = if (boot) 0.22f else 0.08f
        floorMag = floorMag * (1f - fBlend) + softFloor * fBlend
        ceilMag = ceilMag * (1f - fBlend) + softCeil * fBlend

        val rangeRatio = ((ceilMag - floorMag) / (energyEma + 1e-8f)).coerceIn(0.5f, 40f)
        dynamicRange = ((ln(rangeRatio) / ln(25.0)).toFloat()).coerceIn(0f, 1f)
        intensity = ((energyEma / (ceilMag + 1e-8f)) * 0.65f + (1f - dynamicRange) * 0.35f)
            .coerceIn(0f, 1f)
        val punchInstant = ((flux / (fluxMean + 1e-8f) - 0.55f) * 0.4f).coerceIn(0f, 1f)
        punch = punch * (if (boot) 0.65f else 0.82f) + punchInstant * (if (boot) 0.35f else 0.18f)

        confidence = (confidence + if (boot) 0.04f else 0.008f).coerceIn(0f, 1f)
        refreshEnvelopes()
    }

    fun normalize(mag: Float): Float {
        val lo = floorMag
        val hi = max(ceilMag, lo * 1.5f)
        val t = ((mag - lo) / (hi - lo)).coerceIn(0f, 1f)
        val gamma = (0.72f + dynamicRange * 0.28f - intensity * 0.08f).coerceIn(0.55f, 0.95f)
        return t.pow(gamma).coerceIn(0f, 1f)
    }

    fun envelope(prev: Float, target: Float, hopSec: Float = 0.006f): Float {
        val hop = hopSec.coerceIn(0.002f, 0.02f)
        val rate = if (target > prev) attackHz * 1.35f else releaseHz * 1.15f
        val a = (1f - exp(-hop * rate)).coerceIn(0.05f, 0.92f)
        return prev + (target - prev) * a
    }

    private fun refreshEnvelopes() {
        val tempo = bpm.coerceIn(50f, 200f)
        val beatHz = tempo / 60f
        val baseAttack = (beatHz * 2.4f + 10f + punch * 14f).coerceIn(12f, 48f)
        val baseRelease = (beatHz * 0.85f + 4f + (1f - dynamicRange) * 4f).coerceIn(5f, 22f)
        attackHz = baseAttack * (0.85f + intensity * 0.3f)
        releaseHz = baseRelease * (0.75f + dynamicRange * 0.45f)
    }

    private fun meanFlux(): Float {
        if (fluxCount == 0) return 0f
        var s = 0f
        for (i in 0 until fluxCount) s += fluxHistory[i]
        return s / fluxCount
    }

    private fun updateBpmFromGaps(boot: Boolean) {
        // One solid gap is enough to move off the default; median after a few.
        if (onsetGapCount < 1) return
        val tmp = FloatArray(onsetGapCount)
        for (i in 0 until onsetGapCount) tmp[i] = onsetGaps[i]
        tmp.sort()
        val median = tmp[onsetGapCount / 2]
        var estimate = 60_000f / median
        while (estimate < 70f) estimate *= 2f
        while (estimate > 180f) estimate *= 0.5f
        val blend = when {
            boot && onsetGapCount <= 2 -> 0.55f
            boot -> 0.35f
            onsetGapCount <= 2 -> 0.28f
            else -> 0.18f
        }
        bpm = bpm * (1f - blend) + estimate * blend
    }

    companion object {
        private const val FLUX_LEN = 64
        private const val ONSET_GAPS = 10
        /** ~1.2s at ~170 FFT/s. */
        private const val BOOTSTRAP_FRAMES = 200
    }
}
