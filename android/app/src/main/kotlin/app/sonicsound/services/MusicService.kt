package app.sonicsound.services

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.bumptech.glide.Glide
import com.getcapacitor.JSObject
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import app.sonicsound.App
import app.sonicsound.CurrentState
import app.sonicsound.Globals
import app.sonicsound.IBroadcastObserver
import app.sonicsound.KeyValueStorage.Companion.getActiveAccount
import app.sonicsound.models.Playlist
import app.sonicsound.models.SearchType
import app.sonicsound.models.Song
import app.sonicsound.playback.MediaSessionController
import app.sonicsound.playback.PlayQueue
import app.sonicsound.playback.PlaybackCommander
import app.sonicsound.playback.PlaybackNotification
import app.sonicsound.playback.VlcEngine
import app.sonicsound.subsonic.SubsonicClient
import java.util.concurrent.ExecutionException

class MusicService : Service(), IBroadcastObserver, MediaPlayer.EventListener {
    private val subsonicClient: SubsonicClient = SubsonicClient(getActiveAccount())
    private val gson: Gson = GsonBuilder().serializeNulls().create()
    private val connectivityManager: ConnectivityManager =
        App.context.getSystemService(ConnectivityManager::class.java)

    private val queue = PlayQueue()
    private val session = MediaSessionController()
    private lateinit var notification: PlaybackNotification
    private lateinit var engine: VlcEngine
    private lateinit var commander: PlaybackCommander
    private val binder = LocalBinder()

    companion object {
        fun getDefaultPlaylist(): Playlist {
            return Playlist("", "", "", "", false, 0, 0, "", "", listOf())
        }
    }

    override fun onCreate() {
        super.onCreate()
        Globals.RegisterObserver(this)
        Log.i("MusicService", "Created")
        notification = PlaybackNotification(this, session.sessionToken)
        notification.createChannel()
        notification.buildActions()
        engine = VlcEngine(subsonicClient) { pause() }
        engine.create(this)
        commander = PlaybackCommander(
            subsonicClient, connectivityManager, queue, engine,
            onPlay = { play() },
            onPause = { pause() },
            onNext = { next() },
            onPrev = { prev() },
            onCancel = {
                Log.i("MusicService", "Stopping signal received. Stopping.")
                notification.stopForeground()
                stopSelf()
                notification.cancel()
            },
            playSearch = { q, t -> playSearch(q, t) }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("MusicService", "destroying service")
        Globals.UnregisterObserver(this)
        engine.release()
        notification.cancel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun notifyListeners(action: String, value: JSObject?) {
        Globals.NotifyObservers("MS$action", value?.toString())
    }

    private fun playSearch(query: String, type: SearchType = SearchType.SONG) {
        Log.i("PlaySearch", "Searching with query $query")
        when (type) {
            SearchType.SONG -> {
                val search = subsonicClient.search(query)
                if (search.song != null && search.song.isNotEmpty()) {
                    commander.playRadio(search.song[0].id)
                }
            }
            SearchType.ARTIST, SearchType.ALBUM -> {
                val search = subsonicClient.search(query)
                if (search.album != null && search.album.isNotEmpty()) {
                    commander.playAlbum(search.album[0].id, 0)
                } else if (search.song != null && search.song.isNotEmpty()) {
                    commander.playRadio(search.song[0].id)
                }
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != null) {
            CoroutineScope(IO).launch { commander.handleStartAction(intent) }
        }
        return START_STICKY
    }

    override fun update(action: String?, value: String?) {
        if (action!!.startsWith("SL")) {
            commander.handleBroadcast(action, value)
        }
    }

    fun skipTo(track: Int) {
        try {
            queue.skipTo(track)
            engine.loadMedia(queue.currentTrack!!)
            play()
        } catch (e: Exception) {
            Globals.NotifyObservers("EX", e.message)
        }
    }

    private fun pause() = engine.pause()

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun play() {
        val currentTrack = queue.currentTrack ?: return
        engine.play()
        CoroutineScope(IO).launch {
            session.putAlbumAndDuration(currentTrack)
            val albumArtUri = Uri.parse(subsonicClient.getAlbumArt(currentTrack.albumId))
            var albumArtBitmap: Bitmap? = null
            try {
                albumArtBitmap = Glide.with(App.context).asBitmap().load(albumArtUri).submit().get()
            } catch (e: ExecutionException) {
                Globals.NotifyObservers("EX", e.message)
            } catch (e: InterruptedException) {
                Globals.NotifyObservers("EX", e.message)
            }
            session.updateMediaMetadata(currentTrack, albumArtBitmap)
            notification.update(currentTrack, albumArtBitmap)
        }
    }

    private fun next() {
        if (queue.next() != null) {
            try {
                engine.loadMedia(queue.currentTrack!!)
                play()
            } catch (e: Exception) {
                Globals.NotifyObservers("EX", e.message)
            }
        }
    }

    private fun prev() {
        if (queue.prev() != null) {
            try {
                engine.loadMedia(queue.currentTrack!!)
                play()
            } catch (e: Exception) {
                Globals.NotifyObservers("EX", e.message)
            }
        }
    }

    private fun setPlaylistAndPlay(playlist: Playlist, track: Int, seek: Float, playing: Boolean) {
        if (engine.isPlaying) {
            engine.pause()
        }
        queue.playlist = playlist
        queue.currentTrack = playlist.entry[track]
        engine.loadMedia(queue.currentTrack!!)
        engine.seek(seek)
        if (playing) engine.play()
    }

    private fun positionMs(): Long {
        val track = queue.currentTrack ?: return 0L
        return (engine.position * track.duration * 1000).toLong()
    }

    override fun onEvent(event: MediaPlayer.Event) {
        val track = queue.currentTrack
        when (event.type) {
            MediaPlayer.Event.TimeChanged -> {
                session.setPlayingState(positionMs(), 0f)
                notifyListeners("progress", JSObject("{\"time\": ${engine.position}}"))
            }
            MediaPlayer.Event.EndReached -> {
                notifyListeners("stopped", null)
                try {
                    next()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                session.setPausedState((track?.duration ?: 0) * 1000L)
                track?.let { notification.update(it, null, true) }
            }
            MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> {
                notifyListeners("paused", null)
                session.setPausedState(positionMs())
                track?.let { notification.update(it, null, true) }
            }
            MediaPlayer.Event.Playing -> {
                engine.requestAudioFocus(this)
                notifyListeners("play", null)
                notifyListeners(
                    "currentTrack",
                    JSObject("{\"currentTrack\": ${gson.toJson(track!!)}}")
                )
                session.setPlayingState(positionMs(), 1f)
                track.let { notification.update(it, null, false) }
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getCurrentState(): CurrentState = CurrentState(
            engine.isPlaying,
            engine.position,
            queue.currentTrack ?: Song("", "", "", 0, 0, "", "", "", ""),
            queue.shuffling
        )

        fun next() = this@MusicService.next()
        fun prev() = this@MusicService.prev()
        fun play() = this@MusicService.play()
        fun pause() = this@MusicService.pause()
        fun shuffle() {
            queue.shufflePlaylist()
            notifyListeners("playlistUpdated", null)
        }

        fun playpause() {
            if (engine.isPlaying) pause() else play()
        }

        fun seek(position: Float) = engine.seek(position)

        fun seekToMs(positionMs: Long) {
            val duration = queue.currentTrack?.duration ?: return
            engine.seekToMs(positionMs, duration)
        }

        fun setVolume(volume: Int) = engine.setVolume(volume)
        fun playRadio(id: String) = CoroutineScope(IO).launch {
            commander.playRadio(id)
        }
        fun playAlbum(id: String, track: Int) = CoroutineScope(IO).launch {
            commander.playAlbum(id, track)
        }
        fun playPlaylist(id: String, track: Int) = commander.playPlaylist(id, track)
        fun setPlaylistAndPlay(playlist: Playlist, track: Int, seek: Float, playing: Boolean) =
            this@MusicService.setPlaylistAndPlay(playlist, track, seek, playing)
        fun skipTo(track: Int) = this@MusicService.skipTo(track)
        fun getPlaylist(): Playlist = queue.playlist
        fun playSearch(query: String, type: SearchType) = CoroutineScope(IO).launch {
            this@MusicService.playSearch(query, type)
        }
    }
}
