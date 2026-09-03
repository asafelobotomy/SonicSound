package app.sonicsound

/**
 * Typed app-wide events. Prefer [AppEvents.emit] over raw [Globals.NotifyObservers]
 * for new call sites; [AppEvents] dual-writes legacy string actions so existing
 * string observers keep working during the migration.
 */
sealed interface AppEvent {
    data object PlaybackPlay : AppEvent
    data object PlaybackPaused : AppEvent
    data class Progress(val time: Float) : AppEvent
    data class Error(val message: String?) : AppEvent
    data class WebsocketConnected(val connected: Boolean) : AppEvent
    data class WebsocketLogin(val data: String) : AppEvent
    data class CurrentTrack(val json: String) : AppEvent
    data object PlaylistUpdated : AppEvent
}

object AppEvents {
    fun emit(event: AppEvent) {
        val (action, value) = when (event) {
            is AppEvent.PlaybackPlay -> "MSplay" to null
            is AppEvent.PlaybackPaused -> "MSpaused" to null
            is AppEvent.Progress -> "MSprogress" to "{\"time\": ${event.time}}"
            is AppEvent.Error -> "EX" to event.message
            is AppEvent.WebsocketConnected ->
                "webSocketConnection" to if (event.connected) "true" else "false"
            is AppEvent.WebsocketLogin -> "login" to event.data
            is AppEvent.CurrentTrack -> "MScurrentTrack" to event.json
            is AppEvent.PlaylistUpdated -> "MSplaylistUpdated" to null
        }
        Globals.NotifyObservers(action, value)
    }
}
