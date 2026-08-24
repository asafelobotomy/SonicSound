@file:Suppress("BlockingMethodInNonBlockingContext")

package app.sonicsound.subsonic

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.util.Log
import app.sonicsound.App
import app.sonicsound.models.Account
import app.sonicsound.models.Album
import app.sonicsound.models.AlbumWithSongs
import app.sonicsound.models.Artist
import app.sonicsound.models.Playlist
import app.sonicsound.models.SearchResult
import app.sonicsound.models.Song
import app.sonicsound.room.database.SonicSoundDatabase

/**
 * Public Subsonic client facade. Callers should import [app.sonicsound.subsonic.SubsonicClient].
 */
@SuppressLint("NewApi")
class SubsonicClient(var initialAccount: Account) {
    companion object {
        var account: Account = Account(null, "", "", "", false)
        var downloadQueue: MutableList<Song>
            get() = SubsonicDownloads.downloadQueue
            set(value) { SubsonicDownloads.downloadQueue = value }
        var downloadQueueForce: HashMap<String, Boolean>
            get() = SubsonicDownloads.downloadQueueForce
            set(value) { SubsonicDownloads.downloadQueueForce = value }
        var downloading: Boolean
            get() = SubsonicDownloads.downloading
            set(value) { SubsonicDownloads.downloading = value }
    }

    private val connectivityManager: ConnectivityManager =
        App.context.getSystemService(ConnectivityManager::class.java)

    @PublishedApi
    internal val http = SubsonicHttp { account }
    private val coverCache = SubsonicCoverCache(
        accountProvider = { account },
        paramsProvider = { SubsonicAuth.getBasicParams(account).asMap() }
    )
    private val library = SubsonicLibrary(
        http = http,
        coverCache = coverCache,
        accountProvider = { account },
        paramsProvider = { SubsonicAuth.getBasicParams(account).asMap() }
    )
    private val downloads = SubsonicDownloads(
        http = http,
        accountProvider = { account },
        paramsProvider = { SubsonicAuth.getBasicParams(account).asMap() },
        albumFetcher = { id -> library.getAlbum(id) },
        artistFetcher = { id -> library.getArtist(id) }
    )

    val client get() = http.client
    val db: SonicSoundDatabase get() = downloads.db

    init {
        account = initialAccount
    }

    fun getSongsAsMediaItems(songs: List<Song>): List<MediaBrowserCompat.MediaItem> {
        val builder = MediaDescriptionCompat.Builder()
        val ret = mutableListOf<MediaBrowserCompat.MediaItem>()
        for (item in songs) {
            builder.setTitle(item.title)
            builder.setSubtitle(String.format("by %s", item.artist))
            builder.setIconBitmap(
                coverCache.loadCoverBitmapOrPlaceholder(
                    item.coverArt ?: item.albumId,
                    getAlbumArt(item.coverArt ?: item.albumId)
                )
            )
            Log.i("BitmapMediaItem", "Loaded successfully")
            builder.setMediaId("s${item.id}")
            ret.add(
                MediaBrowserCompat.MediaItem(
                    builder.build(),
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                )
            )
        }
        return ret
    }

    fun getPlaylistsAsMediaItems(playlists: List<Playlist>): List<MediaBrowserCompat.MediaItem> {
        val builder = MediaDescriptionCompat.Builder()
        val ret = mutableListOf<MediaBrowserCompat.MediaItem>()
        for (item in playlists) {
            builder.setTitle(item.name)
            builder.setSubtitle(item.comment)
            val coverId = item.coverArt ?: ""
            builder.setIconBitmap(
                coverCache.loadCoverBitmapOrPlaceholder(coverId, getAlbumArt(coverId))
            )
            Log.i("BitmapMediaItem", "Loaded successfully")
            builder.setMediaId("p${item.id}")
            ret.add(
                MediaBrowserCompat.MediaItem(
                    builder.build(),
                    MediaBrowserCompat.MediaItem.FLAG_BROWSABLE or
                        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                )
            )
        }
        return ret
    }

    fun getAlbumsAsPlaylistsItems(albums: List<Album>): List<MediaBrowserCompat.MediaItem> {
        val builder = MediaDescriptionCompat.Builder()
        val ret = mutableListOf<MediaBrowserCompat.MediaItem>()
        for (item in albums) {
            builder.setTitle(item.name)
            builder.setSubtitle(String.format("by %s", item.artist))
            builder.setIconBitmap(
                coverCache.loadCoverBitmapOrPlaceholder(item.id, getAlbumArt(item.id))
            )
            Log.i("BitmapMediaItem", "Loaded successfully")
            builder.setMediaId("a${item.id}")
            ret.add(
                MediaBrowserCompat.MediaItem(
                    builder.build(),
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                )
            )
        }
        return ret
    }

    fun isCached(id: String): Boolean = downloads.isCached(id)
    fun getLocalSongUri(id: String): String = downloads.getLocalSongUri(id)
    fun getLocalArtists(): List<Artist> = downloads.getLocalArtists()
    fun getLocalArtist(id: String): Artist? = downloads.getLocalArtist(id)
    fun getLocalArtistWithAlbums(id: String): Artist? = downloads.getLocalArtistWithAlbums(id)
    fun getLocalAlbums(take: Int, sortedByDate: Boolean = false): List<Album> =
        downloads.getLocalAlbums(take, sortedByDate)
    fun getLocalAlbumWithSongs(id: String): AlbumWithSongs? = downloads.getLocalAlbumWithSongs(id)

    inline fun <reified T : Any> makeSubsonicRequest(
        path: List<String>,
        parameters: HashMap<String, String>?,
        emptyResponse: Boolean = false
    ): T? = http.makeSubsonicRequest(path, parameters, emptyResponse)

    fun search(query: String): SearchResult = library.search(query)
    fun getArtists(): List<Artist> = library.getArtists()
    fun getArtist(id: String): Artist = library.getArtist(id)
    fun getAlbums(): List<Album> = library.getAlbums()
    fun getAlbum(id: String): AlbumWithSongs = library.getAlbum(id)
    fun getTopAlbums(type: String = "frequent", size: Int = 10): List<Album> =
        library.getTopAlbums(type, size)
    fun getAlbumArt(id: String): String = coverCache.getAlbumArt(id)
    fun getSpotifyToken(): String = library.getSpotifyToken()
    fun scrobble(id: String) = library.scrobble(id)
    fun getArtistArt(id: String): String = library.getArtistArt(id)
    fun getLocalAlbumArt(id: String): String = coverCache.getLocalAlbumArt(id)
    fun getLocalArtistArt(id: String): String = coverCache.getLocalArtistArt(id)
    fun getRandomSongs(): List<Song> = library.getRandomSongs()

    fun getSongUri(song: Song?): String? {
        val metered = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
        return library.getSongUri(song, metered)
    }

    fun getSimilarSongs(id: String): List<Song> = library.getSimilarSongs(id)
    fun getSong(id: String): Song = library.getSong(id)
    fun getPlaylist(id: String): Playlist = library.getPlaylist(id)
    fun getPlaylists(): List<Playlist> = library.getPlaylists()
    fun removePlaylist(id: String) = library.removePlaylist(id)
    fun removeFromPlaylist(id: String, track: Int) = library.removeFromPlaylist(id, track)
    fun addToPlaylist(id: String, songId: String) = library.addToPlaylist(id, songId)
    fun updatePlaylist(playlist: Playlist): Playlist = library.updatePlaylist(playlist)
    fun createPlaylist(ids: List<String>, name: String): Playlist =
        library.createPlaylist(ids, name)

    fun login(username: String, password: String, url: String, usePlaintext: Boolean): Account {
        account = SubsonicAuth.login(client, username, password, url, usePlaintext)
        return account
    }

    fun ping(): Boolean = SubsonicAuth.ping(client, account)

    fun downloadPlaylist(playlist: List<Song>, force: Boolean) =
        downloads.downloadPlaylist(playlist, force)

    fun getCoverCacheSizeBytes(): Long = coverCache.getCacheSizeBytes()
    fun clearCoverCache(): Long = coverCache.clearCache()
    fun getLyrics(artist: String, title: String): String =
        library.getLyrics(artist, title)
}

