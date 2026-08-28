package app.sonicsound.playback

import app.sonicsound.models.JukeboxCollection
import app.sonicsound.models.Playlist
import app.sonicsound.models.Song
import app.sonicsound.services.MusicService

/** Current playlist, shuffle state, and track navigation. */
class PlayQueue {
    var playlist: Playlist = MusicService.getDefaultPlaylist()
    var originalPlaylist: List<Song> = listOf()
    var currentTrack: Song? = null
    var shuffling: Boolean = false
    var collection: JukeboxCollection? = null

    private fun entries(): List<Song> = playlist.entry.orEmpty()

    fun shufflePlaylist() {
        if (shuffling) {
            playlist.entry = originalPlaylist.toList()
        } else {
            playlist.entry = shuffle(entries())
        }
        shuffling = !shuffling
    }

    fun shuffle(list: List<Song>): List<Song> {
        val ret = list.shuffled().toMutableList()
        if (currentTrack != null) {
            val index = ret.indexOf(currentTrack)
            if (index >= 0) {
                ret[index] = ret[0]
                ret[0] = currentTrack!!
            }
        }
        return ret
    }

    fun skipTo(track: Int): Song {
        val list = entries()
        if (track < 0 || track >= list.size) {
            throw Exception("Track does not exist on playlist")
        }
        currentTrack = list[track]
        return currentTrack!!
    }

    fun next(): Song? {
        val list = entries()
        val idx = list.indexOf(currentTrack)
        if (idx >= 0 && idx < list.size - 1) {
            currentTrack = list[idx + 1]
            return currentTrack
        }
        return null
    }

    fun prev(): Song? {
        val list = entries()
        val idx = list.indexOf(currentTrack)
        if (idx > 0) {
            currentTrack = list[idx - 1]
            return currentTrack
        }
        return null
    }

    fun songsDuration(songs: List<Song>): Int = songs.sumOf { s -> s.duration }

    fun remainingCount(): Int {
        val list = entries()
        val idx = list.indexOf(currentTrack)
        if (idx < 0) return list.size
        return (list.size - idx - 1).coerceAtLeast(0)
    }

    fun appendEntries(songs: List<Song>) {
        if (songs.isEmpty()) return
        val current = playlist.entry.orEmpty().toMutableList()
        current.addAll(songs)
        playlist.entry = current
        originalPlaylist = current.toList()
    }

    fun setEntries(newPlaylist: Playlist, track: Song) {
        playlist = newPlaylist
        originalPlaylist = newPlaylist.entry.orEmpty().toList()
        currentTrack = track
    }

    fun reset() {
        playlist = MusicService.getDefaultPlaylist()
        collection = null
    }
}
