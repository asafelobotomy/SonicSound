package app.sonicsound.visualizer

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import app.sonicsound.models.FullscreenVisualizer

/** Full-screen WMP-style visualization driven by LibVLC PCM → [PlaybackSpectrum]. */
class WmpVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var mode: String = FullscreenVisualizer.WMP_BARS
    private var tick = 0
    private val spectrum = AudioSpectrumSource(context)
    private var running = false
    private var playing = false
    private var lastFrameMs = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val now = frameTimeNanos / 1_000_000L
            val minDelta = if (playing) 0L else IDLE_FRAME_MS
            if (now - lastFrameMs >= minDelta) {
                lastFrameMs = now
                spectrum.tick()
                tick++
                invalidate()
            }
            scheduleNextFrame()
        }
    }

    fun setMode(mode: String) {
        this.mode = mode
        invalidate()
    }

    fun start() {
        spectrum.start()
        if (running) return
        running = true
        lastFrameMs = 0L
        scheduleNextFrame()
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        spectrum.stop()
    }

    fun setPlaying(playing: Boolean) {
        this.playing = playing
        spectrum.setPlaying(playing)
        if (playing && running) {
            lastFrameMs = 0L
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            scheduleNextFrame()
        }
    }

    private fun scheduleNextFrame() {
        if (!running) return
        if (playing) {
            Choreographer.getInstance().postFrameCallback(frameCallback)
        } else {
            Choreographer.getInstance().postFrameCallbackDelayed(frameCallback, IDLE_FRAME_MS)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        WmpRenderers.draw(mode, canvas, spectrum, tick, width, height)
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val IDLE_FRAME_MS = 64L
    }
}
