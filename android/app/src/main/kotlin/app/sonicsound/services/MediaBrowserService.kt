package app.sonicsound.services

import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.media.MediaBrowserServiceCompat
import app.sonicsound.App
import app.sonicsound.Globals
import app.sonicsound.KeyValueStorage
import app.sonicsound.KeyValueStorage.Companion.getActiveAccount
import app.sonicsound.R
import app.sonicsound.subsonic.SubsonicClient
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MediaBrowserService : MediaBrowserServiceCompat() {
    private val mediaSession: MediaSessionCompat? = Globals.GetMediaSession()
    private val subsonicClient: SubsonicClient = SubsonicClient(getActiveAccount())

    override fun onCreate() {
        super.onCreate()
        sessionToken = mediaSession!!.sessionToken
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        if (!isTrustedMediaBrowserClient(clientPackageName)) {
            return null
        }
        val extras = Bundle()
        extras.putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 2)
        return BrowserRoot("HOME", extras)
    }

    private fun isTrustedMediaBrowserClient(clientPackageName: String): Boolean {
        if (clientPackageName.isBlank()) return true
        return when (clientPackageName) {
            packageName,
            "app.sonicsound",
            "com.google.android.projection.gearhead",
            "com.google.android.gms",
            "com.android.systemui",
            "com.google.android.carassistant",
            "com.google.android.googlequicksearchbox",
            -> true
            else -> false
        }
    }

    private fun placeholderBitmap(): Bitmap {
        val future = Glide.with(App.context)
            .asBitmap()
            .load(R.drawable.ic_album_art_placeholder)
            .submit()
        return runBlocking(Dispatchers.IO) { future.get() }
    }

    fun getHome(): List<MediaBrowserCompat.MediaItem> {
        val builder = MediaDescriptionCompat.Builder()
        val ret = mutableListOf<MediaBrowserCompat.MediaItem>()
        val albumArtBitmap = placeholderBitmap()

        builder.setTitle("Random Songs")
        builder.setSubtitle("Rediscover your library!")
        builder.setIconBitmap(albumArtBitmap)
        builder.setMediaId("RANDOMSONGS")
        ret.add(
            MediaBrowserCompat.MediaItem(
                builder.build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            )
        )
        builder.setTitle("Most Played Albums")
        builder.setSubtitle("Jump back to your favourites")
        builder.setIconBitmap(albumArtBitmap)
        builder.setMediaId("MOSTPLAYED")
        ret.add(
            MediaBrowserCompat.MediaItem(
                builder.build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            )
        )
        builder.setTitle("Playlists")
        builder.setSubtitle("Listen to your curated playlists")
        builder.setIconBitmap(albumArtBitmap)
        builder.setMediaId("PLAYLISTS")
        ret.add(
            MediaBrowserCompat.MediaItem(
                builder.build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            )
        )
        return ret
    }

    override fun onLoadChildren(
        parentMediaId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        when {
            parentMediaId == "HOME" -> result.sendResult(getHome())
            parentMediaId == "RANDOMSONGS" -> loadCachedSongs(result)
            parentMediaId == "PLAYLISTS" -> loadCachedPlaylists(result)
            parentMediaId == "MOSTPLAYED" -> loadCachedAlbums(result)
            parentMediaId.startsWith("p") -> loadPlaylistTracks(parentMediaId.substring(1), result)
            else -> result.sendResult(emptyList())
        }
    }

    private fun loadCachedSongs(result: Result<List<MediaBrowserCompat.MediaItem>>) {
        if (getActiveAccount().username == null) {
            result.sendResult(emptyList())
            return
        }
        val songs = KeyValueStorage.getCachedSongs()
        CoroutineScope(Dispatchers.IO).launch { warmCaches(subsonicClient) }
        if (songs.isNotEmpty()) {
            runBlocking(Dispatchers.IO) {
                result.sendResult(subsonicClient.getSongsAsMediaItems(songs))
            }
        } else {
            result.sendResult(emptyList())
        }
    }

    private fun loadCachedPlaylists(result: Result<List<MediaBrowserCompat.MediaItem>>) {
        if (getActiveAccount().username == null) {
            result.sendResult(emptyList())
            return
        }
        val playlists = KeyValueStorage.getCachedPlaylists()
        CoroutineScope(Dispatchers.IO).launch { warmCaches(subsonicClient) }
        if (playlists.isNotEmpty()) {
            runBlocking(Dispatchers.IO) {
                result.sendResult(subsonicClient.getPlaylistsAsMediaItems(playlists))
            }
        } else {
            result.sendResult(emptyList())
        }
    }

    private fun loadCachedAlbums(result: Result<List<MediaBrowserCompat.MediaItem>>) {
        if (getActiveAccount().username == null) {
            result.sendResult(emptyList())
            return
        }
        val albums = KeyValueStorage.getCachedAlbums()
        CoroutineScope(Dispatchers.IO).launch { warmCaches(subsonicClient) }
        if (albums.isNotEmpty()) {
            runBlocking(Dispatchers.IO) {
                result.sendResult(subsonicClient.getAlbumsAsPlaylistsItems(albums))
            }
        } else {
            result.sendResult(emptyList())
        }
    }

    private fun loadPlaylistTracks(
        playlistId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        if (getActiveAccount().username == null) {
            result.sendResult(emptyList())
            return
        }
        result.detach()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val playlist = subsonicClient.getPlaylist(playlistId)
                val items = subsonicClient.getSongsAsMediaItems(playlist.entry.orEmpty())
                result.sendResult(items)
            } catch (e: Exception) {
                Log.e("MediaBrowser", e.message ?: "playlist load failed")
                result.sendResult(emptyList())
            }
        }
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        // Route through the same Assistant/search path as MediaSession.
        val focus = extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS)
        when {
            query.isBlank() -> Globals.NotifyObservers("SLPLAY", "")
            focus == MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE ->
                Globals.NotifyObservers(
                    "SLPLAYSEARCHARTIST",
                    extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST) ?: query
                )
            focus == MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> {
                val album = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM) ?: ""
                val artist = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST) ?: ""
                Globals.NotifyObservers("SLPLAYSEARCHALBUM", "$album $artist".trim())
            }
            else -> Globals.NotifyObservers(
                "SLPLAYSEARCH",
                query.replace("on sonicsound", "", ignoreCase = true).trim()
            )
        }
        result.sendResult(mutableListOf())
    }

    companion object {
        fun warmCaches(subsonicClient: SubsonicClient) {
            try {
                KeyValueStorage.setCachedSongs(subsonicClient.getRandomSongs())
                KeyValueStorage.setCachedAlbums(subsonicClient.getTopAlbums())
                val playlists = subsonicClient.getPlaylists()
                KeyValueStorage.setCachedPlaylists(
                    playlists.subList(0, minOf(playlists.size, 20))
                )
            } catch (e: Exception) {
                Log.e("MediaBrowser", e.message ?: "cache warm failed")
            }
        }
    }
}
