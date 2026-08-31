package app.sonicsound.playback

import android.util.Log
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

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
    private const val MAX_CHANNELS = 8
    private const val HISTORY_MS = 280
    private const val WOW_DELAY_MS = 14
    private const val MAX_SKIP_MS = 180
    private const val MIN_SKIP_MS = 40
    private const val BUFFER_RATE_HZ = 48_000

    @Volatile
    private var enabled = false

    @Volatile
    private var intensity = 0.12f

    @Volatile
    private var skipsEnabled = false

    @Volatile
    private var sampleRate = 44100

    @Volatile
    private var positionMs = 0L

    @Volatile
    private var durationMs = 0L

    @Volatile
    private var fxActiveLogged = false

    private val rng = XorShift32(0xC0FFEE42L)
    private val nativeLittle = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN

    private val rumbleLp = FloatArray(MAX_CHANNELS)
    private val dustLp1 = FloatArray(MAX_CHANNELS)
    private val dustLp2 = FloatArray(MAX_CHANNELS)
    private val ageLp = FloatArray(MAX_CHANNELS)
    private val bassLp = FloatArray(MAX_CHANNELS)
    private val presenceHp = FloatArray(MAX_CHANNELS)
    private val lastPresenceIn = FloatArray(MAX_CHANNELS)
    private val scoopLp = FloatArray(MAX_CHANNELS)
    private val clickBp = FloatArray(MAX_CHANNELS)
    private val crackleEnv = FloatArray(MAX_CHANNELS)
    private val crackleSign = FloatArray(MAX_CHANNELS) { 1f }
    private val popEnv = FloatArray(MAX_CHANNELS)
    private val popSign = FloatArray(MAX_CHANNELS) { 1f }
    private val frameBuf = FloatArray(MAX_CHANNELS)

    private var envFollow = 0f
    private var envPrev = 0f
    /** Brief music duck when a pop/cluster hits (felt in the song). */
    private var dipEnv = 0f

    private var wowPhase = 0.0
    private var flutterPhase = 0.0
    /** Spindle eccentricity — slow irregular second wow (not locked to flutter). */
    private var eccPhase = 0.0

    private var wowBuf = FloatArray(0)
    private var wowWrite = 0
    private var wowCapFrames = 0

    private var histBuf = FloatArray(0)
    private var histWrite = 0
    private var histCapFrames = 0
    private var histFilled = 0

    private var skipRemaining = 0
    private var skipRead = 0
    private var skipFadeIn = 0
    private var skipFadeOut = 0
    private var skipLen = 0
    private var samplesSinceSkip = 0
    private var nextSkipAt = 0

    // Sparse wear scheduler (ToneArm-style placement, realtime).
    private var samplesSinceWear = 0
    private var nextWearAt = 0
    private var clusterLeft = 0

    private val processLock = Any()

    init {
        synchronized(processLock) {
            ensureBuffersLocked(BUFFER_RATE_HZ)
        }
    }

    fun isEnabled(): Boolean = enabled

    fun configure(
        profileId: String,
        conditionId: String,
        rateHz: Int = sampleRate,
        forceDisableFx: Boolean = false,
    ) {
        val wantVinyl = profileId == AudioProfile.VINYL && !forceDisableFx
        val cond = VinylCondition.resolve(conditionId)
        val rate = rateHz.coerceAtLeast(8000)
        synchronized(processLock) {
            val was = enabled
            val wasSkips = skipsEnabled
            enabled = wantVinyl
            intensity = VinylCondition.intensity(cond)
            skipsEnabled = VinylCondition.skipsEnabled(cond)
            sampleRate = rate
            if (rate > BUFFER_RATE_HZ || wowCapFrames == 0 || histCapFrames == 0) {
                ensureBuffersLocked(max(rate, BUFFER_RATE_HZ))
            }
            if (!enabled) {
                resetStateLocked()
                fxActiveLogged = false
            } else if (!was) {
                resetStateLocked()
                scheduleNextSkipLocked()
                scheduleNextWearLocked(cluster = false)
            } else if (wasSkips && !skipsEnabled) {
                skipRemaining = 0
                samplesSinceSkip = 0
            } else if (!wasSkips && skipsEnabled) {
                scheduleNextSkipLocked()
            }
            when {
                profileId == AudioProfile.VINYL && forceDisableFx -> {
                    if (!fxActiveLogged) {
                        Log.w(TAG, "Vinyl selected but PCM tap inactive — EQ warmth only")
                        fxActiveLogged = true
                    }
                }
                enabled -> {
                    if (!was || !fxActiveLogged) {
                        val color = (0.42f + 0.58f * intensity).coerceIn(0f, 1f)
                        val ageBlend = (0.18f + 0.38f * color).coerceIn(0.16f, 0.68f)
                        val dust = 0.0018f + 0.014f * intensity
                        Log.i(
                            TAG,
                            "Vinyl FX active condition=$cond intensity=$intensity " +
                                "color=$color ageBlend=$ageBlend dust=$dust rate=$rate",
                        )
                    }
                    fxActiveLogged = true
                }
            }
        }
    }

    fun publishClock(posMs: Long, durMs: Long) {
        positionMs = posMs.coerceAtLeast(0L)
        durationMs = durMs.coerceAtLeast(0L)
    }

    fun reset() {
        synchronized(processLock) {
            resetStateLocked()
            if (skipsEnabled) scheduleNextSkipLocked()
            if (enabled) scheduleNextWearLocked(cluster = false)
        }
    }

    fun processInPlace(pcm: ByteArray, len: Int, channels: Int, frames: Int, rate: Int): Boolean {
        if (!enabled || len < 2 || frames <= 0 || channels <= 0) return false
        val ch = channels.coerceIn(1, MAX_CHANNELS)
        val fr = frames
        val byteLen = min(len, fr * ch * 2)
        if (byteLen < fr * ch * 2) return false

        synchronized(processLock) {
            if (!enabled) return false
            if (rate >= 8000) {
                sampleRate = rate
                if (wowCapFrames < (WOW_DELAY_MS * 2 * rate) / 1000 ||
                    histCapFrames < (HISTORY_MS * rate) / 1000
                ) {
                    ensureBuffersLocked(rate)
                }
            }
            val i = intensity
            // Music color vs surface wear are related but not identical axes.
            val color = (0.42f + 0.58f * i).coerceIn(0f, 1f)
            val sr = sampleRate.toFloat().coerceAtLeast(8000f)
            val twoPi = 2.0 * PI
            val little = nativeLittle

            // Platter ~0.556 Hz; flutter ~8–12 Hz; eccentricity slower irregular.
            // Depths sized to be audible on music (not only test tones).
            val wowHz = 0.556
            val flutterHz = 9.2
            val eccHz = 0.37
            val wowDepth = 0.0014 + 0.0048 * color
            val flutterDepth = 0.00035 + 0.0011 * color
            val eccDepth = 0.00055 + 0.0022 * color

            val rumbleCut = 12f + 30f * i
            val rumbleA = 1f - exp((-twoPi * rumbleCut / sr).toFloat())
            val dustCut = 800f + 900f * i
            val dustA = 1f - exp((-twoPi * dustCut / sr).toFloat())

            val dur = durationMs
            val pos = positionMs
            val sideProg = if (dur > 4_000L) {
                (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            val inner = sideProg * sideProg

            // Age LP must color without muting. Prior blend ~0.75–0.90 crushed air
            // so Brand New/Slightly read as “bass EQ only”. Keep air open.
            val ageCut = (15_000f - 6_500f * color - 2_800f * inner * color).coerceAtLeast(4500f)
            val ageA = exp((-twoPi * ageCut / sr).toFloat())
            val ageBlend = (0.18f + 0.38f * color + 0.12f * inner * color).coerceIn(0.16f, 0.68f)

            val presenceCut = 3100f
            val presenceA = exp((-twoPi * presenceCut / sr).toFloat())
            val presenceGain = 0.12f + 0.18f * color

            // Mild mid scoop — half of prior (was stacking with EQ bass).
            val scoopCut = 800f
            val scoopA = 1f - exp((-twoPi * scoopCut / sr).toFloat())
            val scoopAmt = 0.05f + 0.10f * color

            val bassCut = 125f + 45f * color
            val bassA = 1f - exp((-twoPi * bassCut / sr).toFloat())
            val monoBassMix = (0.55f + 0.35f * color).coerceIn(0f, 1f)
            val cross = 0.035f + 0.080f * color

            val clickCut = 2500f
            val clickA = 1f - exp((-twoPi * clickCut / sr).toFloat())

            // Surface events: audible under music, not tiny ticks.
            val crackleAmp = 0.010f + 0.055f * i
            val popAmp = 0.016f + 0.090f * i
            val crackleTau = 0.00030f + 0.00012f * (1f - i)
            val popTau = 0.0022f + 0.0075f * i
            val crackleDecay = exp(-1f / max(1f, crackleTau * sr))
            val popDecay = exp(-1f / max(1f, popTau * sr))
            val dipDecay = exp(-1f / max(1f, 0.014f * sr))

            val rumbleGain = 0.0012f + 0.012f * i
            // Continuous bed must be present between sparse events (esp. Slightly/Heavily).
            val dustFloor = 0.35f
            val dustFollow = 0.65f
            val dustGain = 0.0018f + 0.014f * i

            val drive = 1.06f + 0.38f * color
            val dynAmt = 0.06f + 0.16f * color
            val makeup = 1.06f + 0.05f * color
            // Music dip on pops — felt in the groove.
            val dipDepth = 0.06f + 0.12f * i

            val envAtk = 1f - exp((-twoPi * 90f / sr).toFloat())
            val envRel = 1f - exp((-twoPi * 8f / sr).toFloat())

            val delayBase = (WOW_DELAY_MS * 0.001f * sr).coerceAtLeast(2f)
            val wowCap = wowCapFrames
            val histCap = histCapFrames
            val stereo = ch >= 2

            var allowSkip = skipsEnabled && histFilled > (MIN_SKIP_MS * sampleRate / 1000)
            if (allowSkip) {
                if (pos < 2000L) allowSkip = false
                if (dur > 0L && pos > dur - 3000L) allowSkip = false
            }

            for (f in 0 until fr) {
                var skipGain = 0f
                var histSample = 0f
                if (skipsEnabled) {
                    samplesSinceSkip++
                    if (skipRemaining <= 0 && allowSkip && samplesSinceSkip >= nextSkipAt) {
                        startSkipLocked()
                    }
                    if (skipRemaining > 0 && histCap > 0) {
                        val idx = ((skipRead % histCap) + histCap) % histCap
                        histSample = histBuf[idx]
                        skipRead++
                        val elapsed = skipLen - skipRemaining
                        skipGain = when {
                            elapsed < skipFadeIn -> {
                                val t = elapsed.toFloat() / max(1, skipFadeIn)
                                0.5f - 0.5f * cos(PI.toFloat() * t.coerceIn(0f, 1f))
                            }
                            skipRemaining < skipFadeOut -> {
                                val t = skipRemaining.toFloat() / max(1, skipFadeOut)
                                0.5f - 0.5f * cos(PI.toFloat() * t.coerceIn(0f, 1f))
                            }
                            else -> 1f
                        }
                        skipRemaining--
                        if (skipRemaining <= 0) {
                            scheduleNextSkipLocked()
                        }
                    }
                }

                var frameAbs = 0f
                for (c in 0 until ch) {
                    val sampleIndex = (f * ch + c) * 2
                    val dry0 = readS16(pcm, sampleIndex, little) / 32768f
                    frameAbs += if (dry0 >= 0f) dry0 else -dry0
                }
                frameAbs /= ch
                val envCoef = if (frameAbs > envFollow) envAtk else envRel
                envFollow += envCoef * (frameAbs - envFollow)
                val onset = (envFollow - envPrev).coerceAtLeast(0f)
                envPrev = envFollow
                val groove = (dustFloor + dustFollow * envFollow.coerceIn(0f, 1f))
                    .coerceIn(0.15f, 1.1f)
                val dynGain = 1f / (1f + dynAmt * envFollow.coerceIn(0f, 1.2f))

                // Sparse wear: schedule gaps, not per-sample probability rain.
                var fireCrackle = false
                var firePop = false
                var crackleMonoSign = 1f
                var popMonoSign = 1f
                samplesSinceWear++
                if (samplesSinceWear >= nextWearAt) {
                    // Prefer pops for larger dips; crackles more often within events.
                    val wantPop = rng.next01() < (0.18f + 0.22f * i)
                    if (wantPop) {
                        firePop = true
                        popMonoSign = if (rng.next01() < 0.5f) -1f else 1f
                        dipEnv = max(dipEnv, dipDepth * (0.85f + 0.35f * rng.next01()))
                    } else {
                        fireCrackle = true
                        crackleMonoSign = if (rng.next01() < 0.5f) -1f else 1f
                        // Tiny dip so crackles are “in” the groove, not on top.
                        dipEnv = max(dipEnv, dipDepth * 0.35f * (0.7f + 0.5f * rng.next01()))
                    }
                    // On loud onsets, slightly likelier to cluster (dust in busy grooves).
                    val clusterChance = 0.18f + 0.35f * i + 0.30f * onset.coerceIn(0f, 0.2f) * 5f
                    if (clusterLeft <= 0 && rng.next01() < clusterChance) {
                        clusterLeft = 1 + (rng.next01() * (2f + 3f * i)).toInt()
                    }
                    if (clusterLeft > 0) {
                        clusterLeft--
                        scheduleNextWearLocked(cluster = true)
                    } else {
                        scheduleNextWearLocked(cluster = false)
                    }
                }
                dipEnv *= dipDecay

                val mod = 1.0 +
                    wowDepth * sin(wowPhase) +
                    flutterDepth * sin(flutterPhase) +
                    eccDepth * sin(eccPhase)
                for (c in 0 until ch) {
                    val sampleIndex = (f * ch + c) * 2
                    val dry = readS16(pcm, sampleIndex, little) / 32768f
                    var x = if (skipGain > 0f) {
                        dry * (1f - skipGain) + histSample * skipGain
                    } else {
                        dry
                    }
                    if (wowCap > 1) {
                        val delayFrames = (delayBase * mod.toFloat()).coerceIn(1f, (wowCap - 2).toFloat())
                        val readPos = wowWrite - delayFrames
                        val i0 = floorMod(floor(readPos.toDouble()).toInt(), wowCap)
                        val i1 = (i0 + 1) % wowCap
                        val frac = (readPos - floor(readPos)).coerceIn(0f, 1f)
                        val base = c * wowCap
                        val delayed = wowBuf[base + i0] * (1f - frac) + wowBuf[base + i1] * frac
                        wowBuf[base + wowWrite] = x
                        x = delayed
                    }
                    frameBuf[c] = x
                }

                if (stereo) {
                    var monoBass = 0f
                    for (c in 0 until min(ch, 2)) {
                        bassLp[c] += bassA * (frameBuf[c] - bassLp[c])
                        monoBass += bassLp[c]
                    }
                    monoBass *= 0.5f
                    for (c in 0 until min(ch, 2)) {
                        val hf = frameBuf[c] - bassLp[c]
                        frameBuf[c] = hf + bassLp[c] * (1f - monoBassMix) + monoBass * monoBassMix
                    }
                }

                val musicDip = (1f - dipEnv).coerceIn(0.82f, 1f)

                for (c in 0 until ch) {
                    var x = frameBuf[c] * dynGain * musicDip

                    // Mid scoop then presence = tighter vinyl body.
                    scoopLp[c] += scoopA * (x - scoopLp[c])
                    x = x - scoopAmt * (x - scoopLp[c])

                    presenceHp[c] = presenceA * (presenceHp[c] + x - lastPresenceIn[c])
                    lastPresenceIn[c] = x
                    x += presenceHp[c] * presenceGain

                    ageLp[c] = (1f - ageA) * x + ageA * ageLp[c]
                    x = (1f - ageBlend) * x + ageBlend * ageLp[c]

                    val white = rng.nextSigned()
                    rumbleLp[c] += rumbleA * (white - rumbleLp[c])
                    val rumble = rumbleLp[c] * rumbleGain * groove

                    dustLp1[c] += dustA * (white - dustLp1[c])
                    dustLp2[c] += dustA * (dustLp1[c] - dustLp2[c])
                    val dust = dustLp2[c] * dustGain * groove

                    clickBp[c] += clickA * (white - clickBp[c])
                    val clickTone = white - clickBp[c]

                    if (fireCrackle && crackleEnv[c] <= 0.0001f) {
                        crackleEnv[c] = 1f
                        crackleSign[c] = crackleMonoSign * (if (c == 0) 1f else 0.85f + 0.3f * rng.next01())
                    }
                    val crackle = crackleSign[c] * crackleEnv[c] * crackleAmp *
                        (0.55f + 0.45f * clickTone.coerceIn(-1f, 1f))
                    crackleEnv[c] *= crackleDecay

                    if (firePop && popEnv[c] <= 0.0001f) {
                        popEnv[c] = 1f
                        popSign[c] = popMonoSign * (if (c == 0) 1f else 0.9f + 0.2f * rng.next01())
                    }
                    val pop = popSign[c] * popEnv[c] * popAmp
                    popEnv[c] *= popDecay

                    frameBuf[c] = x + rumble + dust + crackle + pop
                }

                if (stereo) {
                    val l = frameBuf[0]
                    val r = frameBuf[1]
                    frameBuf[0] = l * (1f - cross) + r * cross
                    frameBuf[1] = r * (1f - cross) + l * cross
                }

                var monoAccum = 0f
                for (c in 0 until ch) {
                    val driven = tanh(frameBuf[c] * drive)
                    val out = (driven * makeup).coerceIn(-1f, 1f)
                    writeS16(pcm, (f * ch + c) * 2, (out * 32767f).toInt(), little)
                    monoAccum += out
                }

                if (wowCap > 0) {
                    wowWrite = (wowWrite + 1) % wowCap
                    wowPhase += twoPi * wowHz / sr
                    flutterPhase += twoPi * flutterHz / sr
                    eccPhase += twoPi * eccHz / sr
                    if (wowPhase > twoPi) wowPhase -= twoPi
                    if (flutterPhase > twoPi) flutterPhase -= twoPi
                    if (eccPhase > twoPi) eccPhase -= twoPi
                }

                if (histCap > 0) {
                    histBuf[histWrite] = monoAccum / ch
                    histWrite = (histWrite + 1) % histCap
                    if (histFilled < histCap) histFilled++
                }
            }
        }
        return true
    }

    private fun readS16(pcm: ByteArray, index: Int, little: Boolean): Int {
        val b0 = pcm[index].toInt()
        val b1 = pcm[index + 1].toInt()
        val packed = if (little) {
            (b0 and 0xff) or (b1 shl 8)
        } else {
            (b1 and 0xff) or (b0 shl 8)
        }
        return packed.toShort().toInt()
    }

    private fun writeS16(pcm: ByteArray, index: Int, sample: Int, little: Boolean) {
        val s = sample.coerceIn(-32768, 32767)
        if (little) {
            pcm[index] = (s and 0xff).toByte()
            pcm[index + 1] = ((s shr 8) and 0xff).toByte()
        } else {
            pcm[index] = ((s shr 8) and 0xff).toByte()
            pcm[index + 1] = (s and 0xff).toByte()
        }
    }

    private fun ensureBuffersLocked(rate: Int) {
        val needWow = max(8, (WOW_DELAY_MS * 2 * rate) / 1000)
        val needHist = max(64, (HISTORY_MS * rate) / 1000)
        if (needWow > wowCapFrames || wowBuf.size < MAX_CHANNELS * needWow) {
            wowCapFrames = needWow
            wowBuf = FloatArray(MAX_CHANNELS * wowCapFrames)
            wowWrite = 0
        }
        if (needHist > histCapFrames || histBuf.size < needHist) {
            histCapFrames = needHist
            histBuf = FloatArray(histCapFrames)
            histWrite = 0
            histFilled = 0
        }
    }

    private fun resetStateLocked() {
        rumbleLp.fill(0f)
        dustLp1.fill(0f)
        dustLp2.fill(0f)
        ageLp.fill(0f)
        bassLp.fill(0f)
        presenceHp.fill(0f)
        lastPresenceIn.fill(0f)
        scoopLp.fill(0f)
        clickBp.fill(0f)
        crackleEnv.fill(0f)
        popEnv.fill(0f)
        crackleSign.fill(1f)
        popSign.fill(1f)
        frameBuf.fill(0f)
        envFollow = 0f
        envPrev = 0f
        dipEnv = 0f
        wowPhase = 0.0
        flutterPhase = 0.0
        eccPhase = 0.0
        wowWrite = 0
        histWrite = 0
        histFilled = 0
        skipRemaining = 0
        samplesSinceSkip = 0
        samplesSinceWear = 0
        clusterLeft = 0
        if (wowBuf.isNotEmpty()) wowBuf.fill(0f)
        if (histBuf.isNotEmpty()) histBuf.fill(0f)
    }

    private fun startSkipLocked() {
        val sr = sampleRate
        val minF = (MIN_SKIP_MS * sr) / 1000
        val maxF = (MAX_SKIP_MS * sr) / 1000
        val span = max(0, maxF - minF)
        val len = (minF + (rng.next01() * span).toInt()).coerceAtMost(histFilled)
        if (len < minF || histCapFrames <= 0) return
        val maxOffset = max(1, histFilled - len)
        val offset = 1 + (rng.next01() * maxOffset).toInt()
        skipLen = len
        skipRemaining = skipLen
        skipFadeIn = max(1, skipLen / 8)
        skipFadeOut = max(1, skipLen / 8)
        skipRead = (histWrite - offset - skipLen + histCapFrames * 4) % histCapFrames
        samplesSinceSkip = 0
    }

    private fun scheduleNextSkipLocked() {
        val sr = sampleRate.coerceAtLeast(8000)
        // Soft skips also log-dispersed: less metronomic.
        val minGap = (22.0 * sr).toInt()
        val maxGap = (70.0 * sr).toInt()
        nextSkipAt = logUniformGapLocked(minGap, maxGap)
        samplesSinceSkip = 0
    }

    /**
     * Schedule next crackle/pop. Log-uniform gaps = long quiet stretches with
     * occasional closer events (ToneArm-style dispersion, not a steady rain).
     * Clusters use short gaps for a brief burst of dust.
     */
    private fun scheduleNextWearLocked(cluster: Boolean) {
        val sr = sampleRate.coerceAtLeast(8000)
        val i = intensity
        if (cluster) {
            val minG = max(1, (0.010 * sr).toInt())
            val maxG = max(minG + 1, (0.055 * sr).toInt())
            nextWearAt = logUniformGapLocked(minG, maxG)
        } else {
            // Dispersed but present: BN ~0.9–5s, Slightly ~0.4–2.5s, Heavily ~0.18–1.1s.
            val minSec = (0.95 - 0.78 * i).coerceAtLeast(0.16)
            val maxSec = (5.2 - 4.1 * i).coerceAtLeast(minSec + 0.25)
            val minG = max(1, (minSec * sr).toInt())
            val maxG = max(minG + 1, (maxSec * sr).toInt())
            nextWearAt = logUniformGapLocked(minG, maxG)
        }
        samplesSinceWear = 0
    }

    private fun logUniformGapLocked(minGap: Int, maxGap: Int): Int {
        val lo = max(1, minGap).toDouble()
        val hi = max(lo + 1.0, maxGap.toDouble())
        val g = exp(ln(lo) + rng.next01().toDouble() * (ln(hi) - ln(lo)))
        return g.toInt().coerceIn(minGap, maxGap)
    }

    private fun floorMod(a: Int, m: Int): Int {
        if (m <= 0) return 0
        val r = a % m
        return if (r >= 0) r else r + m
    }

    private class XorShift32(seed: Long) {
        private var state = if (seed == 0L) 1 else seed.toInt()

        fun nextInt(): Int {
            var x = state
            x = x xor (x shl 13)
            x = x xor (x ushr 17)
            x = x xor (x shl 5)
            state = x
            return x
        }

        fun next01(): Float = (nextInt().toLong() and 0xFFFFFFL).toFloat() / 16777216f

        fun nextSigned(): Float = next01() * 2f - 1f
    }
}
