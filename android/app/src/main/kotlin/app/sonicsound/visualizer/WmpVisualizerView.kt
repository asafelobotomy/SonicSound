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
    private val spectrum = AudioSpectrumSource(context)
    private val renderState = WmpRenderState()
    private var running = false
    private var playing = false
    private var lastFrameNanos = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val dt = if (lastFrameNanos == 0L) {
                1f / 60f
            } else {
                ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            }
            lastFrameNanos = frameTimeNanos
            spectrum.tick(playing)
            renderState.step(dt, spectrum, mode)
            invalidate()
            // Always lock to display refresh while visible — smooth across track gaps.
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun setMode(mode: String) {
        if (this.mode != mode) {
            this.mode = mode
            renderState.onModeChanged(mode)
        }
        invalidate()
    }

    fun start() {
        spectrum.start()
        if (running) return
        running = true
        lastFrameNanos = 0L
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
}
