package app.sonicsound.playback

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/** S16 PCM helpers and PRNG for [VinylProcessor]. */
internal object VinylPcmIo {
    fun readS16(pcm: ByteArray, index: Int, little: Boolean): Int {
        val b0 = pcm[index].toInt()
        val b1 = pcm[index + 1].toInt()
        val packed = if (little) {
            (b0 and 0xff) or (b1 shl 8)
        } else {
            (b1 and 0xff) or (b0 shl 8)
        }
        return packed.toShort().toInt()
    }

    fun writeS16(pcm: ByteArray, index: Int, sample: Int, little: Boolean) {
        val s = sample.coerceIn(-32768, 32767)
        if (little) {
            pcm[index] = (s and 0xff).toByte()
            pcm[index + 1] = ((s shr 8) and 0xff).toByte()
        } else {
            pcm[index] = ((s shr 8) and 0xff).toByte()
            pcm[index + 1] = (s and 0xff).toByte()
        }
    }

    fun floorMod(a: Int, m: Int): Int {
        if (m <= 0) return 0
        val r = a % m
        return if (r >= 0) r else r + m
    }

    fun logUniformGap(rng: XorShift32, minGap: Int, maxGap: Int): Int {
        val lo = max(1, minGap).toDouble()
        val hi = max(lo + 1.0, maxGap.toDouble())
        val g = exp(ln(lo) + rng.next01().toDouble() * (ln(hi) - ln(lo)))
        return g.toInt().coerceIn(minGap, maxGap)
    }

    class XorShift32(seed: Long) {
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
