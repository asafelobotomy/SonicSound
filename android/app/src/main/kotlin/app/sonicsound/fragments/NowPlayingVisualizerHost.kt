package app.sonicsound.fragments

import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.view.isVisible
import app.sonicsound.KeyValueStorage
import app.sonicsound.R
import app.sonicsound.extensions.clearAlbumArtTarget
import app.sonicsound.extensions.loadAlbumArt
import app.sonicsound.extensions.loadUrl
import app.sonicsound.models.FullscreenVisualizer
import app.sonicsound.visualizer.DvdScreensaver
import app.sonicsound.visualizer.WmpVisualizerView

/**
 * DVD / WMP / art-background chrome for [NowPlayingFullscreen].
 * Keeps bouncing-art and spectrum surface lifecycle out of the main chrome class.
 */
class NowPlayingVisualizerHost(
    private val root: View,
    private val overlay: FrameLayout,
    private val solidBg: View,
    private val fsBackdrop: ImageView,
    private val fsArt: ImageView,
    private val fsMedia: FrameLayout,
    private val wmpVisualizer: WmpVisualizerView,
    private val fsVisualizer: ImageButton,
    private val isVideoMode: () -> Boolean,
    private val isActive: () -> Boolean,
    private val isPlaying: () -> Boolean,
    private val onChromeApplied: () -> Unit,
    private val onToast: (String) -> Unit,
) {
    var visualizerMode: String = FullscreenVisualizer.ART_BACKGROUND
        private set

    private var dvdRunning = false
    private var dvdLayoutPrepared = false
    private var lastArtUrl: String? = null
    private var lastDvdFrameMs = 0L
    private var cachedDvdSpeedPx = 0f

    private val dvdScreensaver = DvdScreensaver(speedPxPerSec = { dvdSpeedPxPerSec() })
    private val dvdFrameCallback = object : android.view.Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!dvdRunning || !isActive()) return
            stepDvd(frameTimeNanos / 1_000_000L)
            android.view.Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val dvdArtSizePx: Int
        get() = (200f * root.resources.displayMetrics.density).toInt()

    fun metaHidesUntilControls(): Boolean =
        visualizerMode == FullscreenVisualizer.DVD ||
            FullscreenVisualizer.isWmpMode(visualizerMode)

    fun reloadFromSettings(reloadArt: Boolean) {
        if (!isActive() || isVideoMode()) return
        applyModeFromSettings(reloadArt)
    }

    fun cycle() {
        if (!isActive() || isVideoMode()) return
        val current = KeyValueStorage.getSettings().fullscreenVisualizer
        val next = FullscreenVisualizer.nextMode(current)
        val settings = KeyValueStorage.getSettings()
        KeyValueStorage.setSettings(settings.copy(fullscreenVisualizer = next))
        applyModeFromSettings(reloadArt = true)
        val label = root.resources.getString(FullscreenVisualizer.labelRes(next))
        fsVisualizer.contentDescription =
            root.resources.getString(R.string.cycle_visualizer_fmt, label)
        onToast(root.resources.getString(R.string.cycle_visualizer_fmt, label))
    }

    fun applyModeFromSettings(reloadArt: Boolean) {
        applyChrome()
        syncMotionForMode()
        refreshButton()
        if (reloadArt) {
            val url = lastArtUrl
            lastArtUrl = null
            url?.let { loadArtForMode(it) }
        }
    }

    fun syncMotionForMode() {
        if (!isVideoMode() && visualizerMode == FullscreenVisualizer.DVD) {
            startDvd()
        } else {
            stopDvd()
        }
        if (!isVideoMode() && FullscreenVisualizer.isWmpMode(visualizerMode)) {
            startWmp()
        } else {
            stopWmp()
        }
    }

    fun refreshButton() {
        val mode = KeyValueStorage.getSettings().fullscreenVisualizer
        val label = root.resources.getString(FullscreenVisualizer.labelRes(mode))
        fsVisualizer.contentDescription =
            root.resources.getString(R.string.cycle_visualizer_fmt, label)
        fsVisualizer.isVisible = !isVideoMode()
        fsVisualizer.isFocusable = !isVideoMode()
    }

    fun loadArtForMode(artUrl: String) {
        if (artUrl.isNotBlank() && artUrl == lastArtUrl) return
        lastArtUrl = artUrl.takeIf { it.isNotBlank() }
        when {
            FullscreenVisualizer.isWmpMode(visualizerMode) -> Unit
            visualizerMode == FullscreenVisualizer.ART_BACKGROUND -> {
                fsBackdrop.loadUrl(artUrl)
                fsArt.scaleType = ImageView.ScaleType.FIT_CENTER
                fsArt.loadAlbumArt(artUrl, upscaleLowRes = true)
            }
            visualizerMode == FullscreenVisualizer.ART_BLACK ||
                visualizerMode == FullscreenVisualizer.ART_SOLID -> {
                fsArt.scaleType = ImageView.ScaleType.FIT_CENTER
                fsArt.loadAlbumArt(artUrl, upscaleLowRes = true)
            }
            visualizerMode == FullscreenVisualizer.DVD -> {
                fsArt.scaleType = ImageView.ScaleType.CENTER_CROP
                fsArt.loadAlbumArt(artUrl, upscaleLowRes = false)
            }
            else -> {
                fsBackdrop.loadUrl(artUrl)
                fsArt.scaleType = ImageView.ScaleType.FIT_CENTER
                fsArt.loadAlbumArt(artUrl, upscaleLowRes = true)
            }
        }
    }

    fun applyChrome() {
        val settings = KeyValueStorage.getSettings()
        visualizerMode = settings.fullscreenVisualizer
        if (isVideoMode()) {
            solidBg.isVisible = false
            fsBackdrop.isVisible = true
            fsBackdrop.alpha = 0.35f
            overlay.setBackgroundColor(Color.parseColor("#E6282c34"))
            resetArtLayoutForStandard()
            refreshButton()
            onChromeApplied()
            return
        }
        when (visualizerMode) {
            FullscreenVisualizer.ART_BACKGROUND -> {
                stopWmp()
                wmpVisualizer.isVisible = false
                fsArt.isVisible = true
                solidBg.isVisible = false
                fsBackdrop.isVisible = true
                fsBackdrop.alpha = 0.35f
                overlay.setBackgroundColor(Color.parseColor("#E6282c34"))
                resetArtLayoutForStandard()
            }
            FullscreenVisualizer.ART_BLACK -> {
                stopWmp()
                wmpVisualizer.isVisible = false
                fsArt.isVisible = true
                solidBg.isVisible = true
                solidBg.setBackgroundColor(Color.BLACK)
                fsBackdrop.isVisible = false
                overlay.setBackgroundColor(Color.BLACK)
                resetArtLayoutForStandard()
            }
            FullscreenVisualizer.ART_SOLID -> {
                stopWmp()
                wmpVisualizer.isVisible = false
                fsArt.isVisible = true
                solidBg.isVisible = true
                solidBg.setBackgroundColor(parseColorSafe(settings.fullscreenSolidColor))
                fsBackdrop.isVisible = false
                overlay.setBackgroundColor(parseColorSafe(settings.fullscreenSolidColor))
                resetArtLayoutForStandard()
            }
            FullscreenVisualizer.DVD -> {
                stopWmp()
                wmpVisualizer.isVisible = false
                fsArt.isVisible = true
                solidBg.isVisible = true
                solidBg.setBackgroundColor(Color.BLACK)
                fsBackdrop.isVisible = false
                overlay.setBackgroundColor(Color.BLACK)
                if (!dvdLayoutPrepared) {
                    prepareDvdArtLayout()
                    dvdLayoutPrepared = true
                }
            }
            else -> if (FullscreenVisualizer.isWmpMode(visualizerMode)) {
                stopDvd()
                fsArt.isVisible = false
                fsBackdrop.isVisible = false
                solidBg.isVisible = false
                overlay.setBackgroundColor(Color.BLACK)
                wmpVisualizer.isVisible = true
                wmpVisualizer.setMode(visualizerMode)
            } else {
                stopWmp()
                wmpVisualizer.isVisible = false
                fsArt.isVisible = true
                solidBg.isVisible = false
                fsBackdrop.isVisible = true
                fsBackdrop.alpha = 0.35f
                overlay.setBackgroundColor(Color.parseColor("#E6282c34"))
                resetArtLayoutForStandard()
            }
        }
        refreshButton()
        onChromeApplied()
    }

    fun resetArtLayoutForStandard() {
        dvdLayoutPrepared = false
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        fsArt.layoutParams = lp
        fsArt.translationX = 0f
        fsArt.translationY = 0f
        fsArt.scaleType = ImageView.ScaleType.FIT_CENTER
    }

    fun stopAll() {
        stopDvd()
        stopWmp()
    }

    fun clearArtLoads() {
        fsArt.clearAlbumArtTarget()
        fsBackdrop.clearAlbumArtTarget()
        lastArtUrl = null
    }

    fun setPlaying(playing: Boolean) {
        wmpVisualizer.setPlaying(playing)
    }

    private fun prepareDvdArtLayout() {
        val size = dvdArtSizePx
        val lp = FrameLayout.LayoutParams(size, size)
        fsArt.layoutParams = lp
        fsArt.scaleType = ImageView.ScaleType.CENTER_CROP
        val density = root.resources.displayMetrics.density
        dvdScreensaver.reset(40f * density, 40f * density)
        fsArt.translationX = dvdScreensaver.x
        fsArt.translationY = dvdScreensaver.y
    }

    private fun startDvd() {
        if (dvdRunning || isVideoMode()) return
        refreshDvdSpeedCache()
        val density = root.resources.displayMetrics.density
        if (dvdScreensaver.vx == 0f && dvdScreensaver.vy == 0f) {
            dvdScreensaver.reset(
                fsArt.translationX.takeIf { it > 0f } ?: (40f * density),
                fsArt.translationY.takeIf { it > 0f } ?: (40f * density),
            )
        }
        dvdRunning = true
        lastDvdFrameMs = SystemClock.uptimeMillis()
        android.view.Choreographer.getInstance().postFrameCallback(dvdFrameCallback)
    }

    private fun stopDvd() {
        if (!dvdRunning) return
        dvdRunning = false
        android.view.Choreographer.getInstance().removeFrameCallback(dvdFrameCallback)
    }

    private fun startWmp() {
        wmpVisualizer.isVisible = true
        wmpVisualizer.setMode(visualizerMode)
        wmpVisualizer.setPlaying(isPlaying())
        wmpVisualizer.start()
    }

    private fun stopWmp() {
        wmpVisualizer.stop()
        wmpVisualizer.isVisible = false
    }

    private fun dvdSpeedPxPerSec(): Float {
        if (cachedDvdSpeedPx > 0f) return cachedDvdSpeedPx
        return refreshDvdSpeedCache()
    }

    private fun refreshDvdSpeedCache(): Float {
        val density = root.resources.displayMetrics.density
        cachedDvdSpeedPx = when (KeyValueStorage.getSettings().dvdSpeed) {
            FullscreenVisualizer.SPEED_SLOW -> 48f * density
            FullscreenVisualizer.SPEED_FAST -> 160f * density
            else -> 90f * density
        }
        return cachedDvdSpeedPx
    }

    private fun stepDvd(nowMs: Long) {
        val dt = ((nowMs - lastDvdFrameMs).coerceAtMost(50)).toFloat() / 1000f
        lastDvdFrameMs = nowMs
        val parentW = fsMedia.width
        val parentH = fsMedia.height
        if (parentW <= 0 || parentH <= 0) return
        val artW = fsArt.width.takeIf { it > 0 } ?: dvdArtSizePx
        val artH = fsArt.height.takeIf { it > 0 } ?: dvdArtSizePx
        dvdScreensaver.step(dt, parentW, parentH, artW, artH)
        fsArt.translationX = dvdScreensaver.x
        fsArt.translationY = dvdScreensaver.y
    }

    private fun parseColorSafe(hex: String): Int = try {
        Color.parseColor(hex)
    } catch (_: Exception) {
        Color.parseColor(FullscreenVisualizer.DEFAULT_SOLID)
    }
}
