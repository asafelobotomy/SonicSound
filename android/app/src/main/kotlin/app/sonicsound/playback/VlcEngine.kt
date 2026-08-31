package app.sonicsound.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.net.Uri
import android.os.Build
import android.util.Log
import app.sonicsound.App
import app.sonicsound.Globals
import app.sonicsound.KeyValueStorage
import app.sonicsound.models.Settings
import app.sonicsound.models.Song
import app.sonicsound.subsonic.SubsonicClient
import app.sonicsound.visualizer.PlaybackSpectrum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.io.File

/**
 * LibVLC MediaPlayer wrapper: load, play/pause, seek (0..1), volume, audio focus.
 */
class VlcEngine(
    private val subsonicClient: SubsonicClient,
    private val onDevicesRemoved: () -> Unit
) {
    private val args = mutableListOf<String>()
    private var mLibVLC: LibVLC? = null
    var mediaPlayer: MediaPlayer? = null
        private set

    private var appliedReplayGain = false
    private var appliedEqualizerFilter = false
    private var appliedPcmTap = false
    private var released = false
    private val lock = Any()

    private val mAudioManager: AudioManager =
        App.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mPlaybackAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private var mAudioFocusRequest: AudioFocusRequest? = null
    private var wasPlaying: Boolean = false
    private var hasAudioFocus = false

    private val audioFocusChangeListener = OnAudioFocusChangeListener { focusChange: Int ->
        if (released) return@OnAudioFocusChangeListener
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                val player = mediaPlayer ?: return@OnAudioFocusChangeListener
                val media = player.media ?: return@OnAudioFocusChangeListener
                if (wasPlaying && player.position < media.duration) {
                    wasPlaying = false
                    runCatching { player.play() }
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> hasAudioFocus = false
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                val player = mediaPlayer ?: return@OnAudioFocusChangeListener
                if (player.isPlaying) {
                    wasPlaying = true
                    runCatching { player.pause() }
                }
            }
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            if (!released) onDevicesRemoved()
        }
    }

    init {
        mAudioManager.registerAudioDeviceCallback(deviceCallback, null)
    }

    private fun buildLibVlcArgs(settings: Settings): ArrayList<String> {
        val libArgs = ArrayList(args)
        // Equalizer is applied via MediaPlayer.setEqualizer — do NOT toggle
        // --audio-filter=equalizer here; that forced a full LibVLC recreate on every
        // Settings audio-profile change and killed the PCM visualizer tap.
        if (settings.replayGainEnabled) {
            libArgs.add("--audio-replay-gain-mode=track")
            libArgs.add("--audio-replay-gain-preamp=0")
        }
        return libArgs
    }

    fun needsRecreate(settings: Settings): Boolean {
        // Only ReplayGain is a LibVLC-instance flag. EQ/profile changes use setEqualizer.
        return settings.replayGainEnabled != appliedReplayGain
    }

    fun create(eventListener: MediaPlayer.EventListener) {
        synchronized(lock) {
            released = false
            val settings = KeyValueStorage.getSettings()
            val profile = AudioProfile.resolve(settings)
            appliedReplayGain = settings.replayGainEnabled
            appliedEqualizerFilter = AudioProfile.needsEqualizerFilter(profile)
            appliedPcmTap = VlcPcmOutput.isAvailable
            mLibVLC = LibVLC(App.context, buildLibVlcArgs(settings))
            mediaPlayer = MediaPlayer(mLibVLC).also { player ->
                player.setEventListener(eventListener)
                if (appliedPcmTap) {
                    if (!VlcPcmOutput.attach(player)) {
                        Log.w("VlcEngine", "PCM tap attach failed — using LibVLC aout")
                        appliedPcmTap = false
                    }
                }
            }
            applyAudioProfileUnlocked(profile)
        }
    }

    fun applyAudioProfile(profileId: String = AudioProfile.resolve(KeyValueStorage.getSettings())) {
        synchronized(lock) {
            applyAudioProfileUnlocked(profileId)
        }
    }

    private fun applyAudioProfileUnlocked(
        profileId: String = AudioProfile.resolve(KeyValueStorage.getSettings()),
    ) {
        if (released) return
        AudioProfile.apply(mediaPlayer, profileId)
    }

    fun release(wipeSpectrum: Boolean = true) {
        synchronized(lock) {
            if (released) return
            released = true
            runCatching { mAudioManager.unregisterAudioDeviceCallback(deviceCallback) }
            val player = mediaPlayer
            mediaPlayer = null
            if (player != null) {
                runCatching { VlcPcmOutput.detach(player, wipeSpectrum = wipeSpectrum) }
                runCatching { player.setEventListener(null) }
                runCatching {
                    if (player.isPlaying) player.stop()
                }
                runCatching { player.release() }
            }
            runCatching { mLibVLC?.release() }
            mLibVLC = null
            abandonAudioFocus()
        }
    }

    val isPlaying: Boolean get() = synchronized(lock) { !released && mediaPlayer?.isPlaying == true }
    val position: Float get() = synchronized(lock) { if (released) 0f else mediaPlayer?.position ?: 0f }

    fun play() {
        synchronized(lock) {
            if (released) return
            wasPlaying = false
            val player = mediaPlayer ?: return
            if (player.media != null) {
                runCatching { player.play() }
            }
        }
    }

    fun pause() {
        synchronized(lock) {
            if (released) return
            val player = mediaPlayer ?: return
            if (player.isPlaying) {
                runCatching { player.pause() }
            }
        }
    }

    /** Seek to a fractional position in [0f, 1f] (LibVLC convention). */
    fun seek(position: Float) {
        synchronized(lock) {
            if (released) return
            mediaPlayer?.position = position.coerceIn(0f, 1f)
        }
    }

    /**
     * Seek using MediaSession milliseconds. Converts to LibVLC fraction via track duration.
     */
    fun seekToMs(positionMs: Long, durationSec: Int) {
        if (durationSec <= 0) return
        seek(positionMs.toFloat() / (durationSec * 1000f))
    }

    fun setVolume(volume: Int) {
        if (released) return
        mediaPlayer?.volume = volume
    }

    fun requestAudioFocus(context: Context) {
        if (released || hasAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (mAudioFocusRequest == null) {
                mAudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(mPlaybackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
            }
            val res = mAudioManager.requestAudioFocus(mAudioFocusRequest!!)
            hasAudioFocus = res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!hasAudioFocus) {
                mediaPlayer?.takeIf { it.isPlaying }?.let { runCatching { it.pause() } }
            }
        } else {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            val result: Int = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!hasAudioFocus) {
                mediaPlayer?.takeIf { it.isPlaying }?.let { runCatching { it.pause() } }
            }
        }
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mAudioFocusRequest != null) {
            mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest!!)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            mAudioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
    }

    @Throws(Exception::class)
    fun loadMedia(currentTrack: Song) {
        synchronized(lock) {
            if (released) return
            val uri = resolvePlaybackUri(currentTrack) ?: run {
                Log.w("VlcEngine", "No URI for track ${currentTrack.id}")
                return
            }
            PlaybackSpectrum.prepareForTrack(currentTrack.id)
            val lib = mLibVLC ?: return
            val player = mediaPlayer ?: return
            val media = Media(lib, Uri.parse(uri))
            try {
                applyMediaOptions(media, uri)
                // Avoid player.stop() here: it triggers audio cleanup and, on album
                // swaps, LibVLC often never re-runs setup/play into our PCM tap —
                // audio may continue via a fallback aout while the visualizer dies.
                // Replacing media is enough; VLC flushes/stops the previous item.
                VlcPcmOutput.noteMediaSwap()
                player.media = media
            } finally {
                media.release()
            }
            applyAudioProfileUnlocked()
        }
        if (!KeyValueStorage.getOfflineMode()) {
            CoroutineScope(IO).launch {
                try {
                    subsonicClient.reportPlayback(currentTrack.id, 0, "starting")
                    subsonicClient.scrobble(currentTrack.id)
                } catch (ex: Exception) {
                    Globals.NotifyObservers("EX", "Couldn't scrobble. Check your connection.")
                }
            }
        }
    }

    private fun resolvePlaybackUri(currentTrack: Song): String? {
        val local = File(subsonicClient.getLocalSongUri(currentTrack.id))
        if (local.exists() && local.length() > 1024L) {
            return "file://${local.path}"
        }
        if (KeyValueStorage.getOfflineMode()) {
            throw Exception("The song did not download successfully. Try to download it again.")
        }
        return subsonicClient.getSongUri(currentTrack)
    }

    private fun applyMediaOptions(media: Media, uri: String) {
        if (uri.startsWith("http://", ignoreCase = true) ||
            uri.startsWith("https://", ignoreCase = true)
        ) {
            media.addOption(":network-caching=1500")
            media.addOption(":http-reconnect")
        } else {
            media.addOption(":file-caching=300")
        }
    }

    fun loadStreamUrl(url: String) {
        synchronized(lock) {
            if (released) return
            PlaybackSpectrum.prepareForTrack("stream:${url.hashCode()}")
            val lib = mLibVLC ?: return
            val player = mediaPlayer ?: return
            val media = Media(lib, Uri.parse(url))
            try {
                applyMediaOptions(media, url)
                VlcPcmOutput.noteMediaSwap()
                player.media = media
            } finally {
                media.release()
            }
            applyAudioProfileUnlocked()
        }
    }
}
