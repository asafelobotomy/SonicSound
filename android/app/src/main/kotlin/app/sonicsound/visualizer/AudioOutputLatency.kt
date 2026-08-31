package app.sonicsound.visualizer

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import app.sonicsound.playback.VlcPcmOutput

/**
 * Estimates how far ahead decoded PCM is vs what the user actually hears.
 *
 * Visualizations are driven at AudioTrack-write time; speakers/HDMI add tens of ms,
 * Bluetooth A2DP often 150–350ms+. Delaying the displayed spectrum by this amount
 * lines bars up with the audible beat.
 */
object AudioOutputLatency {
    private const val TAG = "AudioOutLatency"

    /** Base fudge for mixer / Shield path when APIs under-report. */
    private const val BASE_MS = 25

    /** Typical A2DP encoder + buffer when device type is Bluetooth. */
    private const val BT_A2DP_MS = 220

    /** BLE / hearing-aid style links. */
    private const val BT_BLE_MS = 120

    /** Wired / HDMI / speaker extras beyond AudioTrack. */
    private const val WIRED_EXTRA_MS = 15

    fun estimateMs(context: Context): Int {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val trackMs = VlcPcmOutput.outputLatencyMs()
        val route = routeExtraMs(am)
        val outputProp = outputPropertyMs(am)
        val total = (BASE_MS + trackMs + route + outputProp).coerceIn(0, 750)
        return total
    }

    private fun routeExtraMs(am: AudioManager): Int {
        val devices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        } else {
            return WIRED_EXTRA_MS
        }
        var btA2dp = false
        var btBle = false
        var wired = false
        for (d in devices) {
            when (d.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                -> btA2dp = true
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_HDMI,
                AudioDeviceInfo.TYPE_HDMI_ARC,
                AudioDeviceInfo.TYPE_LINE_ANALOG,
                AudioDeviceInfo.TYPE_LINE_DIGITAL,
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                -> wired = true
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        when (d.type) {
                            AudioDeviceInfo.TYPE_BLE_HEADSET,
                            AudioDeviceInfo.TYPE_BLE_SPEAKER,
                            -> btBle = true
                        }
                    }
                }
            }
        }
        return when {
            btA2dp -> BT_A2DP_MS
            btBle -> BT_BLE_MS
            wired -> WIRED_EXTRA_MS
            else -> WIRED_EXTRA_MS
        }
    }

    private fun outputPropertyMs(am: AudioManager): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) return 0
        val frames = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            ?.toIntOrNull() ?: return 0
        val rate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull()?.coerceAtLeast(1) ?: return 0
        // Half a native burst as a small mixer pad (not full burst — AudioTrack already counted).
        return ((frames * 500L) / rate).toInt().coerceIn(0, 40)
    }

    fun logEstimate(context: Context) {
        val ms = estimateMs(context)
        Log.i(TAG, "viz delay=${ms}ms (track=${VlcPcmOutput.outputLatencyMs()}ms)")
    }
}
