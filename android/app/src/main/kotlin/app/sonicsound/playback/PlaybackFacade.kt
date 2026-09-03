package app.sonicsound.playback

import android.content.Intent
import app.sonicsound.App
import app.sonicsound.Constants
import app.sonicsound.services.MusicService
import app.sonicsound.services.MusicService.LocalBinder

/**
 * Shared local-playback dispatch: prefer a bound [LocalBinder], otherwise start
 * [MusicService] with the matching action. Callers that also speak websocket should
 * short-circuit before invoking these helpers.
 */
object PlaybackFacade {

    fun play(binder: LocalBinder?, bound: Boolean, startService: (Intent) -> Unit) {
        if (bound && binder != null) {
            binder.play()
            return
        }
        startService(serviceIntent(Constants.SERVICE_PLAY))
    }

    fun pause(binder: LocalBinder?, bound: Boolean, startService: (Intent) -> Unit) {
        if (bound && binder != null) {
            binder.pause()
            return
        }
        startService(serviceIntent(Constants.SERVICE_PAUSE))
    }

    fun next(binder: LocalBinder?, bound: Boolean, startService: (Intent) -> Unit) {
        if (bound && binder != null) {
            binder.next()
            return
        }
        startService(serviceIntent(Constants.SERVICE_NEXT))
    }

    fun prev(binder: LocalBinder?, bound: Boolean, startService: (Intent) -> Unit) {
        if (bound && binder != null) {
            binder.prev()
            return
        }
        startService(serviceIntent(Constants.SERVICE_PREV))
    }

    fun seek(binder: LocalBinder?, bound: Boolean, position: Float) {
        if (bound && binder != null) binder.seek(position)
    }

    fun setVolume(binder: LocalBinder?, bound: Boolean, volume: Int) {
        if (bound && binder != null) binder.setVolume(volume)
    }

    fun shuffle(binder: LocalBinder?, bound: Boolean) {
        if (bound && binder != null) binder.shuffle()
    }

    fun cycleRepeat(binder: LocalBinder?, bound: Boolean) {
        if (bound && binder != null) binder.cycleRepeat()
    }

    fun skipTo(binder: LocalBinder?, bound: Boolean, track: Int) {
        if (bound && binder != null) binder.skipTo(track)
    }

    fun playAlbum(
        binder: LocalBinder?,
        bound: Boolean,
        startService: (Intent) -> Unit,
        id: String,
        track: Int,
    ) {
        if (bound && binder != null) {
            binder.playAlbum(id, track)
            return
        }
        startService(
            serviceIntent(Constants.SERVICE_PLAY_ALBUM).apply {
                putExtra("id", id)
                putExtra("track", track)
            }
        )
    }

    fun playPlaylist(
        binder: LocalBinder?,
        bound: Boolean,
        startService: (Intent) -> Unit,
        id: String,
        track: Int,
    ) {
        if (bound && binder != null) {
            binder.playPlaylist(id, track)
            return
        }
        startService(
            serviceIntent(Constants.SERVICE_PLAY_PLAYLIST).apply {
                putExtra("id", id)
                putExtra("track", track)
            }
        )
    }

    fun playRadio(
        binder: LocalBinder?,
        bound: Boolean,
        startService: (Intent) -> Unit,
        id: String,
    ) {
        if (bound && binder != null) {
            binder.playRadio(id)
            return
        }
        startService(
            serviceIntent(Constants.SERVICE_PLAY_RADIO).apply {
                putExtra("id", id)
            }
        )
    }

    fun playInternetRadio(binder: LocalBinder?, bound: Boolean, streamUrl: String, name: String): Boolean {
        if (!bound || binder == null) return false
        binder.playInternetRadio(streamUrl, name)
        return true
    }

    fun playJukeboxCollection(
        binder: LocalBinder?,
        bound: Boolean,
        startService: (Intent) -> Unit,
        collection: String,
    ) {
        if (bound && binder != null) {
            binder.playJukeboxCollection(collection)
            return
        }
        startService(
            serviceIntent(Constants.SERVICE_PLAY_JUKEBOX).apply {
                putExtra("collection", collection)
            }
        )
    }

    /** Default starter used by Capacitor / TV when no custom lambda is needed. */
    fun defaultStartService(intent: Intent) {
        App.context.startService(intent)
    }

    private fun serviceIntent(action: String): Intent =
        Intent(App.context, MusicService::class.java).also { it.action = action }
}
