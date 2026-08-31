package app.sonicsound.playback

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import app.sonicsound.visualizer.PlaybackSpectrum
import org.videolan.libvlc.MediaPlayer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * LibVLC decoded-PCM audio output via [libvlc_audio_set_callbacks].
 *
 * Lifecycle hazards this object defends against (historically killed visualizations):
 * - Track skip → flush without resume
 * - Album / media replace → cleanup with no matching setup
 * - Settings / ReplayGain → full [VlcEngine] recreate + detach
 * - Stale cleanup after a newer setup (aout depth)
 * - Re-attach mid-stream clearing format callbacks
 *
 * Strategy: soft spectrum resets across gaps, keep/reuse AudioTrack during media-swap
 * grace, heal a missing track on play / Playing, never hard-wipe spectrum except on
 * true teardown ([detach] with wipeSpectrum=true).
 */
object VlcPcmOutput {
    private const val TAG = "VlcPcmOutput"
    private const val MEDIA_SWAP_GRACE_MS = 2_500L
    private const val ENGINE_RECREATE_GRACE_MS = 5_000L

    @Volatile
    private var ready = false

    @Volatile
    private var track: AudioTrack? = null

    @Volatile
    private var channels = 2

    /** Channel count the AudioTrack was opened with (may be less than LibVLC channels). */
    @Volatile
    private var outputChannels = 2

    /** Reused stereo/mono downmix scratch — avoids allocating on the audio callback. */
    private var downmixScratch = ByteArray(0)

    @Volatile
    private var sampleRate = 44100

    @Volatile
    private var volumeLinear = 1f

    @Volatile
    private var muted = false

    @Volatile
    private var playCallbacks = 0

    @Volatile
    private var lastPcmUptimeMs = 0L

    /** Nested setup/cleanup depth — ignores stale cleanup after a newer setup. */
    private var aoutDepth = 0

    /** While swapping media / recreating the engine, keep AudioTrack when possible. */
    @Volatile
    private var mediaSwapUntilMs = 0L

    private val trackLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        try {
            runCatching { System.loadLibrary("c++_shared") }
            System.loadLibrary("vlc")
            runCatching { System.loadLibrary("vlcjni") }
            System.loadLibrary("vlc_pcm_tap")
            ready = nativeInit()
            if (!ready) Log.w(TAG, "nativeInit failed — falling back to LibVLC aout")
            else Log.i(TAG, "PCM tap ready")
        } catch (e: Throwable) {
            Log.w(TAG, "vlc_pcm_tap unavailable", e)
            ready = false
        }
    }

    val isAvailable: Boolean get() = ready

    /** True if PCM play callbacks arrived recently (visualizer feed is alive). */
    fun isPcmFresh(maxAgeMs: Long = 1_500L): Boolean {
        val last = lastPcmUptimeMs
        if (last == 0L) return false
        return SystemClock.uptimeMillis() - last <= maxAgeMs
    }

    /** Call before replacing media so cleanup does not drop the live tap. */
    fun noteMediaSwap(graceMs: Long = MEDIA_SWAP_GRACE_MS) {
        mediaSwapUntilMs = SystemClock.uptimeMillis() + graceMs
    }

    /** Longer grace used around [VlcEngine] recreate (Settings / ReplayGain). */
    fun noteEngineRecreate() {
        noteMediaSwap(ENGINE_RECREATE_GRACE_MS)
    }

    fun attach(player: MediaPlayer): Boolean {
        if (!ready) return false
        val ptr = playerNativePtr(player)
        if (ptr == 0L) {
            Log.w(TAG, "MediaPlayer native instance is 0")
            return false
        }
        val ok = nativeAttach(ptr)
        Log.i(TAG, if (ok) "PCM tap attached ptr=0x${ptr.toString(16)}" else "PCM tap attach failed")
        return ok
    }

    /**
     * @param wipeSpectrum hard-clear FFT state. Use true only when the player is going
     * away for good (service destroy). Hot recreates should soft-reset so the UI
     * recovers as soon as PCM resumes.
     */
    fun detach(player: MediaPlayer?, wipeSpectrum: Boolean = true) {
        if (!ready) return
        nativeDetach(playerNativePtr(player))
        synchronized(trackLock) {
            aoutDepth = 0
            // Keep swap grace if a recreate is in flight.
            if (wipeSpectrum) mediaSwapUntilMs = 0L
            releaseTrackLocked()
        }
        if (wipeSpectrum) {
            PlaybackSpectrum.reset()
        } else {
            PlaybackSpectrum.softReset()
        }
        playCallbacks = 0
        Log.i(TAG, "PCM detach wipeSpectrum=$wipeSpectrum")
    }

    /** Active AudioTrack output device when available (API 23+). */
    fun routedOutputDevice(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return synchronized(trackLock) {
            runCatching { track?.routedDevice }.getOrNull()
        }
    }

    /**
     * Ensure AudioTrack is live when LibVLC reports Playing.
     * Optionally re-attaches if PCM stays silent (Settings recreate race).
     */
    fun onEnginePlaying(player: MediaPlayer? = null) {
        synchronized(trackLock) {
            val t = track
            if (t == null) {
                if (openTrackLocked(sampleRate, channels)) {
                    Log.w(TAG, "onEnginePlaying healed null AudioTrack")
                }
            } else if (t.playState != AudioTrack.PLAYSTATE_PLAYING) {
                runCatching { t.play() }
            }
        }
        if (player == null || !ready) return
        val baseline = playCallbacks
        val baselineTime = lastPcmUptimeMs
        mainHandler.removeCallbacks(pcmHealRunnable)
        pcmHealPlayer = player
        pcmHealBaseline = baseline
        pcmHealBaselineTime = baselineTime
        mainHandler.postDelayed(pcmHealRunnable, 900L)
    }

    private var pcmHealPlayer: MediaPlayer? = null
    private var pcmHealBaseline = 0
    private var pcmHealBaselineTime = 0L
    private val pcmHealRunnable = Runnable {
        val player = pcmHealPlayer ?: return@Runnable
        if (isPcmFresh()) return@Runnable
        if (playCallbacks != pcmHealBaseline && lastPcmUptimeMs != pcmHealBaselineTime) return@Runnable
        Log.w(TAG, "PCM silent after Playing — re-attaching tap")
        noteMediaSwap()
        attach(player)
        synchronized(trackLock) {
            if (track == null) openTrackLocked(sampleRate, channels)
            else runCatching { track?.play() }
        }
    }

    private fun playerNativePtr(player: MediaPlayer?): Long {
        if (player == null) return 0L
        return runCatching {
            val m = player.javaClass.methods.firstOrNull { it.name == "getInstance" && it.parameterCount == 0 }
                ?: player.javaClass.superclass?.methods?.firstOrNull {
                    it.name == "getInstance" && it.parameterCount == 0
                }
            (m?.invoke(player) as? Long) ?: 0L
        }.getOrElse {
            Log.w(TAG, "getInstance reflection failed", it)
            0L
        }
    }

    @JvmStatic
    fun nativeSetup(rate: Int, ch: Int): Boolean {
        synchronized(trackLock) {
            val wantRate = rate.coerceAtLeast(8000)
            val wantCh = ch.coerceIn(1, 8)
            playCallbacks = 0
            aoutDepth++
            val existing = track
            if (existing != null && sampleRate == wantRate && channels == wantCh) {
                sampleRate = wantRate
                channels = wantCh
                runCatching {
                    existing.pause()
                    existing.flush()
                    existing.play()
                }
                Log.i(TAG, "AudioTrack reused $wantRate Hz / $wantCh ch depth=$aoutDepth")
                return true
            }
            sampleRate = wantRate
            channels = wantCh
            val ok = openTrackLocked(sampleRate, channels)
            Log.i(TAG, "AudioTrack setup $sampleRate Hz / $channels ch ok=$ok depth=$aoutDepth")
            if (!ok && aoutDepth > 0) aoutDepth--
            return ok
        }
    }

    @JvmStatic
    fun nativePlay(pcm: ByteArray, bytes: Int, ch: Int, frames: Int) {
        val len = min(bytes, pcm.size)
        if (len <= 0) return
        val playCh = ch.coerceAtLeast(1)
        val playFrames = frames.coerceAtLeast(1)
        val rate = sampleRate

        // Keep AudioTrack.write off the spectrum lock — FFT runs on analyzer thread.
        synchronized(trackLock) {
            var t = track
            if (t == null) {
                Log.w(TAG, "PCM play with null AudioTrack — healing ${sampleRate}Hz/${playCh}ch")
                if (!openTrackLocked(sampleRate.coerceAtLeast(8000), playCh.coerceIn(1, 8))) {
                    lastPcmUptimeMs = SystemClock.uptimeMillis()
                    // Fall through to spectrum ingest below.
                    t = null
                } else {
                    t = track
                }
            }
            if (t != null) {
                if (t.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    runCatching { t.play() }
                }
                val writePcm: ByteArray
                val writeLen: Int
                if (playCh == outputChannels || outputChannels < 1) {
                    writePcm = pcm
                    writeLen = len
                } else {
                    val mixed = downmixToOutput(pcm, len, playCh, playFrames, outputChannels)
                    writePcm = mixed.first
                    writeLen = mixed.second
                }
                var offset = 0
                while (offset < writeLen) {
                    val written = t.write(writePcm, offset, writeLen - offset)
                    if (written <= 0) break
                    offset += written
                }
            }
            lastPcmUptimeMs = SystemClock.uptimeMillis()
            playCallbacks++
        }

        PlaybackSpectrum.onPcmS16(
            pcm, len, playCh, playFrames, ByteOrder.nativeOrder(), rate,
        )

        val n = playCallbacks
        if (n == 1 || n == 50 || n == 200) {
            Log.i(
                TAG,
                "PCM play#$n bytes=$len ch=$playCh outCh=$outputChannels frames=$playFrames " +
                    "peak=${"%.4f".format(PlaybackSpectrum.lastPcmPeak)} " +
                    "energy=${"%.3f".format(PlaybackSpectrum.energy())} " +
                    "bass=${"%.3f".format(PlaybackSpectrum.bass())} " +
                    "L/R=${"%.2f".format(PlaybackSpectrum.left())}/${"%.2f".format(PlaybackSpectrum.right())}",
            )
        }
    }

    @JvmStatic
    fun nativePause() {
        synchronized(trackLock) {
            runCatching { track?.pause() }
        }
    }

    @JvmStatic
    fun nativeResume() {
        synchronized(trackLock) {
            runCatching { track?.play() }
        }
        Log.i(TAG, "PCM resume energy=${"%.3f".format(PlaybackSpectrum.energy())}")
    }

    @JvmStatic
    fun nativeFlush() {
        synchronized(trackLock) {
            runCatching {
                track?.pause()
                track?.flush()
                track?.play()
            }
            PlaybackSpectrum.softReset()
        }
        playCallbacks = 0
        Log.i(TAG, "PCM flush/softReset energy=${"%.3f".format(PlaybackSpectrum.energy())}")
    }

    @JvmStatic
    fun nativeCleanup() {
        val inSwap = SystemClock.uptimeMillis() < mediaSwapUntilMs
        synchronized(trackLock) {
            if (aoutDepth > 0) aoutDepth--
            if (aoutDepth > 0) {
                Log.i(TAG, "PCM cleanup skipped — newer aout still live (depth=$aoutDepth)")
                return
            }
            if (inSwap && track != null) {
                runCatching {
                    track?.pause()
                    track?.flush()
                }
                PlaybackSpectrum.softReset()
                playCallbacks = 0
                Log.i(TAG, "PCM cleanup during media swap — keeping AudioTrack")
                return
            }
            releaseTrackLocked()
        }
        PlaybackSpectrum.softReset()
        playCallbacks = 0
        Log.i(TAG, "PCM cleanup (soft) — waiting for setup/heal")
    }

    @JvmStatic
    fun nativeVolume(volume: Float, mute: Boolean) {
        volumeLinear = volume.coerceIn(0f, 2f)
        muted = mute
        synchronized(trackLock) {
            track?.let { applyGainLocked(it) }
        }
    }

    /** Estimated AudioTrack output latency in milliseconds (0 if unknown). */
    fun outputLatencyMs(): Int {
        synchronized(trackLock) {
            val t = track ?: return 0
            val rate = sampleRate.coerceAtLeast(1)
            val fromApi = runCatching {
                // getLatency() is hidden/deprecated on some SDKs — reflect safely.
                val m = AudioTrack::class.java.methods.firstOrNull {
                    it.name == "getLatency" && it.parameterCount == 0
                }
                (m?.invoke(t) as? Int) ?: 0
            }.getOrDefault(0).coerceAtLeast(0)
            val bufMs = ((t.bufferSizeInFrames.toLong() * 1000L) / rate).toInt()
            return when {
                fromApi > 0 -> fromApi.coerceIn(0, 500)
                bufMs > 0 -> (bufMs / 2).coerceIn(0, 500)
                else -> 0
            }
        }
    }

    private fun openTrackLocked(rate: Int, ch: Int): Boolean {
        releaseTrackLocked()
        sampleRate = rate.coerceAtLeast(8000)
        channels = ch.coerceIn(1, 8)
        val masks = channelMaskCandidates(channels)
        var lastError: Exception? = null
        for (channelMask in masks) {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
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
                            .setSampleRate(sampleRate)
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
                applyGainLocked(t)
                track = t
                outputChannels = when (channelMask) {
                    AudioFormat.CHANNEL_OUT_MONO -> 1
                    AudioFormat.CHANNEL_OUT_STEREO -> 2
                    AudioFormat.CHANNEL_OUT_QUAD -> 4
                    AudioFormat.CHANNEL_OUT_5POINT1 -> 6
                    else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        channelMask == AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
                    ) {
                        8
                    } else {
                        channels.coerceIn(1, 8)
                    }
                }
                Log.i(
                    TAG,
                    "AudioTrack open ${sampleRate}Hz mask=0x${channelMask.toString(16)} " +
                        "inCh=$channels outCh=$outputChannels buf=${bufSize}B latency~${outputLatencyMs()}ms",
                )
                return true
            } catch (e: Exception) {
                lastError = e
            }
        }
        Log.e(TAG, "AudioTrack setup failed for $sampleRate Hz / $channels ch", lastError)
        track = null
        return false
    }

    /** Prefer true multi-channel out; fall back toward stereo/mono if the device rejects it. */
    private fun channelMaskCandidates(ch: Int): List<Int> {
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

    /**
     * Downmix multi-channel PCM to the AudioTrack layout when the device cannot
     * open a matching surround track. Spectrum analysis still uses the original buffer.
     * Reuses [downmixScratch] to avoid GC on the realtime audio path.
     */
    private fun downmixToOutput(
        pcm: ByteArray,
        bytes: Int,
        inCh: Int,
        frames: Int,
        outCh: Int,
    ): Pair<ByteArray, Int> {
        val inChannels = inCh.coerceIn(1, 8)
        val outChannels = when (outCh) {
            1 -> 1
            else -> 2 // Only mono/stereo fallbacks are opened; never write a partial 5.1 frame.
        }
        val frameCount = (bytes / (2 * inChannels)).coerceAtMost(frames).coerceAtLeast(0)
        val need = frameCount * outChannels * 2
        if (downmixScratch.size < need) {
            downmixScratch = ByteArray(need.coerceAtLeast(4096))
        }
        val out = downmixScratch
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
        return out to need
    }

    private fun applyGainLocked(t: AudioTrack) {
        val gain = if (muted) 0f else volumeLinear.coerceIn(0f, 1f)
        runCatching { t.setVolume(gain) }
    }

    private fun releaseTrackLocked() {
        val t = track ?: return
        track = null
        runCatching {
            t.pause()
            t.flush()
            t.release()
        }
    }

    private external fun nativeInit(): Boolean
    private external fun nativeAttach(mediaPlayerPtr: Long): Boolean
    private external fun nativeDetach(mediaPlayerPtr: Long)
}
