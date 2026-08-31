package app.sonicsound.visualizer

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.util.Log
import app.sonicsound.playback.VlcPcmOutput

/**
 * Estimates how far ahead decoded PCM is vs what the user actually hears.
 *
 * Visualizations are driven at AudioTrack-write time; speakers/HDMI add tens of ms,
 * Bluetooth A2DP often 150–350ms+. Delaying the displayed spectrum by this amount
 * lines bars up with the audible beat.
 *
 * Prefer the **active routed** device when available so a merely-paired Bluetooth
 * headset does not add a false ~220ms delay while playing over HDMI/speakers.
 *
 * IMPORTANT (SHIELD / Tegra): [AudioManager.getDevices] can take tens–hundreds of ms.
 * Never call the full estimate on the Choreographer/UI path — use [cachedMs] there and
 * refresh on a background thread.
 */
object AudioOutputLatency {
    private const val TAG = "AudioOutLatency"

    private const val BASE_MS = 25
    private const val BT_A2DP_MS = 220
    private const val BT_BLE_MS = 120
    private const val WIRED_EXTRA_MS = 15
    private const val REFRESH_INTERVAL_MS = 2_000L

    @Volatile
    private var cachedMs: Int = BASE_MS + WIRED_EXTRA_MS

    @Volatile
    private var lastRefreshUptimeMs = 0L

    private val refreshThread = HandlerThread("ss-aout-latency", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
    private val refreshHandler = Handler(refreshThread.looper)

    /** Non-blocking read for the frame loop. */
    fun cachedMs(): Int = cachedMs

    /**
     * Full estimate. Safe from a background thread; updates [cachedMs].
     * Prefer [cachedMs] / [scheduleRefresh] on the UI thread.
     */
    fun estimateMs(context: Context): Int {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val trackMs = VlcPcmOutput.outputLatencyMs()
        val route = routeExtraMs(am)
        val outputProp = outputPropertyMs(am)
        val ms = (BASE_MS + trackMs + route + outputProp).coerceIn(0, 750)
        cachedMs = ms
        lastRefreshUptimeMs = SystemClock.uptimeMillis()
        return ms
    }

    /** Kick a background refresh at most once per [REFRESH_INTERVAL_MS]. */
    fun scheduleRefresh(context: Context) {
        val now = SystemClock.uptimeMillis()
        if (now - lastRefreshUptimeMs < REFRESH_INTERVAL_MS) return
        lastRefreshUptimeMs = now // claim slot so we don't stampede
        val app = context.applicationContext
        refreshHandler.post {
            runCatching { estimateMs(app) }
                .onFailure { Log.w(TAG, "latency refresh failed", it) }
        }
    }

    private fun routeExtraMs(am: AudioManager): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return WIRED_EXTRA_MS
        val routed = VlcPcmOutput.routedOutputDevice()
            ?: preferredCommunicationDevice(am)
        if (routed != null) return extraForDevice(routed)

        // Fallback: only count devices that look like they could be the active path.
        // Prefer non-BT when both BT and wired/HDMI are present (paired ≠ playing).
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        var hasBtA2dp = false
        var hasBtBle = false
        var hasWiredOrHdmi = false
        for (d in devices) {
            when (extraForDevice(d)) {
                BT_A2DP_MS -> hasBtA2dp = true
                BT_BLE_MS -> hasBtBle = true
                WIRED_EXTRA_MS -> hasWiredOrHdmi = true
            }
        }
        return when {
            hasWiredOrHdmi && !hasBtA2dp && !hasBtBle -> WIRED_EXTRA_MS
            hasWiredOrHdmi -> WIRED_EXTRA_MS // HDMI/speaker wins over merely-paired BT
            hasBtA2dp -> BT_A2DP_MS
            hasBtBle -> BT_BLE_MS
            else -> WIRED_EXTRA_MS
        }
    }

    private fun preferredCommunicationDevice(am: AudioManager): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return runCatching { am.communicationDevice }.getOrNull()
    }

    private fun extraForDevice(d: AudioDeviceInfo): Int {
        return when (d.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            -> BT_A2DP_MS
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            -> WIRED_EXTRA_MS
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    when (d.type) {
                        AudioDeviceInfo.TYPE_BLE_HEADSET,
                        AudioDeviceInfo.TYPE_BLE_SPEAKER,
                        -> return BT_BLE_MS
                    }
                }
                WIRED_EXTRA_MS
            }
        }
    }

    private fun outputPropertyMs(am: AudioManager): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) return 0
        val frames = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            ?.toIntOrNull() ?: return 0
        val rate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull()?.coerceAtLeast(1) ?: return 0
        return ((frames * 500L) / rate).toInt().coerceIn(0, 40)
    }

    fun logEstimate(context: Context) {
        Log.i(TAG, "viz delay=${cachedMs}ms (track=${VlcPcmOutput.outputLatencyMs()}ms)")
    }
}
