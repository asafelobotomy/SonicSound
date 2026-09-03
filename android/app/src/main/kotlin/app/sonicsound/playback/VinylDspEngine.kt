package app.sonicsound.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh
import java.nio.ByteOrder

/** Mutable DSP state + per-frame processing for [VinylProcessor]. */
internal class VinylDspEngine {
    companion object {
        const val MAX_CHANNELS = 8
        const val HISTORY_MS = 280
        const val WOW_DELAY_MS = 14
        const val MAX_SKIP_MS = 180
        const val MIN_SKIP_MS = 40
        const val BUFFER_RATE_HZ = 48_000
    }

    var enabled = false
    var intensity = 0.12f
    var skipsEnabled = false
    var sampleRate = 44100
    var positionMs = 0L
    var durationMs = 0L

    val rng = VinylPcmIo.XorShift32(0xC0FFEE42L)
    val nativeLittle = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN

    val rumbleLp = FloatArray(MAX_CHANNELS)
    val dustLp1 = FloatArray(MAX_CHANNELS)
    val dustLp2 = FloatArray(MAX_CHANNELS)
    val ageLp = FloatArray(MAX_CHANNELS)
    val bassLp = FloatArray(MAX_CHANNELS)
    val presenceHp = FloatArray(MAX_CHANNELS)
    val lastPresenceIn = FloatArray(MAX_CHANNELS)
    val scoopLp = FloatArray(MAX_CHANNELS)
    val clickBp = FloatArray(MAX_CHANNELS)
    val crackleEnv = FloatArray(MAX_CHANNELS)
    val crackleSign = FloatArray(MAX_CHANNELS) { 1f }
    val popEnv = FloatArray(MAX_CHANNELS)
    val popSign = FloatArray(MAX_CHANNELS) { 1f }
    val frameBuf = FloatArray(MAX_CHANNELS)

    var envFollow = 0f
    var envPrev = 0f
    var dipEnv = 0f
    var wowPhase = 0.0
    var flutterPhase = 0.0
    var eccPhase = 0.0

    var wowBuf = FloatArray(0)
    var wowWrite = 0
    var wowCapFrames = 0
    var histBuf = FloatArray(0)
    var histWrite = 0
    var histCapFrames = 0
    var histFilled = 0

    var skipRemaining = 0
    var skipRead = 0
    var skipFadeIn = 0
    var skipFadeOut = 0
    var skipLen = 0
    var samplesSinceSkip = 0
    var nextSkipAt = 0
    var samplesSinceWear = 0
    var nextWearAt = 0
    var clusterLeft = 0

    fun ensureBuffers(rate: Int) {
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

    fun resetState() {
        rumbleLp.fill(0f); dustLp1.fill(0f); dustLp2.fill(0f); ageLp.fill(0f)
        bassLp.fill(0f); presenceHp.fill(0f); lastPresenceIn.fill(0f); scoopLp.fill(0f)
        clickBp.fill(0f); crackleEnv.fill(0f); popEnv.fill(0f)
        crackleSign.fill(1f); popSign.fill(1f); frameBuf.fill(0f)
        envFollow = 0f; envPrev = 0f; dipEnv = 0f
        wowPhase = 0.0; flutterPhase = 0.0; eccPhase = 0.0
        wowWrite = 0; histWrite = 0; histFilled = 0
        skipRemaining = 0; samplesSinceSkip = 0; samplesSinceWear = 0; clusterLeft = 0
        if (wowBuf.isNotEmpty()) wowBuf.fill(0f)
        if (histBuf.isNotEmpty()) histBuf.fill(0f)
    }

    fun startSkip() {
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

    fun scheduleNextSkip() {
        val sr = sampleRate.coerceAtLeast(8000)
        nextSkipAt = VinylPcmIo.logUniformGap(rng, (22.0 * sr).toInt(), (70.0 * sr).toInt())
        samplesSinceSkip = 0
    }

    fun scheduleNextWear(cluster: Boolean) {
        val sr = sampleRate.coerceAtLeast(8000)
        val inten = intensity
        if (cluster) {
            val minG = max(1, (0.010 * sr).toInt())
            val maxG = max(minG + 1, (0.055 * sr).toInt())
            nextWearAt = VinylPcmIo.logUniformGap(rng, minG, maxG)
        } else {
            val minSec = (0.95 - 0.78 * inten).coerceAtLeast(0.16)
            val maxSec = (5.2 - 4.1 * inten).coerceAtLeast(minSec + 0.25)
            val minG = max(1, (minSec * sr).toInt())
            val maxG = max(minG + 1, (maxSec * sr).toInt())
            nextWearAt = VinylPcmIo.logUniformGap(rng, minG, maxG)
        }
        samplesSinceWear = 0
    }

    fun processFrames(pcm: ByteArray, fr: Int, ch: Int, rate: Int) {
        if (rate >= 8000) {
            sampleRate = rate
            if (wowCapFrames < (WOW_DELAY_MS * 2 * rate) / 1000 ||
                histCapFrames < (HISTORY_MS * rate) / 1000
            ) {
                ensureBuffers(rate)
            }
        }
        val c = VinylFrameCoeffs.compute(intensity, sampleRate, positionMs, durationMs)
        val little = nativeLittle
        val wowCap = wowCapFrames
        val histCap = histCapFrames
        val stereo = ch >= 2
        val i = intensity
        var allowSkip = skipsEnabled && histFilled > (MIN_SKIP_MS * sampleRate / 1000)
        if (allowSkip) {
            if (positionMs < 2000L) allowSkip = false
            if (durationMs > 0L && positionMs > durationMs - 3000L) allowSkip = false
        }
        for (f in 0 until fr) {
            processFrame(pcm, f, ch, c, little, wowCap, histCap, stereo, i, allowSkip)
        }
    }

    private fun processFrame(
        pcm: ByteArray,
        f: Int,
        ch: Int,
        c: VinylFrameCoeffs,
        little: Boolean,
        wowCap: Int,
        histCap: Int,
        stereo: Boolean,
        i: Float,
        allowSkip: Boolean,
    ) {
        var skipGain = 0f
        var histSample = 0f
        if (skipsEnabled) {
            samplesSinceSkip++
            if (skipRemaining <= 0 && allowSkip && samplesSinceSkip >= nextSkipAt) {
                startSkip()
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
                if (skipRemaining <= 0) scheduleNextSkip()
            }
        }

        var frameAbs = 0f
        for (ci in 0 until ch) {
            val dry0 = VinylPcmIo.readS16(pcm, (f * ch + ci) * 2, little) / 32768f
            frameAbs += if (dry0 >= 0f) dry0 else -dry0
        }
        frameAbs /= ch
        val envCoef = if (frameAbs > envFollow) c.envAtk else c.envRel
        envFollow += envCoef * (frameAbs - envFollow)
        val onset = (envFollow - envPrev).coerceAtLeast(0f)
        envPrev = envFollow
        val groove = (c.dustFloor + c.dustFollow * envFollow.coerceIn(0f, 1f)).coerceIn(0.15f, 1.1f)
        val dynGain = 1f / (1f + c.dynAmt * envFollow.coerceIn(0f, 1.2f))

        var fireCrackle = false
        var firePop = false
        var crackleMonoSign = 1f
        var popMonoSign = 1f
        samplesSinceWear++
        if (samplesSinceWear >= nextWearAt) {
            val wantPop = rng.next01() < (0.18f + 0.22f * i)
            if (wantPop) {
                firePop = true
                popMonoSign = if (rng.next01() < 0.5f) -1f else 1f
                dipEnv = max(dipEnv, c.dipDepth * (0.85f + 0.35f * rng.next01()))
            } else {
                fireCrackle = true
                crackleMonoSign = if (rng.next01() < 0.5f) -1f else 1f
                dipEnv = max(dipEnv, c.dipDepth * 0.35f * (0.7f + 0.5f * rng.next01()))
            }
            val clusterChance = 0.18f + 0.35f * i + 0.30f * onset.coerceIn(0f, 0.2f) * 5f
            if (clusterLeft <= 0 && rng.next01() < clusterChance) {
                clusterLeft = 1 + (rng.next01() * (2f + 3f * i)).toInt()
            }
            if (clusterLeft > 0) {
                clusterLeft--
                scheduleNextWear(cluster = true)
            } else {
                scheduleNextWear(cluster = false)
            }
        }
        dipEnv *= c.dipDecay

        val mod = 1.0 +
            c.wowDepth * sin(wowPhase) +
            c.flutterDepth * sin(flutterPhase) +
            c.eccDepth * sin(eccPhase)
        for (ci in 0 until ch) {
            val dry = VinylPcmIo.readS16(pcm, (f * ch + ci) * 2, little) / 32768f
            var x = if (skipGain > 0f) dry * (1f - skipGain) + histSample * skipGain else dry
            if (wowCap > 1) {
                val delayFrames = (c.delayBase * mod.toFloat()).coerceIn(1f, (wowCap - 2).toFloat())
                val readPos = wowWrite - delayFrames
                val i0 = VinylPcmIo.floorMod(floor(readPos.toDouble()).toInt(), wowCap)
                val i1 = (i0 + 1) % wowCap
                val frac = (readPos - floor(readPos)).coerceIn(0f, 1f)
                val base = ci * wowCap
                val delayed = wowBuf[base + i0] * (1f - frac) + wowBuf[base + i1] * frac
                wowBuf[base + wowWrite] = x
                x = delayed
            }
            frameBuf[ci] = x
        }

        if (stereo) {
            var monoBass = 0f
            for (ci in 0 until min(ch, 2)) {
                bassLp[ci] += c.bassA * (frameBuf[ci] - bassLp[ci])
                monoBass += bassLp[ci]
            }
            monoBass *= 0.5f
            for (ci in 0 until min(ch, 2)) {
                val hf = frameBuf[ci] - bassLp[ci]
                frameBuf[ci] = hf + bassLp[ci] * (1f - c.monoBassMix) + monoBass * c.monoBassMix
            }
        }

        val musicDip = (1f - dipEnv).coerceIn(0.82f, 1f)
        for (ci in 0 until ch) {
            var x = frameBuf[ci] * dynGain * musicDip
            scoopLp[ci] += c.scoopA * (x - scoopLp[ci])
            x = x - c.scoopAmt * (x - scoopLp[ci])
            presenceHp[ci] = c.presenceA * (presenceHp[ci] + x - lastPresenceIn[ci])
            lastPresenceIn[ci] = x
            x += presenceHp[ci] * c.presenceGain
            ageLp[ci] = (1f - c.ageA) * x + c.ageA * ageLp[ci]
            x = (1f - c.ageBlend) * x + c.ageBlend * ageLp[ci]

            val white = rng.nextSigned()
            rumbleLp[ci] += c.rumbleA * (white - rumbleLp[ci])
            val rumble = rumbleLp[ci] * c.rumbleGain * groove
            dustLp1[ci] += c.dustA * (white - dustLp1[ci])
            dustLp2[ci] += c.dustA * (dustLp1[ci] - dustLp2[ci])
            val dust = dustLp2[ci] * c.dustGain * groove
            clickBp[ci] += c.clickA * (white - clickBp[ci])
            val clickTone = white - clickBp[ci]

            if (fireCrackle && crackleEnv[ci] <= 0.0001f) {
                crackleEnv[ci] = 1f
                crackleSign[ci] = crackleMonoSign * (if (ci == 0) 1f else 0.85f + 0.3f * rng.next01())
            }
            val crackle = crackleSign[ci] * crackleEnv[ci] * c.crackleAmp *
                (0.55f + 0.45f * clickTone.coerceIn(-1f, 1f))
            crackleEnv[ci] *= c.crackleDecay
            if (firePop && popEnv[ci] <= 0.0001f) {
                popEnv[ci] = 1f
                popSign[ci] = popMonoSign * (if (ci == 0) 1f else 0.9f + 0.2f * rng.next01())
            }
            val pop = popSign[ci] * popEnv[ci] * c.popAmp
            popEnv[ci] *= c.popDecay
            frameBuf[ci] = x + rumble + dust + crackle + pop
        }

        if (stereo) {
            val l = frameBuf[0]
            val r = frameBuf[1]
            frameBuf[0] = l * (1f - c.cross) + r * c.cross
            frameBuf[1] = r * (1f - c.cross) + l * c.cross
        }

        var monoAccum = 0f
        for (ci in 0 until ch) {
            val driven = tanh(frameBuf[ci] * c.drive)
            val out = (driven * c.makeup).coerceIn(-1f, 1f)
            VinylPcmIo.writeS16(pcm, (f * ch + ci) * 2, (out * 32767f).toInt(), little)
            monoAccum += out
        }

        if (wowCap > 0) {
            wowWrite = (wowWrite + 1) % wowCap
            wowPhase += c.twoPi * c.wowHz / c.sr
            flutterPhase += c.twoPi * c.flutterHz / c.sr
            eccPhase += c.twoPi * c.eccHz / c.sr
            if (wowPhase > c.twoPi) wowPhase -= c.twoPi
            if (flutterPhase > c.twoPi) flutterPhase -= c.twoPi
            if (eccPhase > c.twoPi) eccPhase -= c.twoPi
        }
        if (histCap > 0) {
            histBuf[histWrite] = monoAccum / ch
            histWrite = (histWrite + 1) % histCap
            if (histFilled < histCap) histFilled++
        }
    }
}
