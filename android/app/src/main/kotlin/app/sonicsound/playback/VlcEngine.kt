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
import app.sonicsound.models.Song
import app.sonicsound.subsonic.SubsonicClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/**
 * LibVLC MediaPlayer wrapper: load, play/pause, seek (0..1), volume, audio focus.
 */
class VlcEngine(
    private val subsonicClient: SubsonicClient,
    private val onDevicesRemoved: () -> Unit
) {
    private val args = mutableListOf("-vvv")
    private var mLibVLC: LibVLC? = null
    var mediaPlayer: MediaPlayer? = null
        private set

    private val mAudioManager: AudioManager =
        App.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mPlaybackAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private var mAudioFocusRequest: AudioFocusRequest? = null
    private var wasPlaying: Boolean = false

    private val audioFocusChangeListener = OnAudioFocusChangeListener { focusChange: Int ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN ->
                if (mediaPlayer != null &&
                    mediaPlayer!!.media != null &&
                    mediaPlayer!!.position < mediaPlayer!!.media!!.duration &&
                    wasPlaying
                ) {
                    wasPlaying = false
                    mediaPlayer!!.play()
                }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
                    wasPlaying = true
                    mediaPlayer!!.pause()
                }
            }
        }
    }

    private inner class DeviceCallback : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            onDevicesRemoved()
        }
    }

    init {
        mAudioManager.registerAudioDeviceCallback(DeviceCallback(), null)
    }

    private fun buildLibVlcArgs(): ArrayList<String> {
        val settings = KeyValueStorage.getSettings()
        val libArgs = ArrayList(args)
        if (settings.eqEnabled) {
            libArgs.add("--audio-filter=equalizer")
        }
        if (settings.replayGainEnabled) {
            libArgs.add("--audio-replay-gain-mode=track")
        }
        return libArgs
    }

    fun create(eventListener: MediaPlayer.EventListener) {
        mLibVLC = LibVLC(App.context, buildLibVlcArgs())
        mediaPlayer = MediaPlayer(mLibVLC)
        mediaPlayer!!.setEventListener(eventListener)
    }

    fun release() {
        if (mediaPlayer?.isPlaying == true) mediaPlayer?.stop()
        mediaPlayer?.release()
        mLibVLC?.release()
        mediaPlayer = null
        mLibVLC = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mAudioFocusRequest != null) {
            mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest!!)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            mAudioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    val isPlaying: Boolean get() = mediaPlayer?.isPlaying == true
    val position: Float get() = mediaPlayer?.position ?: 0f

    fun play() {
        wasPlaying = false
        if (mediaPlayer?.media != null) {
            mediaPlayer!!.play()
        }
    }

    fun pause() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer!!.pause()
        }
    }

    /** Seek to a fractional position in [0f, 1f] (LibVLC convention). */
    fun seek(position: Float) {
        mediaPlayer?.position = position.coerceIn(0f, 1f)
    }

    /**
     * Seek using MediaSession milliseconds. Converts to LibVLC fraction via track duration.
     */
    fun seekToMs(positionMs: Long, durationSec: Int) {
        if (durationSec <= 0) return
        seek(positionMs.toFloat() / (durationSec * 1000f))
    }

    fun setVolume(volume: Int) {
        mediaPlayer?.volume = volume
    }

    fun requestAudioFocus(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (mAudioFocusRequest != null) {
                mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest!!)
            }
            mAudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(mPlaybackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            val res = mAudioManager.requestAudioFocus(mAudioFocusRequest!!)
            if (res == AudioManager.AUDIOFOCUS_REQUEST_FAILED && mediaPlayer?.isPlaying == true) {
                mediaPlayer!!.pause()
            }
        } else {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            val result: Int = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED && mediaPlayer?.isPlaying == true) {
                mediaPlayer!!.pause()
            }
        }
    }

    @Throws(Exception::class)
    fun loadMedia(currentTrack: Song) {
        var uri: String?
        val file = File(subsonicClient.getLocalSongUri(currentTrack.id))
        if (file.exists()) {
            var lock: FileLock? = null
            try {
                val channel = RandomAccessFile(file, "rw").channel
                lock = channel.tryLock()
                uri = "file://" + file.path
            } catch (e: OverlappingFileLockException) {
                uri = subsonicClient.getSongUri(currentTrack)
            } catch (e: IOException) {
                uri = subsonicClient.getSongUri(currentTrack)
            }
            if (lock != null && lock.isValid) {
                try {
                    lock.release()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        } else {
            if (KeyValueStorage.getOfflineMode()) {
                throw Exception("The song did not download successfully. Try to download it again.")
            }
            uri = subsonicClient.getSongUri(currentTrack)
        }
        if (uri != null) {
            val media = Media(mLibVLC, Uri.parse(uri))
            if (mediaPlayer!!.isPlaying) mediaPlayer!!.pause()
            mediaPlayer!!.media = media
            media.release()
            if (!KeyValueStorage.getOfflineMode()) {
                CoroutineScope(IO).launch {
                    try {
                        subsonicClient.scrobble(currentTrack.id)
                    } catch (ex: Exception) {
                        Globals.NotifyObservers("EX", "Couldn't scrobble. Check your connection.")
                    }
                }
            }
        } else {
            Log.w("VlcEngine", "No URI for track ${currentTrack.id}")
        }
    }

    fun loadStreamUrl(url: String) {
        val media = Media(mLibVLC, Uri.parse(url))
        if (mediaPlayer!!.isPlaying) mediaPlayer!!.pause()
        mediaPlayer!!.media = media
        media.release()
    }
}
