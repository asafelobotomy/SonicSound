package app.sonicsound.playback

import android.app.SearchManager
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.MediaStore
import app.sonicsound.App
import app.sonicsound.Constants
import app.sonicsound.Globals
import app.sonicsound.KeyValueStorage
import app.sonicsound.KeyValueStorage.Companion.getActiveAccount
import app.sonicsound.models.Playlist
import app.sonicsound.models.SearchType
import app.sonicsound.models.Song
import app.sonicsound.subsonic.SubsonicClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch

/** Intent / broadcast command routing and radio/album/playlist start helpers. */
class PlaybackCommander(
    private val subsonicClient: SubsonicClient,
    private val connectivityManager: ConnectivityManager,
    private val queue: PlayQueue,
    private val engine: VlcEngine,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrev: () -> Unit,
    private val onCancel: () -> Unit,
    private val playSearch: (String, SearchType) -> Unit
) {
    fun handleStartAction(intent: Intent) {
        when (intent.action) {
            Constants.SERVICE_PLAY_PAUSE ->
                if (engine.isPlaying) onPause() else onPlay()
            Constants.SERVICE_NEXT -> onNext()
            Constants.SERVICE_PREV -> onPrev()
            Constants.SERVICE_PLAY_ALBUM -> {
                val id = intent.extras?.getString("id")
                val track = intent.extras?.getInt("track")
                if (id != null && track != null) playAlbum(id, track)
            }
            Constants.SERVICE_PLAY_RADIO -> {
                val id = intent.extras?.getString("id")
                if (id != null) playRadio(id)
            }
            Constants.SERVICE_PLAY_PLAYLIST -> {
                val id = intent.extras?.getString("id")
                val track = intent.extras?.getInt("track")
                if (id != null) playPlaylist(id, track ?: 0)
            }
            Constants.SERVICE_PLAY_SEARCH ->
                intent.extras?.getString("query")?.let { playSearch(it, SearchType.SONG) }
            Constants.SERVICE_PLAY_SEARCH_ALBUM ->
                intent.extras?.getString("query")?.let { playSearch(it, SearchType.ALBUM) }
            Constants.SERVICE_PLAY_SEARCH_ARTIST ->
                intent.extras?.getString("query")?.let { playSearch(it, SearchType.ARTIST) }
            MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH -> handleMediaSearch(intent)
        }
    }

    fun handleBroadcast(action: String, value: String?) {
        when (action) {
            "SLPLAY", "SLPAUSE" -> if (engine.isPlaying) onPause() else onPlay()
            "SLPREV" -> onPrev()
            "SLNEXT" -> onNext()
            "SLPLAYID" -> CoroutineScope(Dispatchers.IO).launch {
                val id = value!!.subSequence(1, value.length).toString()
                when (value.subSequence(0, 1)) {
                    "s" -> playRadio(id)
                    "a" -> playAlbum(id, 0)
                    "p" -> playPlaylist(id, 0)
                }
            }
            "SLPLAYSEARCH" -> CoroutineScope(IO).launch {
                playSearch(value!!, SearchType.SONG)
            }
            "SLPLAYSEARCHARTIST" -> CoroutineScope(IO).launch {
                playSearch(value!!, SearchType.ARTIST)
            }
            "SLPLAYSEARCHALBUM" -> CoroutineScope(IO).launch {
                playSearch(value!!, SearchType.ALBUM)
            }
            "SLCANCEL" -> onCancel()
        }
    }

    private fun handleMediaSearch(intent: Intent) {
        val mediaFocus: String? = intent.getStringExtra(MediaStore.EXTRA_MEDIA_FOCUS)
        val query: String? = intent.getStringExtra(SearchManager.QUERY)
        val album: String? = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM)
        val artist: String? = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST)
        val title: String? = intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE)
        if (query == null) return
        when {
            mediaFocus == null -> playSearch(query, SearchType.SONG)
            mediaFocus.compareTo("vnd.android.cursor.item/*") == 0 -> {
                if (query.isNotEmpty()) playSearch(query, SearchType.SONG) else onPlay()
            }
            mediaFocus.compareTo(MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE) == 0 ->
                playSearch(artist!!, SearchType.ARTIST)
            mediaFocus.compareTo(MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE) == 0 ->
                playSearch("$album $artist", SearchType.SONG)
            mediaFocus.compareTo("vnd.android.cursor.item/audio") == 0 ->
                playSearch("$album $artist $title", SearchType.SONG)
        }
    }

    private fun maybePrefetch(songs: List<Song>) {
        if (connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
            && !KeyValueStorage.getOfflineMode()
            && !App.isTv
        ) {
            subsonicClient.downloadPlaylist(songs, false)
        }
    }

    fun playRadio(id: String) {
        queue.reset()
        try {
            val songs: MutableList<Song> = mutableListOf()
            songs.addAll(subsonicClient.getSimilarSongs(id))
            songs.add(0, subsonicClient.getSong(id))
            val pl = Playlist(
                "current",
                "Internet radio based on ${songs[0].title}",
                "by ${songs[0].artist}",
                getActiveAccount().username!!,
                false,
                songs.size,
                queue.songsDuration(songs),
                "",
                songs[0].albumId,
                songs
            )
            queue.setEntries(pl, songs[0])
            maybePrefetch(songs)
            engine.loadMedia(queue.currentTrack!!)
            onPlay()
        } catch (e: Exception) {
            Globals.NotifyObservers("EX", e.message)
        }
    }

    fun playPlaylist(id: String, track: Int) {
        queue.reset()
        try {
            val pl = subsonicClient.getPlaylist(id)
            queue.setEntries(pl, pl.entry.orEmpty()[track])
            maybePrefetch(pl.entry.orEmpty())
            engine.loadMedia(queue.currentTrack!!)
            onPlay()
        } catch (e: Exception) {
            Globals.NotifyObservers("EX", e.message)
        }
    }

    fun playAlbum(id: String, track: Int) {
        queue.reset()
        try {
            val songs: MutableList<Song> = mutableListOf()
            songs.addAll(
                if (KeyValueStorage.getOfflineMode()) {
                    subsonicClient.getLocalAlbumWithSongs(id)!!.song
                } else {
                    subsonicClient.getAlbum(id).song
                }
            )
            val pl = Playlist(
                "current",
                songs[0].album,
                "by ${songs[0].artist}",
                getActiveAccount().username!!,
                false,
                songs.size,
                queue.songsDuration(songs),
                "",
                songs[0].albumId,
                songs
            )
            queue.setEntries(pl, songs[track])
            maybePrefetch(songs)
            engine.loadMedia(queue.currentTrack!!)
            onPlay()
        } catch (e: Exception) {
            Globals.NotifyObservers("EX", e.message)
        }
    }

    fun playInternetRadio(streamUrl: String, name: String) {
        queue.reset()
        try {
            val song = Song(
                id = "radio:${name.hashCode()}",
                parent = streamUrl,
                title = name,
                duration = 0,
                track = 0,
                artist = "Internet Radio",
                album = name,
                albumId = "",
                coverArt = ""
            )
            val songs = listOf(song)
            val pl = Playlist(
                "current",
                name,
                "Internet Radio",
                getActiveAccount().username ?: "guest",
                false,
                1,
                0,
                "",
                "",
                songs
            )
            queue.setEntries(pl, song)
            engine.loadStreamUrl(streamUrl)
            onPlay()
        } catch (e: Exception) {
            Globals.NotifyObservers("EX", e.message)
        }
    }
}
