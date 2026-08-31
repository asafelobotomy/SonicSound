package app.sonicsound.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import app.sonicsound.visualizer.PlaybackSpectrum
import org.videolan.libvlc.MediaPlayer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * LibVLC decoded-PCM audio output via [libvlc_audio_set_callbacks].
 *
 * Replaces the platform aout so we can play through [AudioTrack] and feed
 * [PlaybackSpectrum] without RECORD_AUDIO / Visualizer.
 */
object VlcPcmOutput {
    private const val TAG = "VlcPcmOutput"

    @Volatile
    private var ready = false

    @Volatile
    private var track: AudioTrack? = null

    @Volatile
    private var channels = 2

    @Volatile
    private var sampleRate = 44100

    @Volatile
    private var volumeLinear = 1f

    @Volatile
    private var muted = false

    @Volatile
    private var playCallbacks = 0

    private val trackLock = Any()

    init {
        try {
            // LibVLC must be mapped before nativeInit dlopen("libvlc.so").
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

    /** Attach PCM callbacks to a LibVLC media player instance. */
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

    fun detach(player: MediaPlayer?) {
        if (!ready) return
        nativeDetach(playerNativePtr(player))
        nativeCleanup()
    }

    /** LibVLC [VLCObject.getInstance] — Kotlin property access is unreliable across versions. */
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

    // --- Called from JNI (VLC audio thread) ---

    @JvmStatic
    fun nativeSetup(rate: Int, ch: Int): Boolean {
        synchronized(trackLock) {
            releaseTrackLocked()
            playCallbacks = 0
            sampleRate = rate.coerceAtLeast(8000)
            channels = ch.coerceIn(1, 8)
            Log.i(TAG, "AudioTrack setup $sampleRate Hz / $channels ch")
            val channelMask = if (channels >= 2) {
                AudioFormat.CHANNEL_OUT_STEREO
            } else {
                AudioFormat.CHANNEL_OUT_MONO
            }
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuf <= 0) {
                Log.e(TAG, "Invalid AudioTrack buffer for $sampleRate Hz / $channels ch")
                return false
            }
            // 3× min keeps underruns rare without huge latency on TV.
            val bufSize = minBuf * 3
            return try {
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
                true
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack setup failed", e)
                track = null
                false
            }
        }
    }

    @JvmStatic
    fun nativePlay(pcm: ByteArray, bytes: Int, ch: Int, frames: Int) {
        val t = track ?: return
        val len = min(bytes, pcm.size)
        if (len <= 0) return
        // Pace playback — LibVLC expects the play callback to consume in real time.
        var offset = 0
        while (offset < len) {
            val written = t.write(pcm, offset, len - offset)
            if (written <= 0) break
            offset += written
        }
        PlaybackSpectrum.onPcmS16(
            pcm,
            len,
            ch.coerceAtLeast(1),
            frames.coerceAtLeast(1),
            ByteOrder.nativeOrder(),
        )
        val n = playCallbacks + 1
        playCallbacks = n
        if (n == 1 || n == 50 || n == 200) {
            Log.i(
                TAG,
                "PCM play#$n bytes=$len ch=$ch frames=$frames " +
                    "peak=${"%.4f".format(PlaybackSpectrum.lastPcmPeak)} " +
                    "energy=${"%.3f".format(PlaybackSpectrum.energy())} " +
                    "bass=${"%.3f".format(PlaybackSpectrum.bass())}",
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
    fun nativeFlush() {
        synchronized(trackLock) {
            runCatching {
                track?.pause()
                track?.flush()
                track?.play()
            }
            PlaybackSpectrum.reset()
        }
    }

    @JvmStatic
    fun nativeCleanup() {
        synchronized(trackLock) {
            releaseTrackLocked()
        }
        PlaybackSpectrum.reset()
    }

    @JvmStatic
    fun nativeVolume(volume: Float, mute: Boolean) {
        volumeLinear = volume.coerceIn(0f, 2f)
        muted = mute
        synchronized(trackLock) {
            track?.let { applyGainLocked(it) }
        }
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
