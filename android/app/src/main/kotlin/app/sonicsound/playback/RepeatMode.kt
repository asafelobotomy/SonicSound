package app.sonicsound.playback

/** Repeat off → repeat queue → repeat current track. */
enum class RepeatMode {
    OFF,
    ALL,
    ONE;

    fun cycle(): RepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }

    companion object {
        fun fromWire(value: String?): RepeatMode = when (value?.lowercase()) {
            "all", "queue" -> ALL
            "one", "track" -> ONE
            else -> OFF
        }
    }

    fun toWire(): String = when (this) {
        OFF -> "off"
        ALL -> "all"
        ONE -> "one"
    }
}
