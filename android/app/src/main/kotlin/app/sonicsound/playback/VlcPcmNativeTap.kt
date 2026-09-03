package app.sonicsound.playback

import android.util.Log
import org.videolan.libvlc.MediaPlayer

/**
 * Native library load + MediaPlayer pointer reflection for [VlcPcmOutput].
 * JNI entry points remain on [VlcPcmOutput] so existing native symbols keep working.
 */
internal object VlcPcmNativeTap {
    private const val TAG = "VlcPcmNativeTap"

    /** Load shared libs; returns true if libraries loaded (caller still runs nativeInit). */
    fun loadLibraries(): Boolean {
        return try {
            runCatching { System.loadLibrary("c++_shared") }
            System.loadLibrary("vlc")
            runCatching { System.loadLibrary("vlcjni") }
            System.loadLibrary("vlc_pcm_tap")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "vlc_pcm_tap unavailable", e)
            false
        }
    }

    fun playerNativePtr(player: MediaPlayer?): Long {
        if (player == null) return 0L
        return runCatching {
            val m = player.javaClass.methods.firstOrNull { it.name == "getInstance" && it.parameterCount == 0 }
                ?: player.javaClass.superclass?.methods?.firstOrNull {
                    it.name == "getInstance" && it.parameterCount == 0
                }
            (m?.invoke(player) as? Long) ?: 0L
        }.getOrElse {
            Log.w(TAG, "getInstance reflection failed", it)
            0L
        }
    }
}
