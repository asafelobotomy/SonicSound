package app.sonicsound

import android.media.session.PlaybackState
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import java.util.Objects

class Globals private constructor() {
    private val observers: MutableList<IBroadcastObserver> = ArrayList()
    private val mediaSession: MediaSessionCompat =
        MediaSessionCompat(App.context, "SonicSound").also { session ->
            session.setCallback(SonicSoundSessionCallbacks())
            val stateBuilder = PlaybackStateCompat.Builder()
            stateBuilder.setState(
                PlaybackStateCompat.STATE_PAUSED,
                PlaybackState.PLAYBACK_POSITION_UNKNOWN.toLong(),
                1f,
            )
            stateBuilder.setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS,
            )
            session.setPlaybackState(stateBuilder.build())
        }

    companion object {
        @Volatile
        private var instance: Globals? = null

        @JvmStatic
        fun getInstance(): Globals {
            return instance ?: synchronized(this) {
                instance ?: Globals().also { instance = it }
            }
        }

        @JvmStatic
        fun RegisterObserver(observer: IBroadcastObserver) {
            getInstance().observers.add(observer)
        }

        @JvmStatic
        fun UnregisterObserver(observer: IBroadcastObserver) {
            getInstance().observers.remove(observer)
        }

        @JvmStatic
        fun NotifyObservers(action: String?, value: String?) {
            if (Objects.equals(action, "EX")) {
                Log.e("SonicSound Exception", value ?: "Unknown error")
            }
            for (observer in getInstance().observers.toList()) {
                try {
                    observer.update(action, value)
                } catch (e: Exception) {
                    Log.e("SonicSound Globals", e.message ?: "Unknown error")
                }
            }
        }

        @JvmStatic
        fun GetMediaSession(): MediaSessionCompat = getInstance().mediaSession
    }
}
