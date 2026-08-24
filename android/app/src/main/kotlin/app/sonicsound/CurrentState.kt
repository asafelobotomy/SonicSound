package app.sonicsound

import app.sonicsound.models.Song

class CurrentState(
    val playing: Boolean,
    val position: Float,
    val currentTrack: Song,
    val shuffling: Boolean
)