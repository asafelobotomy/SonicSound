package app.sonicsound.visualizer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.sonicsound.R
import app.sonicsound.models.FullscreenVisualizer

/** Full-screen legacy WMP-style visualization canvas driven by live [AudioSpectrumSource]. */
class WmpVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var mode: String = FullscreenVisualizer.WMP_BARS
    private var tick = 0
    private val spectrum = AudioSpectrumSource()
    private var running = false
    private var playing = false
    private var permissionRequested = false
    private var deniedHintShown = false
    private var lastFrameMs = 0L
    private var lastAttachAttemptMs = 0L

    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 200, 200, 200)
        textAlign = Paint.Align.CENTER
        textSize = 36f
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val now = frameTimeNanos / 1_000_000L
            val minDelta = if (playing) 0L else IDLE_FRAME_MS
            if (now - lastFrameMs >= minDelta) {
                lastFrameMs = now
                if (spectrum.shouldReattach()) {
                    tryAttachSpectrum(force = true)
                } else {
                    maybeRetryAttach()
                }
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
        if (!running) {
            tryAttachSpectrum(force = true)
            running = true
            lastFrameMs = 0L
            scheduleNextFrame()
        } else if (!spectrum.active) {
            // Already animating but capture died — retry without tearing down the loop.
            tryAttachSpectrum(force = true)
        }
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

    private fun maybeRetryAttach() {
        if (spectrum.active) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastAttachAttemptMs < ATTACH_RETRY_MS) return
        tryAttachSpectrum(force = false)
    }

    private fun tryAttachSpectrum(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastAttachAttemptMs < ATTACH_RETRY_MS) return
        // Avoid thrashing a healthy Visualizer (e.g. every track change calls start()).
        if (!force && spectrum.active) return
        if (force && spectrum.active && !spectrum.shouldReattach()) return
        lastAttachAttemptMs = now

        val ctx = context
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            spectrum.start(ctx)
            return
        }
        requestRecordAudioPermission()
    }

    private fun requestRecordAudioPermission() {
        if (permissionRequested) {
            maybeShowDeniedHint()
            return
        }
        val activity = findActivity()
        if (activity == null) {
            Log.w(TAG, "No Activity in context chain — cannot request RECORD_AUDIO")
            return
        }
        permissionRequested = true
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO,
        )
    }

    private fun findActivity(): Activity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return ctx as? Activity
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun maybeShowDeniedHint() {
        if (deniedHintShown || hasMicPermission()) return
        deniedHintShown = true
        Toast.makeText(
            context,
            context.getString(R.string.visualizer_needs_mic_permission),
            Toast.LENGTH_LONG,
        ).show()
    }

    /** Retry attach after the host activity receives a permission result. */
    fun onRecordAudioPermissionResult(granted: Boolean) {
        if (granted) {
            deniedHintShown = false
            permissionRequested = false
            if (running) {
                // Force recreate even if a prior failed instance lingered.
                spectrum.stop()
                tryAttachSpectrum(force = true)
            }
        } else {
            maybeShowDeniedHint()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        WmpRenderers.draw(mode, canvas, spectrum, tick, width, height)
        if (running && !spectrum.active) {
            hintPaint.textSize = (height * 0.035f).coerceIn(28f, 48f)
            val msg = if (hasMicPermission()) {
                R.string.visualizer_waiting_for_audio
            } else {
                R.string.visualizer_needs_mic_permission
            }
            canvas.drawText(context.getString(msg), width / 2f, height / 2f, hintPaint)
        }
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    companion object {
        const val REQUEST_RECORD_AUDIO = 0x51A0
        private const val TAG = "WmpVisualizerView"
        private const val IDLE_FRAME_MS = 64L
        private const val ATTACH_RETRY_MS = 2000L
    }
}
