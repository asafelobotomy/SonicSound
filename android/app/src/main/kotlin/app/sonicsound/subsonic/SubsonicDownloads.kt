package app.sonicsound.subsonic

import android.net.Uri
import android.util.Log
import app.sonicsound.App
import app.sonicsound.Globals
import app.sonicsound.Helpers
import app.sonicsound.KeyValueStorage
import app.sonicsound.models.Account
import app.sonicsound.models.Album
import app.sonicsound.models.AlbumWithSongs
import app.sonicsound.models.Artist
import app.sonicsound.models.Song
import app.sonicsound.room.database.SonicSoundDatabase
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import okhttp3.Request
import okio.Buffer
import okio.buffer
import okio.sink
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Local Room registration, song download queue, and on-disk song cache.
 */
class SubsonicDownloads(
    private val http: SubsonicHttp,
    private val accountProvider: () -> Account,
    private val paramsProvider: () -> HashMap<String, String>,
    private val albumFetcher: (String) -> Album,
    private val artistFetcher: (String) -> Artist
) {
    val db: SonicSoundDatabase

    init {
        val account = accountProvider()
        val authority = if (account.url != "") Uri.parse(account.url).authority else ""
        db = Room.databaseBuilder(
            App.context,
            SonicSoundDatabase::class.java,
            "sonicsound$authority"
        ).build()
    }

    private val account: Account
        get() = accountProvider()

    fun getSongsDirectory(): String {
        val uri = Uri.parse(account.url)
        return "${uri.authority}/songs/"
    }

    fun getLocalSongUri(id: String): String =
        Helpers.constructPath(listOf(App.context.filesDir.path, getSongsDirectory(), id))

    fun isCached(id: String): Boolean = File(getLocalSongUri(id)).exists()

    fun getLocalArtists(): List<Artist> = db.artistDao().getAll()
    fun getLocalArtist(id: String): Artist? = db.artistDao().get(id)

    fun getLocalArtistWithAlbums(id: String): Artist? {
        val artist = getLocalArtist(id) ?: return null
        artist.album = db.albumDao().getByArtist(id).sortedBy { s -> s.year }
        artist.albumCount = artist.album.size
        return artist
    }

    fun getLocalAlbums(take: Int, sortedByDate: Boolean = false): List<Album> =
        db.albumDao().getAll().take(take)
            .sortedBy { s -> if (sortedByDate) s.created else s.name }

    private fun getLocalAlbum(id: String): Album? = db.albumDao().get(id)

    fun getLocalAlbumWithSongs(id: String): AlbumWithSongs? {
        val album: Album = getLocalAlbum(id) ?: return null
        val ret = AlbumWithSongs(album)
        ret.song = db.songDao().getByAlbum(id).sortedBy { s -> s.track }
        return ret
    }

    fun unregisterSong(id: String) {
        val s = db.songDao().get(id) ?: return
        val albumId = s.albumId
        try {
            db.songDao().delete(s)
        } catch (e: Exception) {
            Globals.NotifyObservers("EX", e.message)
            return
        }
        checkAndUnregisterAlbum(albumId)
    }

    private fun checkAndUnregisterAlbum(id: String) {
        val a = db.albumDao().get(id) ?: return
        if (db.songDao().getByAlbum(id).isEmpty()) {
            val artistId = a.artistId
            try {
                db.albumDao().delete(a)
            } catch (e: Exception) {
                Globals.NotifyObservers("EX", e.message)
                return
            }
            checkAndUnregisterArtist(artistId)
        }
    }

    private fun checkAndUnregisterArtist(id: String) {
        val a = db.artistDao().get(id) ?: return
        if (db.albumDao().getByArtist(id).isEmpty()) {
            try {
                db.artistDao().delete(a)
            } catch (e: Exception) {
                Globals.NotifyObservers("EX", e.message)
            }
        }
    }

    fun registerSong(song: Song) {
        if (db.songDao().get(song.id) == null) {
            Log.i("LocalCacheDB", "Registering song ${song.id}")
            try {
                db.songDao().insert(song)
            } catch (e: Exception) {
                Globals.NotifyObservers("EX", e.message)
            }
        }
        if (db.albumDao().get(song.albumId) == null) {
            registerAlbum(albumFetcher(song.albumId))
        }
    }

    private fun registerAlbum(album: Album) {
        if (db.albumDao().get(album.id) == null) {
            album.created =
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            try {
                db.albumDao().insert(album)
            } catch (e: Exception) {
                Globals.NotifyObservers("EX", e.message)
            }
            Log.i("LocalCacheDB", "Registering album ${album.id}")
        }
        if (db.artistDao().get(album.artistId) == null) {
            registerArtist(artistFetcher(album.artistId))
        }
    }

    private fun registerArtist(artist: Artist) {
        if (db.artistDao().get(artist.id) == null) {
            try {
                db.artistDao().insert(artist)
            } catch (e: Exception) {
                Globals.NotifyObservers("EX", e.message)
            }
            Log.i("LocalCacheDB", "Registering artist ${artist.id}")
        }
    }

    private fun getSongDownload(id: String): String {
        val uriBuilder = Uri.parse(account.url).buildUpon()
            .appendPath("rest")
            .appendPath("download")
        for ((key, value) in paramsProvider()) {
            uriBuilder.appendQueryParameter(key, value)
        }
        uriBuilder.appendQueryParameter("id", id)
        return uriBuilder.build().toString()
    }

    fun downloadSong(id: String) {
        try {
            val request: Request = Request.Builder().url(getSongDownload(id)).build()
            Log.i("SonicSound", "Downloading song $id")
            val response = http.execute(request)
            if (!response.isSuccessful) {
                Globals.NotifyObservers("EX", response.message)
                return
            }
            val dirPath =
                Helpers.constructPath(listOf(App.context.filesDir.path, getSongsDirectory()))
            val dir = File(dirPath)
            if (!dir.exists()) dir.mkdirs()
            val file = File(getLocalSongUri(id))
            val body = response.body!!
            val contentLength = body.contentLength()
            val source = body.source()
            val sink = file.sink().buffer()
            val sinkBuffer: Buffer = sink.buffer
            var totalBytesRead: Long = 0
            val bufferSize: Long = 8 * 1024
            var bytesRead: Long
            var time = LocalDateTime.now()
            while (source.read(sinkBuffer, bufferSize).also { bytesRead = it } != -1L) {
                sink.emit()
                totalBytesRead += bytesRead
                val progress = (totalBytesRead * 100 / contentLength).toInt()
                if (Duration.between(time, LocalDateTime.now()).toMillis() > 200) {
                    time = LocalDateTime.now()
                    Globals.NotifyObservers("MSprogress$id", "{\"progress\":$progress}")
                }
            }
            Globals.NotifyObservers("MSprogress$id", "{\"progress\":100}")
            sink.flush()
            sink.close()
        } catch (e: Exception) {
            Globals.NotifyObservers("EX", e.message)
            if (File(getLocalSongUri(id)).exists()) {
                File(getLocalSongUri(id)).delete()
            }
        }
    }

    companion object {
        var downloadQueue: MutableList<Song> = mutableListOf()
        var downloadQueueForce: HashMap<String, Boolean> = HashMap()
        var downloading: Boolean = false
    }

    fun downloadPlaylist(playlist: List<Song>, force: Boolean) {
        downloadQueue.addAll(playlist)
        playlist.forEach { downloadQueueForce[it.id] = force }
        if (!downloading) download(true)
    }

    private fun download(spawn: Boolean) {
        if (downloadQueue.size > 0) {
            downloading = true
            val index = if (spawn) 2 else 0
            for (i in 0..index) {
                CoroutineScope(IO).launch {
                    Thread.sleep(i.toLong() * 500)
                    if (downloadQueue.size == 0) return@launch
                    val song = downloadQueue[0]
                    val force = downloadQueueForce[song.id] ?: false
                    downloadQueueForce.remove(downloadQueue[0].id)
                    downloadQueue.removeAt(0)
                    if (KeyValueStorage.getSettings().cacheSize > 0) {
                        val dir = File(
                            Helpers.constructPath(
                                listOf(App.context.filesDir.path, getSongsDirectory())
                            )
                        )
                        if (dir.exists()) {
                            val files = dir.listFiles()?.toList()
                                ?.sortedBy { file -> file.lastModified() }?.toMutableList()
                                ?: mutableListOf()
                            var size = files.sumOf { it.length() }
                            while (size > KeyValueStorage.getSettings().cacheSize * (1024L * 1024 * 1024)) {
                                if (files.isEmpty()) break
                                val victim = files.removeAt(0)
                                victim.delete()
                                unregisterSong(victim.name)
                                size = files.sumOf { it.length() }
                            }
                        }
                    }
                    if (!File(getLocalSongUri(song.id)).exists() || force) {
                        registerSong(song)
                        downloadSong(song.id)
                    }
                    download(false)
                }
            }
        } else {
            downloading = false
        }
    }
}
