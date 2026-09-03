package app.sonicsound.playback

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max

/** Per-block filter / modulation coefficients for vinyl DSP. */
internal data class VinylFrameCoeffs(
    val color: Float,
    val sr: Float,
    val twoPi: Double,
    val wowHz: Double,
    val flutterHz: Double,
    val eccHz: Double,
    val wowDepth: Double,
    val flutterDepth: Double,
    val eccDepth: Double,
    val rumbleA: Float,
    val dustA: Float,
    val ageA: Float,
    val ageBlend: Float,
    val presenceA: Float,
    val presenceGain: Float,
    val scoopA: Float,
    val scoopAmt: Float,
    val bassA: Float,
    val monoBassMix: Float,
    val cross: Float,
    val clickA: Float,
    val crackleAmp: Float,
    val popAmp: Float,
    val crackleDecay: Float,
    val popDecay: Float,
    val dipDecay: Float,
    val rumbleGain: Float,
    val dustFloor: Float,
    val dustFollow: Float,
    val dustGain: Float,
    val drive: Float,
    val dynAmt: Float,
    val makeup: Float,
    val dipDepth: Float,
    val envAtk: Float,
    val envRel: Float,
    val delayBase: Float,
) {
    companion object {
        fun compute(intensity: Float, sampleRate: Int, positionMs: Long, durationMs: Long): VinylFrameCoeffs {
            val i = intensity
            val color = (0.42f + 0.58f * i).coerceIn(0f, 1f)
            val sr = sampleRate.toFloat().coerceAtLeast(8000f)
            val twoPi = 2.0 * PI

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

            val sideProg = if (durationMs > 4_000L) {
                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            val inner = sideProg * sideProg

            val ageCut = (15_000f - 6_500f * color - 2_800f * inner * color).coerceAtLeast(4500f)
            val ageA = exp((-twoPi * ageCut / sr).toFloat())
            val ageBlend = (0.18f + 0.38f * color + 0.12f * inner * color).coerceIn(0.16f, 0.68f)

            val presenceCut = 3100f
            val presenceA = exp((-twoPi * presenceCut / sr).toFloat())
            val presenceGain = 0.12f + 0.18f * color

            val scoopCut = 800f
            val scoopA = 1f - exp((-twoPi * scoopCut / sr).toFloat())
            val scoopAmt = 0.05f + 0.10f * color

            val bassCut = 125f + 45f * color
            val bassA = 1f - exp((-twoPi * bassCut / sr).toFloat())
            val monoBassMix = (0.55f + 0.35f * color).coerceIn(0f, 1f)
            val cross = 0.035f + 0.080f * color

            val clickCut = 2500f
            val clickA = 1f - exp((-twoPi * clickCut / sr).toFloat())

            val crackleAmp = 0.010f + 0.055f * i
            val popAmp = 0.016f + 0.090f * i
            val crackleTau = 0.00030f + 0.00012f * (1f - i)
            val popTau = 0.0022f + 0.0075f * i
            val crackleDecay = exp(-1f / max(1f, crackleTau * sr))
            val popDecay = exp(-1f / max(1f, popTau * sr))
            val dipDecay = exp(-1f / max(1f, 0.014f * sr))

            val rumbleGain = 0.0012f + 0.012f * i
            val dustFloor = 0.35f
            val dustFollow = 0.65f
            val dustGain = 0.0018f + 0.014f * i

            val drive = 1.06f + 0.38f * color
            val dynAmt = 0.06f + 0.16f * color
            val makeup = 1.06f + 0.05f * color
            val dipDepth = 0.06f + 0.12f * i

            val envAtk = 1f - exp((-twoPi * 90f / sr).toFloat())
            val envRel = 1f - exp((-twoPi * 8f / sr).toFloat())
            val delayBase = (14 * 0.001f * sr).coerceAtLeast(2f)

            return VinylFrameCoeffs(
                color = color,
                sr = sr,
                twoPi = twoPi,
                wowHz = wowHz,
                flutterHz = flutterHz,
                eccHz = eccHz,
                wowDepth = wowDepth,
                flutterDepth = flutterDepth,
                eccDepth = eccDepth,
                rumbleA = rumbleA,
                dustA = dustA,
                ageA = ageA,
                ageBlend = ageBlend,
                presenceA = presenceA,
                presenceGain = presenceGain,
                scoopA = scoopA,
                scoopAmt = scoopAmt,
                bassA = bassA,
                monoBassMix = monoBassMix,
                cross = cross,
                clickA = clickA,
                crackleAmp = crackleAmp,
                popAmp = popAmp,
                crackleDecay = crackleDecay,
                popDecay = popDecay,
                dipDecay = dipDecay,
                rumbleGain = rumbleGain,
                dustFloor = dustFloor,
                dustFollow = dustFollow,
                dustGain = dustGain,
                drive = drive,
                dynAmt = dynAmt,
                makeup = makeup,
                dipDepth = dipDepth,
                envAtk = envAtk,
                envRel = envRel,
                delayBase = delayBase,
            )
        }
    }
}
