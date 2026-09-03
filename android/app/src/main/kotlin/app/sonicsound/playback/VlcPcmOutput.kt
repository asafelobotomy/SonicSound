package app.sonicsound.playback

import android.media.AudioDeviceInfo
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import android.util.Log
import app.sonicsound.KeyValueStorage
import app.sonicsound.visualizer.PlaybackSpectrum
import org.videolan.libvlc.MediaPlayer

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

    /** True after a successful nativeAttach until detach. */
    @Volatile
    private var tapAttached = false

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
    init {
        try {
            if (!VlcPcmNativeTap.loadLibraries()) {
                ready = false
            } else {
                ready = nativeInit()
                if (!ready) Log.w(TAG, "nativeInit failed — falling back to LibVLC aout")
                else Log.i(TAG, "PCM tap ready")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "vlc_pcm_tap unavailable", e)
            ready = false
        }
    }

    val isAvailable: Boolean get() = ready

    /** True when LibVLC play callbacks are currently attached to this tap. */
    fun isTapAttached(): Boolean = ready && tapAttached

    fun currentSampleRate(): Int = sampleRate.coerceAtLeast(8000)

    /**
     * Apply vinyl FX config from settings. When [tapActive] is false, vinyl profile
     * keeps EQ warmth only (processor disabled).
     */
    fun syncVinylProcessor(tapActive: Boolean = isTapAttached(), sampleRateHint: Int = currentSampleRate()) {
        val settings = KeyValueStorage.getSettings()
        val profile = AudioProfile.resolve(settings)
        val condition = VinylCondition.resolve(settings)
        val forceOff = profile == AudioProfile.VINYL && !tapActive
        VinylProcessor.configure(
            profileId = profile,
            conditionId = condition,
            rateHz = sampleRateHint.coerceAtLeast(8000),
            forceDisableFx = forceOff,
        )
    }

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
        val ptr = VlcPcmNativeTap.playerNativePtr(player)
        if (ptr == 0L) {
            Log.w(TAG, "MediaPlayer native instance is 0")
            tapAttached = false
            return false
        }
        val ok = nativeAttach(ptr)
        tapAttached = ok
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
        nativeDetach(VlcPcmNativeTap.playerNativePtr(player))
        tapAttached = false
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
        VinylProcessor.reset()
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
    private val heal = VlcPcmHeal(
        tag = TAG,
        trackLock = trackLock,
        isPcmFresh = { isPcmFresh() },
        getPlayCallbacks = { playCallbacks },
        getLastPcmUptimeMs = { lastPcmUptimeMs },
        getTrack = { track },
        openTrack = { rate, ch -> openTrackLocked(rate, ch) },
        playTrack = { runCatching { track?.play() } },
        attach = { attach(it) },
        noteMediaSwap = { noteMediaSwap() },
        syncVinyl = { active, rate -> syncVinylProcessor(tapActive = active, sampleRateHint = rate) },
        currentSampleRate = { currentSampleRate() },
        getSampleRate = { sampleRate },
        getChannels = { channels },
    )

    fun onEnginePlaying(player: MediaPlayer? = null) {
        heal.onEnginePlaying(player, ready)
    }

    @JvmStatic
    fun nativeSetup(rate: Int, ch: Int): Boolean {
        val ok: Boolean
        val wantRate: Int
        synchronized(trackLock) {
            wantRate = rate.coerceAtLeast(8000)
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
                ok = true
            } else {
                sampleRate = wantRate
                channels = wantCh
                ok = openTrackLocked(sampleRate, channels)
                Log.i(TAG, "AudioTrack setup $sampleRate Hz / $channels ch ok=$ok depth=$aoutDepth")
                if (!ok && aoutDepth > 0) aoutDepth--
            }
        }
        // Authoritative rate for vinyl DSP (settings may have configured earlier with default).
        // nativeSetup only runs when the PCM tap is attached and receiving format callbacks.
        syncVinylProcessor(tapActive = true, sampleRateHint = wantRate)
        return ok
    }

    @JvmStatic
    fun nativePlay(pcm: ByteArray, bytes: Int, ch: Int, frames: Int) {
        VlcPcmPlay.handle(
            tag = TAG,
            trackLock = trackLock,
            pcm = pcm,
            bytes = bytes,
            ch = ch,
            frames = frames,
            sampleRate = sampleRate,
            getTrack = { track },
            openTrack = { rate, chn -> openTrackLocked(rate, chn) },
            getOutputChannels = { outputChannels },
            downmix = { p, b, ic, f, oc -> downmixToOutput(p, b, ic, f, oc) },
            onWritten = {
                lastPcmUptimeMs = SystemClock.uptimeMillis()
                playCallbacks++
                playCallbacks
            },
        )
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
        VinylProcessor.reset()
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
                VinylProcessor.reset()
                playCallbacks = 0
                Log.i(TAG, "PCM cleanup during media swap — keeping AudioTrack")
                return
            }
            releaseTrackLocked()
        }
        PlaybackSpectrum.softReset()
        VinylProcessor.reset()
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
            return VlcPcmAudioTrack.estimateLatencyMs(t, sampleRate)
        }
    }

    private fun openTrackLocked(rate: Int, ch: Int): Boolean {
        releaseTrackLocked()
        sampleRate = rate.coerceAtLeast(8000)
        channels = ch.coerceIn(1, 8)
        val opened = VlcPcmAudioTrack.open(
            sampleRate = sampleRate,
            channels = channels,
            applyGain = { applyGainLocked(it) },
            latencyMs = { t, rateHz -> VlcPcmAudioTrack.estimateLatencyMs(t, rateHz) },
        )
        if (opened == null) {
            track = null
            return false
        }
        track = opened.track
        outputChannels = opened.outputChannels
        return true
    }

    private fun downmixToOutput(
        pcm: ByteArray,
        bytes: Int,
        inCh: Int,
        frames: Int,
        outCh: Int,
    ): Pair<ByteArray, Int> {
        val (out, need, scratch) = VlcPcmAudioTrack.downmixToOutput(
            downmixScratch, pcm, bytes, inCh, frames, outCh
        )
        downmixScratch = scratch
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
