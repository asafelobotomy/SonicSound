package app.sonicsound.playback

import app.sonicsound.KeyValueStorage.Companion.getActiveAccount
import app.sonicsound.models.JukeboxCollection
import app.sonicsound.models.Playlist
import app.sonicsound.models.Song
import app.sonicsound.subsonic.SubsonicClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fetches and refills songs for Jukebox Collections. */
class JukeboxEngine(
    private val subsonicClient: SubsonicClient,
) {
    private val recentIds = ArrayDeque<String>(100)
    private var genreOffset = 0

    suspend fun fetchBatch(collection: JukeboxCollection, batchSize: Int = 50): List<Song> =
        withContext(Dispatchers.IO) {
            val raw = when (collection) {
                is JukeboxCollection.Random ->
                    subsonicClient.getRandomSongsFiltered(batchSize)
                is JukeboxCollection.Genre -> {
                    val songs = subsonicClient.getSongsByGenre(collection.genre, batchSize, genreOffset)
                    genreOffset += songs.size
                    if (songs.size < batchSize) {
                        genreOffset = 0
                        songs + subsonicClient.getRandomSongsFiltered(batchSize - songs.size, genre = collection.genre)
                    } else songs
                }
                is JukeboxCollection.Artist -> fetchArtistSongs(collection.artistId)
                is JukeboxCollection.Decade ->
                    subsonicClient.getRandomSongsFiltered(
                        batchSize,
                        fromYear = collection.fromYear,
                        toYear = collection.toYear,
                    )
                is JukeboxCollection.Similar -> {
                    val seed = collection.currentSeedId
                    val seedSong = subsonicClient.getSong(seed)
                    listOf(seedSong) + subsonicClient.getSimilarSongs(seed)
                }
                is JukeboxCollection.Starred ->
                    subsonicClient.getStarred2Songs().shuffled().take(batchSize)
                is JukeboxCollection.ServerPlaylist ->
                    subsonicClient.getPlaylist(collection.playlistId).entry.orEmpty()
            }
            dedupe(raw).take(batchSize)
        }

    fun onTrackPlayed(songId: String, collection: JukeboxCollection) {
        if (recentIds.size >= 100) recentIds.removeFirst()
        recentIds.addLast(songId)
        if (collection is JukeboxCollection.Similar) {
            collection.currentSeedId = songId
        }
    }

    fun resetOffsets() {
        genreOffset = 0
    }

    private fun fetchArtistSongs(artistId: String): List<Song> {
        val artist = subsonicClient.getArtist(artistId)
        val songs = mutableListOf<Song>()
        artist.album.orEmpty().forEach { album ->
            try {
                songs.addAll(subsonicClient.getAlbum(album.id).song.orEmpty())
            } catch (_: Exception) {
                // skip
            }
        }
        return songs.shuffled()
    }

    private fun dedupe(songs: List<Song>): List<Song> {
        val seen = recentIds.toSet()
        val out = mutableListOf<Song>()
        val batchSeen = mutableSetOf<String>()
        for (song in songs) {
            if (song.id in seen || song.id in batchSeen) continue
            batchSeen.add(song.id)
            out.add(song)
        }
        return out.ifEmpty { songs.distinctBy { it.id } }
    }

    fun buildPlaylist(collection: JukeboxCollection, songs: List<Song>): Playlist {
        val name = "Jukebox · ${collection.label}"
        return Playlist(
            "",
            name,
            null,
            getActiveAccount().username ?: "",
            false,
            songs.size,
            songs.sumOf { it.duration },
            "",
            null,
            songs,
        )
    }
}
