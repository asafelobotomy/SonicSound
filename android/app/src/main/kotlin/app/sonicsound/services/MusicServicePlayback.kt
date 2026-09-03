package app.sonicsound.services

import android.graphics.Bitmap
import android.net.Uri
import com.bumptech.glide.Glide
import com.getcapacitor.JSObject
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import app.sonicsound.App
import app.sonicsound.AppEvent
import app.sonicsound.AppEvents
import app.sonicsound.KeyValueStorage
import app.sonicsound.models.JukeboxCollection
import app.sonicsound.playback.JukeboxEngine
import app.sonicsound.playback.MediaSessionController
import app.sonicsound.playback.PlayQueue
import app.sonicsound.playback.PlaybackNotification
import app.sonicsound.playback.VlcEngine
import app.sonicsound.playback.VlcPcmOutput
import app.sonicsound.playback.VinylProcessor
import app.sonicsound.subsonic.SubsonicClient
import app.sonicsound.visualizer.TrackCharacterPrefetch
import java.util.concurrent.ExecutionException

/** Session / notification / event helpers for [MusicService]. */
internal class MusicServicePlayback(
    private val subsonicClient: SubsonicClient,
    private val queue: PlayQueue,
    private val jukeboxEngine: JukeboxEngine,
    private val session: MediaSessionController,
    private val gson: Gson,
    private val engine: () -> VlcEngine,
    private val notification: () -> PlaybackNotification,
    private val notifyListeners: (String, JSObject?) -> Unit,
    private val notifyError: (String?) -> Unit,
) {
    var refillInProgress = false

    fun playLocked(service: MusicService) {
        val currentTrack = queue.currentTrack ?: return
        val eng = engine()
        eng.requestAudioFocus(service)
        eng.play()
        prefetchNextTrackCharacter()
        CoroutineScope(IO).launch {
            session.putAlbumAndDuration(currentTrack)
            val albumArtUri = Uri.parse(subsonicClient.getAlbumArt(currentTrack.albumId))
            var albumArtBitmap: Bitmap? = null
            try {
                val loaded = Glide.with(App.context).asBitmap().load(albumArtUri).submit().get()
                albumArtBitmap = loaded.copy(Bitmap.Config.ARGB_8888, false) ?: loaded
            } catch (e: ExecutionException) {
                notifyError(e.message)
            } catch (e: InterruptedException) {
                notifyError(e.message)
            }
            session.updateMediaMetadata(currentTrack, albumArtBitmap)
            notification().update(currentTrack, albumArtBitmap)
        }
    }

    fun prefetchNextTrackCharacter() {
        val next = queue.peekNext() ?: return
        CoroutineScope(IO).launch {
            runCatching {
                val local = java.io.File(subsonicClient.getLocalSongUri(next.id))
                val uri = when {
                    local.exists() && local.length() > 1024L -> "file://${local.path}"
                    KeyValueStorage.getOfflineMode() -> null
                    else -> subsonicClient.getSongUri(next)
                } ?: return@launch
                TrackCharacterPrefetch.prefetch(App.context, next.id, uri)
            }
        }
    }

    suspend fun refillQueue(collection: JukeboxCollection): Boolean {
        if (refillInProgress) return false
        refillInProgress = true
        try {
            val more = jukeboxEngine.fetchBatch(collection)
            if (more.isEmpty()) return false
            queue.appendEntries(more)
            notifyListeners("playlistUpdated", null)
            return true
        } catch (e: Exception) {
            notifyError(e.message)
            return false
        } finally {
            refillInProgress = false
        }
    }

    fun advancePlaybackLocked(playLocked: () -> Unit) {
        try {
            engine().loadMedia(queue.currentTrack!!)
            playLocked()
            queue.collection?.let { col ->
                queue.currentTrack?.id?.let { jukeboxEngine.onTrackPlayed(it, col) }
            }
        } catch (e: Exception) {
            notifyError(e.message)
        }
    }

    fun maybeRefillCollection() {
        val collection = queue.collection ?: return
        if (queue.remainingCount() > 10 || refillInProgress) return
        CoroutineScope(IO).launch { refillQueue(collection) }
    }

    fun positionMs(): Long {
        val track = queue.currentTrack ?: return 0L
        return (engine().position * track.duration * 1000).toLong()
    }

    fun durationMs(): Long {
        val track = queue.currentTrack ?: return 0L
        return (track.duration * 1000L).coerceAtLeast(0L)
    }

    fun publishVinylClock() {
        VinylProcessor.publishClock(positionMs(), durationMs())
    }

    fun onPlayerEvent(
        event: MediaPlayer.Event,
        pendingSeek: Float?,
        clearPendingSeek: () -> Unit,
        setPendingSeek: (Float?) -> Unit,
        next: () -> Unit,
    ): Float? {
        var pending = pendingSeek
        val track = queue.currentTrack
        when (event.type) {
            MediaPlayer.Event.TimeChanged -> {
                publishVinylClock()
                session.setPlayingState(positionMs(), 0f)
                AppEvents.emit(AppEvent.Progress(engine().position))
            }
            MediaPlayer.Event.EndReached -> {
                try {
                    next()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                session.setPausedState((track?.duration ?: 0) * 1000L)
                track?.let { notification().update(it, null, true) }
            }
            MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> {
                publishVinylClock()
                AppEvents.emit(AppEvent.PlaybackPaused)
                session.setPausedState(positionMs())
                track?.let { notification().update(it, null, true) }
            }
            MediaPlayer.Event.Playing -> {
                pending?.let { seekPos ->
                    pending = null
                    clearPendingSeek()
                    setPendingSeek(null)
                    engine().seek(seekPos)
                }
                VlcPcmOutput.onEnginePlaying(engine().mediaPlayer)
                publishVinylClock()
                AppEvents.emit(AppEvent.PlaybackPlay)
                if (track != null) {
                    notifyListeners(
                        "currentTrack",
                        JSObject("{\"currentTrack\": ${gson.toJson(track)}}"),
                    )
                    session.setPlayingState(positionMs(), 1f)
                    notification().update(track, null, false)
                }
            }
        }
        return pending
    }
}
