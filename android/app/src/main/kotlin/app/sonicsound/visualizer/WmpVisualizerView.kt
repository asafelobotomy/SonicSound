package app.sonicsound.visualizer

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.View
import app.sonicsound.models.FullscreenVisualizer

/**
 * Full-screen WMP-style visualization driven by LibVLC PCM → [PlaybackSpectrum].
 *
 * Render path: hardware-accelerated [View] + [Choreographer] + [invalidate].
 *
 * Why not SurfaceView + setFixedSize on SHIELD (mdarcy / Tegra X1+):
 * - 4K video uses NVDEC + HWC overlay planes — a different pipe than Skia/Canvas
 * - SurfaceFlinger here is configured with only 2 framebuffer buffers; locking a
 *   secondary Surface every vsync contended and stalled (~50fps bars, 100ms+ spikes)
 * - Simple modes (bars/scope) were already cheap as in-tree Views; the Surface path
 *   made them worse without helping video-class throughput
 */
class WmpVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var mode: String = FullscreenVisualizer.WMP_BARS
    private val spectrum = AudioSpectrumSource(context)
    private val renderState = WmpRenderState()
    private var running = false
    private var playing = false
    private var lastFrameNanos = 0L
    private var framesInWindow = 0
    private var windowStartMs = 0L
    private var lastLoggedSlowMs = 0L
    private var maxSimMsInWindow = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            try {
                val dt = if (lastFrameNanos == 0L) {
                    1f / 60f
                } else {
                    ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                }
                lastFrameNanos = frameTimeNanos
                val t0 = SystemClock.elapsedRealtime()
                spectrum.tick(playing)
                renderState.step(dt, spectrum, mode)
                noteFrame(SystemClock.elapsedRealtime() - t0)
                invalidate()
            } catch (_: Throwable) {
                // Keep the frame loop alive — a bad draw/sim tick must not kill Now Playing.
            }
            if (running) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    fun setMode(mode: String) {
        if (this.mode != mode) {
            this.mode = mode
            renderState.onModeChanged(mode)
            spectrum.smoothWaveNeeded = mode == "wmp_scope"
            Log.i(TAG, "mode=$mode ${width}x${height} running=$running")
            framesInWindow = 0
            windowStartMs = 0L
            maxSimMsInWindow = 0L
        }
        invalidate()
    }

    fun start() {
        spectrum.start()
        if (running) return
        running = true
        lastFrameNanos = 0L
        framesInWindow = 0
        windowStartMs = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        spectrum.stop()
    }

    fun setPlaying(playing: Boolean) {
        this.playing = playing
        spectrum.setPlaying(playing)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        WmpRenderers.draw(mode, canvas, spectrum, renderState, width, height)
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun noteFrame(simMs: Long) {
        if (windowStartMs == 0L) windowStartMs = SystemClock.elapsedRealtime()
        framesInWindow++
        if (simMs > maxSimMsInWindow) maxSimMsInWindow = simMs
        val now = SystemClock.elapsedRealtime()
        if (simMs >= 12L && now - lastLoggedSlowMs > 2_000L) {
            lastLoggedSlowMs = now
            Log.i(TAG, "slow sim ${simMs}ms mode=$mode ${width}x${height}")
        }
        if (framesInWindow >= 120) {
            val elapsed = (now - windowStartMs).coerceAtLeast(1L)
            val fps = framesInWindow * 1000f / elapsed
            Log.i(
                TAG,
                "viz ~${"%.1f".format(fps)} fps mode=$mode ${width}x${height} " +
                    "maxSim=${maxSimMsInWindow}ms",
            )
            framesInWindow = 0
            windowStartMs = now
            maxSimMsInWindow = 0L
        }
    }

    companion object {
        private const val TAG = "WmpViz"
    }
}
