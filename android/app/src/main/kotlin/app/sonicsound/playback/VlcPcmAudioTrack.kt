package app.sonicsound.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import java.nio.ByteOrder

/** AudioTrack open / downmix helpers for [VlcPcmOutput]. */
internal object VlcPcmAudioTrack {
    private const val TAG = "VlcPcmAudioTrack"

    data class OpenResult(
        val track: AudioTrack,
        val outputChannels: Int,
    )

    fun channelMaskCandidates(ch: Int): List<Int> {
        val primary = when (ch) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            4 -> AudioFormat.CHANNEL_OUT_QUAD
            6 -> AudioFormat.CHANNEL_OUT_5POINT1
            8 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
            } else {
                AudioFormat.CHANNEL_OUT_5POINT1
            }
            else -> if (ch > 2) AudioFormat.CHANNEL_OUT_5POINT1 else AudioFormat.CHANNEL_OUT_STEREO
        }
        return listOf(
            primary,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.CHANNEL_OUT_MONO,
        ).distinct()
    }

    fun open(
        sampleRate: Int,
        channels: Int,
        applyGain: (AudioTrack) -> Unit,
        latencyMs: (AudioTrack, Int) -> Int,
    ): OpenResult? {
        val rate = sampleRate.coerceAtLeast(8000)
        val ch = channels.coerceIn(1, 8)
        val masks = channelMaskCandidates(ch)
        var lastError: Exception? = null
        for (channelMask in masks) {
            val minBuf = AudioTrack.getMinBufferSize(
                rate,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuf <= 0) continue
            val bufSize = minBuf * 2
            try {
                val builder = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(rate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(channelMask)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufSize)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                }
                val t = builder.build()
                t.play()
                applyGain(t)
                val outCh = when (channelMask) {
                    AudioFormat.CHANNEL_OUT_MONO -> 1
                    AudioFormat.CHANNEL_OUT_STEREO -> 2
                    AudioFormat.CHANNEL_OUT_QUAD -> 4
                    AudioFormat.CHANNEL_OUT_5POINT1 -> 6
                    else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        channelMask == AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
                    ) {
                        8
                    } else {
                        ch
                    }
                }
                Log.i(
                    TAG,
                    "AudioTrack open ${rate}Hz mask=0x${channelMask.toString(16)} " +
                        "inCh=$ch outCh=$outCh buf=${bufSize}B latency~${latencyMs(t, rate)}ms",
                )
                return OpenResult(t, outCh)
            } catch (e: Exception) {
                lastError = e
            }
        }
        Log.e(TAG, "AudioTrack setup failed for $rate Hz / $ch ch", lastError)
        return null
    }

    fun downmixToOutput(
        scratch: ByteArray,
        pcm: ByteArray,
        bytes: Int,
        inCh: Int,
        frames: Int,
        outCh: Int,
    ): Triple<ByteArray, Int, ByteArray> {
        val inChannels = inCh.coerceIn(1, 8)
        val outChannels = when (outCh) {
            1 -> 1
            else -> 2
        }
        val frameCount = (bytes / (2 * inChannels)).coerceAtMost(frames).coerceAtLeast(0)
        val need = frameCount * outChannels * 2
        var outScratch = scratch
        if (outScratch.size < need) {
            outScratch = ByteArray(need.coerceAtLeast(4096))
        }
        val out = outScratch
        val little = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
        fun read(i: Int): Int {
            if (i + 1 >= bytes) return 0
            return if (little) {
                ((pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)).toShort().toInt()
            } else {
                ((pcm[i + 1].toInt() and 0xFF) or (pcm[i].toInt() shl 8)).toShort().toInt()
            }
        }
        fun write(o: Int, sample: Int) {
            val s = sample.coerceIn(-32768, 32767)
            if (little) {
                out[o] = (s and 0xFF).toByte()
                out[o + 1] = ((s shr 8) and 0xFF).toByte()
            } else {
                out[o] = ((s shr 8) and 0xFF).toByte()
                out[o + 1] = (s and 0xFF).toByte()
            }
        }
        for (f in 0 until frameCount) {
            val base = f * inChannels * 2
            val c0 = read(base).toFloat()
            val c1 = if (inChannels > 1) read(base + 2).toFloat() else c0
            val c2 = if (inChannels > 2) read(base + 4).toFloat() else 0f
            val c3 = if (inChannels > 3) read(base + 6).toFloat() else 0f
            val c4 = if (inChannels > 4) read(base + 8).toFloat() else 0f
            val c5 = if (inChannels > 5) read(base + 10).toFloat() else 0f
            val c6 = if (inChannels > 6) read(base + 12).toFloat() else 0f
            val c7 = if (inChannels > 7) read(base + 14).toFloat() else 0f
            if (outChannels == 1) {
                val m = when {
                    inChannels >= 6 -> (c0 + c1 + c2 * 0.7f + c4 * 0.5f + c5 * 0.5f) / 3.4f
                    inChannels >= 2 -> (c0 + c1) * 0.5f
                    else -> c0
                }
                write(f * 2, m.toInt())
            } else {
                val l = when {
                    inChannels >= 8 -> c0 + c2 * 0.7f + c4 * 0.5f + c6 * 0.5f + c3 * 0.15f
                    inChannels >= 6 -> c0 + c2 * 0.7f + c4 * 0.5f + c3 * 0.15f
                    inChannels == 4 -> c0 + c2 * 0.5f
                    else -> c0
                }
                val r = when {
                    inChannels >= 8 -> c1 + c2 * 0.7f + c5 * 0.5f + c7 * 0.5f + c3 * 0.15f
                    inChannels >= 6 -> c1 + c2 * 0.7f + c5 * 0.5f + c3 * 0.15f
                    inChannels == 4 -> c1 + c3 * 0.5f
                    inChannels >= 2 -> c1
                    else -> c0
                }
                write(f * 4, l.toInt())
                write(f * 4 + 2, r.toInt())
            }
        }
        return Triple(out, need, outScratch)
    }

    fun estimateLatencyMs(track: AudioTrack, sampleRate: Int): Int {
        val rate = sampleRate.coerceAtLeast(1)
        val fromApi = runCatching {
            val m = AudioTrack::class.java.methods.firstOrNull {
                it.name == "getLatency" && it.parameterCount == 0
            }
            (m?.invoke(track) as? Int) ?: 0
        }.getOrDefault(0).coerceAtLeast(0)
        val bufMs = ((track.bufferSizeInFrames.toLong() * 1000L) / rate).toInt()
        return when {
            fromApi > 0 -> fromApi.coerceIn(0, 500)
            bufMs > 0 -> (bufMs / 2).coerceIn(0, 500)
            else -> 0
        }
    }
}
