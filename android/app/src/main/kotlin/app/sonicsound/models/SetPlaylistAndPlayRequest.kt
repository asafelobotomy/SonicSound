package app.sonicsound.models

class SetPlaylistAndPlayRequest(val playlist: Playlist, val track: Int, val seek: Float, val playing: Boolean)