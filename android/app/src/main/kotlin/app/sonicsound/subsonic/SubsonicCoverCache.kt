package app.sonicsound.subsonic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import app.sonicsound.App
import app.sonicsound.Globals
import app.sonicsound.Helpers
import app.sonicsound.R
import app.sonicsound.models.Account
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Cover/artist art download paths, disk cache, and TV cache helpers.
 */
class SubsonicCoverCache(
    private val accountProvider: () -> Account,
    private val paramsProvider: () -> HashMap<String, String>
) {
    private val account: Account
        get() = accountProvider()

    fun getCoverArtsDirectory(): String {
        val uri = Uri.parse(account.url)
        return Helpers.constructPath(
            listOf(App.context.filesDir.path, "${uri.authority}/albumArts/")
        )
    }

    fun getArtistArtsDirectory(): String {
        val uri = Uri.parse(account.url)
        return Helpers.constructPath(
            listOf(App.context.filesDir.path, "${uri.authority}/artistArts/")
        )
    }

    fun getLocalCoverArtUri(id: String, size: Int? = null): String {
        val name = if (size != null && size > 0) "${id}_s$size.png" else "$id.png"
        return Helpers.constructPath(listOf(getCoverArtsDirectory(), name))
    }

    fun getLocalArtistArtUri(id: String): String =
        Helpers.constructPath(listOf(getArtistArtsDirectory(), "$id.png"))

    fun getAlbumArtUrl(id: String, size: Int? = null): String {
        val uriBuilder = Uri.parse(account.url).buildUpon()
            .appendPath("rest")
            .appendPath("getCoverArt")
        for ((key, value) in paramsProvider()) {
            uriBuilder.appendQueryParameter(key, value)
        }
        uriBuilder.appendQueryParameter("id", id)
        if (size != null && size > 0) {
            uriBuilder.appendQueryParameter("size", size.toString())
        }
        return uriBuilder.build().toString()
    }

    /** Returns remote cover URL and caches the image to disk asynchronously. */
    fun getAlbumArt(id: String, size: Int? = null): String {
        val url = getAlbumArtUrl(id, size)
        val localPath = getLocalCoverArtUri(id, size)
        CoroutineScope(IO).launch {
            try {
                cacheDownload(url, getCoverArtsDirectory(), localPath)
            } catch (e: Exception) {
                Log.e("Image saver", e.message ?: "cache failed")
                Globals.NotifyObservers("EX", e.message)
            }
        }
        return url
    }

    fun cacheRemoteImage(url: String, directory: String, localPath: String) {
        CoroutineScope(IO).launch {
            try {
                cacheDownload(url, directory, localPath)
            } catch (e: Exception) {
                Log.e("Image saver", e.message ?: "cache failed")
                Globals.NotifyObservers("EX", e.message)
            }
        }
    }

    private fun cacheDownload(url: String, directory: String, localPath: String) {
        synchronized(cacheLock) {
            if (File(localPath).exists()) return
            Log.i("Image saver", "Fetching image $localPath")
            val bitmap = try {
                Glide.with(App.context)
                    .asBitmap()
                    .load(url)
                    .submit()
                    .get()
            } catch (e: Exception) {
                Glide.with(App.context)
                    .asBitmap()
                    .load(R.drawable.ic_album_art_placeholder)
                    .submit()
                    .get()
            }
            saveImage(bitmap, directory, localPath)
        }
    }

    fun getLocalAlbumArtFile(id: String): File {
        val file = File(getLocalCoverArtUri(id))
        if (!file.exists()) {
            throw Exception("There isn't a cached version of this cover art.")
        }
        return file
    }

    fun getLocalAlbumArt(id: String): String =
        "data:image/png;base64,${
            Base64.encodeToString(getLocalAlbumArtFile(id).readBytes(), Base64.NO_WRAP)
        }"

    fun getLocalArtistArt(id: String): String {
        val file = File(getLocalArtistArtUri(id))
        if (!file.exists()) {
            throw Exception("There isn't a cached version of this artist art.")
        }
        return "data:image/png;base64,${Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)}"
    }

    fun loadCoverBitmapOrPlaceholder(id: String, remoteUrl: String): Bitmap {
        val local = File(getLocalCoverArtUri(id))
        if (local.exists()) {
            Log.i("BitmapMediaItem", "Loading from disk")
            BitmapFactory.decodeFile(local.absolutePath)?.let { return it }
        }
        Log.i("BitmapMediaItem", "Loading from server")
        return try {
            Glide.with(App.context).asBitmap().load(Uri.parse(remoteUrl)).submit().get()
        } catch (e: Exception) {
            Glide.with(App.context)
                .asBitmap()
                .load(R.drawable.ic_album_art_placeholder)
                .submit()
                .get()
        }
    }

    /** Total size in bytes of cached cover + artist art for the active account. */
    fun getCacheSizeBytes(): Long {
        return dirSize(File(getCoverArtsDirectory())) + dirSize(File(getArtistArtsDirectory()))
    }

    /** Deletes cached cover and artist art for the active account. Returns bytes freed. */
    fun clearCache(): Long {
        val freed = getCacheSizeBytes()
        deleteDirContents(File(getCoverArtsDirectory()))
        deleteDirContents(File(getArtistArtsDirectory()))
        return freed
    }

    companion object {
        private val cacheLock = Any()

        fun saveImage(image: Bitmap, directory: String, path: String) {
            val storageDir = File(directory)
            var success = true
            if (!storageDir.exists()) {
                success = storageDir.mkdirs()
            }
            if (success) {
                val imageFile = File(path)
                try {
                    val fOut: OutputStream = FileOutputStream(imageFile)
                    image.compress(Bitmap.CompressFormat.PNG, 100, fOut)
                    fOut.flush()
                    fOut.close()
                    Log.i("Image save", "image successfully saved")
                } catch (e: Exception) {
                    Log.e("Image saver", e.message ?: "save failed")
                    Globals.NotifyObservers("EX", e.message)
                }
            }
        }

        private fun dirSize(dir: File): Long {
            if (!dir.exists()) return 0L
            return dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        }

        private fun deleteDirContents(dir: File) {
            if (!dir.exists()) return
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    deleteDirContents(file)
                    file.delete()
                } else {
                    file.delete()
                }
            }
        }
    }
}
