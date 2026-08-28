package app.sonicsound

import app.sonicsound.models.Song

class CurrentState(
    val playing: Boolean,
    val playtime: Float,
    val currentTrack: Song,
    val shuffling: Boolean,
    val repeatMode: String = "off",
)