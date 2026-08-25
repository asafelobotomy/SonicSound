package app.sonicsound.models

class Settings(
    val cacheSize: Int = 0,
    val transcoding: String = "",
    val eqEnabled: Boolean = false,
    val replayGainEnabled: Boolean = false,
)
