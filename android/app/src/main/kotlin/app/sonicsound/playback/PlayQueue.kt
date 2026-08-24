package app.sonicsound.playback

import app.sonicsound.models.Playlist
import app.sonicsound.models.Song
import app.sonicsound.services.MusicService

/** Current playlist, shuffle state, and track navigation. */
class PlayQueue {
    var playlist: Playlist = MusicService.getDefaultPlaylist()
    var originalPlaylist: List<Song> = listOf()
    var currentTrack: Song? = null
    var shuffling: Boolean = false

    fun shufflePlaylist() {
        if (shuffling) {
            playlist.entry = originalPlaylist.toList()
        } else {
            playlist.entry = shuffle(playlist.entry)
        }
        shuffling = !shuffling
    }

    fun shuffle(list: List<Song>): List<Song> {
        val ret = list.shuffled().toMutableList()
        if (currentTrack != null) {
            val index = ret.indexOf(currentTrack)
            ret[index] = ret[0]
            ret[0] = currentTrack!!
        }
        return ret
    }

    fun skipTo(track: Int): Song {
        if (track >= playlist.entry.size) {
            throw Exception("Track does not exist on playlist")
        }
        currentTrack = playlist.entry[track]
        return currentTrack!!
    }

    fun next(): Song? {
        if (playlist.entry.indexOf(currentTrack) < playlist.entry.size - 1) {
            currentTrack = playlist.entry[playlist.entry.indexOf(currentTrack) + 1]
            return currentTrack
        }
        return null
    }

    fun prev(): Song? {
        if (playlist.entry.indexOf(currentTrack) > 0) {
            currentTrack = playlist.entry[playlist.entry.indexOf(currentTrack) - 1]
            return currentTrack
        }
        return null
    }

    fun songsDuration(songs: List<Song>): Int = songs.sumOf { s -> s.duration }

    fun setEntries(newPlaylist: Playlist, track: Song) {
        playlist = newPlaylist
        originalPlaylist = newPlaylist.entry.toList()
        currentTrack = track
    }

    fun reset() {
        playlist = MusicService.getDefaultPlaylist()
    }
}
