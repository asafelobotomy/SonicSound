package app.sonicsound.playback

import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.videolan.libvlc.MediaPlayer

/** Heal / AudioTrack open helpers for [VlcPcmOutput]. */
internal class VlcPcmHeal(
    private val tag: String,
    private val trackLock: Any,
    private val isPcmFresh: () -> Boolean,
    private val getPlayCallbacks: () -> Int,
    private val getLastPcmUptimeMs: () -> Long,
    private val getTrack: () -> AudioTrack?,
    private val openTrack: (Int, Int) -> Boolean,
    private val playTrack: () -> Unit,
    private val attach: (MediaPlayer) -> Boolean,
    private val noteMediaSwap: () -> Unit,
    private val syncVinyl: (Boolean, Int) -> Unit,
    private val currentSampleRate: () -> Int,
    private val getSampleRate: () -> Int,
    private val getChannels: () -> Int,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pcmHealPlayer: MediaPlayer? = null
    private var pcmHealBaseline = 0
    private var pcmHealBaselineTime = 0L

    private val pcmHealRunnable = Runnable {
        val player = pcmHealPlayer ?: return@Runnable
        if (isPcmFresh()) return@Runnable
        if (getPlayCallbacks() != pcmHealBaseline && getLastPcmUptimeMs() != pcmHealBaselineTime) {
            return@Runnable
        }
        Log.w(tag, "PCM silent after Playing — re-attaching tap")
        noteMediaSwap()
        val attached = attach(player)
        synchronized(trackLock) {
            if (getTrack() == null) openTrack(getSampleRate(), getChannels())
            else playTrack()
        }
        syncVinyl(attached, currentSampleRate())
    }

    fun onEnginePlaying(player: MediaPlayer?, ready: Boolean) {
        synchronized(trackLock) {
            val t = getTrack()
            if (t == null) {
                if (openTrack(getSampleRate(), getChannels())) {
                    Log.w(tag, "onEnginePlaying healed null AudioTrack")
                }
            } else if (t.playState != AudioTrack.PLAYSTATE_PLAYING) {
                runCatching { t.play() }
            }
        }
        if (player == null || !ready) return
        pcmHealPlayer = player
        pcmHealBaseline = getPlayCallbacks()
        pcmHealBaselineTime = getLastPcmUptimeMs()
        mainHandler.removeCallbacks(pcmHealRunnable)
        mainHandler.postDelayed(pcmHealRunnable, 900L)
    }

    fun cancel() {
        mainHandler.removeCallbacks(pcmHealRunnable)
    }
}
