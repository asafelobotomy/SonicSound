package app.sonicsound.visualizer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Decodes the first few seconds of the upcoming track off the playback path
 * and caches a [TrackCharacter.Snapshot] so viz adapts in ~1s instead of 10–20s.
 */
object TrackCharacterPrefetch {
    private const val TAG = "TrackCharPrefetch"
    private const val ANALYZE_MS = 2_800L
    private const val FFT_SIZE = 512
    private const val BANDS = 32

    private val cache = ConcurrentHashMap<String, TrackCharacter.Snapshot>()
    private val pendingSongId = AtomicReference<String?>(null)
    private val mutex = Mutex()
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun peek(songId: String): TrackCharacter.Snapshot? = cache[songId]

    fun take(songId: String): TrackCharacter.Snapshot? = cache[songId]

    fun remember(songId: String, snapshot: TrackCharacter.Snapshot) {
        if (songId.isBlank()) return
        cache[songId] = snapshot
        // Bound cache size.
        if (cache.size > 24) {
            val drop = cache.keys.take(8)
            drop.forEach { cache.remove(it) }
        }
    }

    /** Song that will soft-reset next (current track being prepared). */
    fun setUpcomingPlayback(songId: String?) {
        pendingSongId.set(songId)
    }

    fun consumeForPlayback(songId: String): TrackCharacter.Snapshot? {
        setUpcomingPlayback(null)
        return take(songId)
    }

    fun prefetch(context: Context, songId: String, uri: String, headers: Map<String, String> = emptyMap()) {
        if (songId.isBlank() || uri.isBlank()) return
        if (cache.containsKey(songId)) return
        job?.cancel()
        job = scope.launch {
            mutex.withLock {
                if (cache.containsKey(songId)) return@withLock
                runCatching {
                    val snap = analyzeUri(context, uri, headers)
                    if (snap != null) {
                        cache[songId] = snap
                        Log.i(
                            TAG,
                            "prefetch $songId bpm=${"%.0f".format(snap.bpm)} " +
                                "int=${"%.2f".format(snap.intensity)} " +
                                "dr=${"%.2f".format(snap.dynamicRange)}",
                        )
                    }
                }.onFailure { Log.w(TAG, "prefetch failed for $songId", it) }
            }
        }
    }

    private fun analyzeUri(
        context: Context,
        uri: String,
        headers: Map<String, String>,
    ): TrackCharacter.Snapshot? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            if (uri.startsWith("file://") || uri.startsWith("/")) {
                val path = if (uri.startsWith("file://")) uri.removePrefix("file://") else uri
                extractor.setDataSource(path)
            } else {
                extractor.setDataSource(context, Uri.parse(uri), headers.ifEmpty { null })
            }
            val track = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val character = TrackCharacter()
            val bands = FloatArray(BANDS)
            val window = FloatArray(FFT_SIZE)
            val re = FloatArray(FFT_SIZE)
            val im = FloatArray(FFT_SIZE)
            val hann = FloatArray(FFT_SIZE) { i ->
                (0.5f * (1.0 - cos(2.0 * PI * i / (FFT_SIZE - 1)))).toFloat()
            }
            var write = 0
            var samples = 0
            val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8_000)
            val maxSamples = ((ANALYZE_MS * rate) / 1000L).toInt()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val deadline = SystemClock.uptimeMillis() + ANALYZE_MS + 4_000L

            while (!outputDone && samples < maxSamples && SystemClock.uptimeMillis() < deadline) {
                if (!inputDone) {
                    val inIx = codec.dequeueInputBuffer(4_000)
                    if (inIx >= 0) {
                        val buf = codec.getInputBuffer(inIx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIx = codec.dequeueOutputBuffer(info, 4_000)
                when {
                    outIx >= 0 -> {
                        val out = codec.getOutputBuffer(outIx)
                        if (out != null && info.size > 0 &&
                            info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            val pcm = extractMono(out, info.offset, info.size, codec.outputFormat)
                            for (s in pcm) {
                                window[write] = s
                                write = (write + 1) % FFT_SIZE
                                samples++
                                if (samples % (FFT_SIZE / 4) == 0) {
                                    for (i in 0 until FFT_SIZE) {
                                        val idx = (write + i) % FFT_SIZE
                                        re[i] = window[idx] * hann[i]
                                        im[i] = 0f
                                    }
                                    fftRadix2(re, im)
                                    fillBands(re, im, bands)
                                    character.observe(bands, SystemClock.elapsedRealtime())
                                }
                                if (samples >= maxSamples) break
                            }
                        }
                        codec.releaseOutputBuffer(outIx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            val snap = character.capture().copy(confidence = 0.75f)
            if (samples < rate / 4) null else snap
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun extractMono(
        buffer: ByteBuffer,
        offset: Int,
        size: Int,
        format: MediaFormat,
    ): FloatArray {
        val ch = try {
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } catch (_: Exception) {
            2
        }.coerceAtLeast(1)
        // Prefer PCM 16-bit; fall back treating as 16-bit LE.
        val samples = size / 2
        val frames = samples / ch
        if (frames <= 0) return FloatArray(0)
        val out = FloatArray(frames)
        val order = buffer.order()
        var i = offset
        for (f in 0 until frames) {
            var acc = 0
            for (c in 0 until ch) {
                if (i + 1 >= offset + size) break
                val lo = buffer.get(i).toInt() and 0xFF
                val hi = buffer.get(i + 1).toInt()
                val s = if (order == ByteOrder.LITTLE_ENDIAN) {
                    ((hi shl 8) or lo).toShort()
                } else {
                    ((lo shl 8) or (hi and 0xFF)).toShort()
                }
                // Front L/R matter most; still include others lightly.
                val w = when {
                    ch == 1 -> 1f
                    c < 2 -> 1f
                    c == 3 && ch >= 6 -> 0.35f // LFE-ish
                    else -> 0.55f
                }
                acc += (s * w).toInt()
                i += 2
            }
            out[f] = (acc / (ch * 32768f)).coerceIn(-1f, 1f)
        }
        return out
    }

    private fun fillBands(re: FloatArray, im: FloatArray, bands: FloatArray) {
        val usable = FFT_SIZE / 2
        val per = usable / bands.size
        for (b in bands.indices) {
            var peak = 0f
            val start = 1 + b * per
            val end = min(start + per, usable)
            for (k in start until end) {
                val mag = sqrt(re[k] * re[k] + im[k] * im[k])
                if (mag > peak) peak = mag
            }
            bands[b] = peak
        }
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
