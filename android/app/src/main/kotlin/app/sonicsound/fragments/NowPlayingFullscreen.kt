package app.sonicsound.fragments

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import app.sonicsound.KeyValueStorage
import app.sonicsound.R
import app.sonicsound.TvActivity
import app.sonicsound.extensions.loadAlbumArt
import app.sonicsound.extensions.loadUrl
import app.sonicsound.models.FullscreenVisualizer
import app.sonicsound.models.Song
import app.sonicsound.playback.RepeatMode
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Fullscreen art / music-video chrome for [NowPlayingFragment]. */
class NowPlayingFullscreen(
    private val root: View,
    private val bind: TvActivity.TvActivityBind,
    private val chrome: View,
    private val mediaSlot: FrameLayout,
    private val musicVideoContainer: FrameLayout,
    private val onPlayPause: () -> Unit,
    private val onSeek: (Float) -> Unit,
    private val onShuffle: () -> Unit,
    private val onRepeat: () -> Unit,
    private val onLike: () -> Unit,
    private val onMusicVideo: () -> Unit,
    private val enableMusicVideo: Boolean = false,
    private val durationProvider: () -> Int,
    private val timeLabels: () -> Pair<String, String>,
) {
    private val overlay: FrameLayout = root.findViewById(R.id.fl_fullscreen_overlay)
    private val solidBg: View = root.findViewById(R.id.v_fs_solid_bg)
    private val fsBackdrop: ImageView = root.findViewById(R.id.img_fs_backdrop)
    private val fsArt: ImageView = root.findViewById(R.id.img_fs_art)
    private val fsMedia: FrameLayout = root.findViewById(R.id.fl_fs_media)
    private val fsFocusAnchor: View = root.findViewById(R.id.v_fs_focus_anchor)
    private val fsMeta: View = root.findViewById(R.id.ll_fs_meta)
    private val fsTitle: TextView = root.findViewById(R.id.tv_fs_title)
    private val fsSubtitle: TextView = root.findViewById(R.id.tv_fs_subtitle)
    private val fsNextRow: LinearLayout = root.findViewById(R.id.ll_fs_next)
    private val fsNextLabel: TextView = root.findViewById(R.id.tv_fs_next_label)
    private val fsNextArtist: TextView = root.findViewById(R.id.tv_fs_next_artist)
    private val fsControls: LinearLayout = root.findViewById(R.id.ll_fs_controls)
    private val fsClock: LinearLayout = root.findViewById(R.id.ll_fs_clock)
    private val fsClockTime: TextView = root.findViewById(R.id.tv_fs_clock_time)
    private val fsClockDate: TextView = root.findViewById(R.id.tv_fs_clock_date)
    private val fsPlay: ImageButton = root.findViewById(R.id.btn_fs_play)
    private val fsPrev: ImageButton = root.findViewById(R.id.btn_fs_prev)
    private val fsNextBtn: ImageButton = root.findViewById(R.id.btn_fs_next)
    private val fsShuffle: ImageButton = root.findViewById(R.id.btn_fs_shuffle)
    private val fsRepeat: ImageButton = root.findViewById(R.id.btn_fs_repeat)
    private val fsLike: ImageButton = root.findViewById(R.id.btn_fs_like)
    private val fsMusicVideo: ImageButton = root.findViewById(R.id.btn_fs_music_video)
    private val fsSeekBar = root.findViewById<android.widget.SeekBar>(R.id.sb_fs_progress)
    private val fsScrubber = NowPlayingScrubber(
        root.findViewById(R.id.ll_fs_scrubber),
        fsSeekBar,
        durationProvider = durationProvider,
        onSeek = onSeek,
        onTimePreview = { sec ->
            val labels = timeLabels()
            setProgress(fsSeekBar.progress, formatSec(sec), labels.second)
        },
    )
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            refreshClock()
            clockHandler.postDelayed(this, 15_000)
        }
    }
    private val fsButtons = mutableListOf<ImageButton>()
    private var lastFocusedControl: View? = null
    var active = false
        private set
    private var videoMode = false
    private var controlsVisible = true
    private var visualizerMode = FullscreenVisualizer.ART_BACKGROUND
    private var dvdRunning = false
    private var dvdVx = 0f
    private var dvdVy = 0f
    private var lastDvdFrameMs = 0L
    private val dvdArtSizePx: Int
        get() = (200f * root.resources.displayMetrics.density).toInt()
    private val dvdFrameCallback = object : android.view.Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!dvdRunning || !active) return
            stepDvd(frameTimeNanos / 1_000_000L)
            android.view.Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        fsScrubber.wire()
        styleExtraBold(fsClockTime)
        styleBold(fsClockDate)
        if (enableMusicVideo) {
            fsMusicVideo.visibility = View.VISIBLE
            fsMusicVideo.isFocusable = true
            fsButtons.addAll(listOf(fsShuffle, fsRepeat, fsPrev, fsPlay, fsNextBtn, fsMusicVideo, fsLike))
        } else {
            fsMusicVideo.visibility = View.GONE
            fsMusicVideo.isFocusable = false
            fsButtons.addAll(listOf(fsShuffle, fsRepeat, fsPrev, fsPlay, fsNextBtn, fsLike))
        }
        fsButtons.forEach { btn ->
            btn.setOnClickListener {
                rememberFocus(btn)
                when (btn.id) {
                    R.id.btn_fs_prev -> bind.prev()
                    R.id.btn_fs_next -> bind.next()
                    R.id.btn_fs_play -> onPlayPause()
                    R.id.btn_fs_shuffle -> onShuffle()
                    R.id.btn_fs_repeat -> onRepeat()
                    R.id.btn_fs_like -> onLike()
                    R.id.btn_fs_music_video -> if (enableMusicVideo) onMusicVideo()
                }
                showControls()
            }
            btn.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) rememberFocus(v)
                showControls()
            }
        }
        fsSeekBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) rememberFocus(fsSeekBar)
            showControls()
        }
        fsFocusAnchor.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (isInteractionKey(keyCode)) {
                showControls()
                restoreFocus()
                true
            } else {
                false
            }
        }
        overlay.setOnClickListener { showControls() }
        overlay.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && isInteractionKey(keyCode)) {
                if (!controlsVisible) {
                    showControls()
                    restoreFocus()
                } else {
                    resetHideTimer()
                }
            }
            false
        }
    }

    fun enter(artUrl: String, title: String, subtitle: String, nextSong: Song?, video: Boolean) {
        active = true
        videoMode = video
        chrome.isVisible = false
        overlay.isVisible = true
        bind.setImmersive(true)
        applyVisualizerChrome()
        applyTrack(artUrl, title, subtitle, nextSong, video)
        lastFocusedControl = fsPlay
        showControls()
        fsPlay.requestFocus()
        startClockUpdates()
    }

    fun updateTrack(
        artUrl: String,
        title: String,
        subtitle: String,
        nextSong: Song?,
        video: Boolean,
    ) {
        if (!active) return
        applyTrack(artUrl, title, subtitle, nextSong, video)
    }

    /** @return true if Back was consumed (scrub disarm). */
    fun handleBackPress(): Boolean = fsScrubber.disarmOnBack()

    private fun applyTrack(
        artUrl: String,
        title: String,
        subtitle: String,
        nextSong: Song?,
        video: Boolean,
    ) {
        val wasVideo = videoMode
        videoMode = video
        fsTitle.text = title
        styleExtraBold(fsTitle)
        fsSubtitle.text = subtitle
        styleBold(fsSubtitle)
        bindNext(nextSong)
        if (!video) {
            applyVisualizerChrome()
        }
        when {
            video && !wasVideo -> {
                stopDvd()
                resetArtLayoutForStandard()
                fsArt.isVisible = false
                fsBackdrop.isVisible = true
                solidBg.isVisible = false
                moveVideo(toFullscreen = true)
            }
            !video && wasVideo -> {
                moveVideo(toFullscreen = false)
                fsArt.isVisible = true
                applyVisualizerChrome()
                loadArtForMode(artUrl)
            }
            !video -> loadArtForMode(artUrl)
        }
        if (!video && visualizerMode == FullscreenVisualizer.DVD) {
            startDvd()
        } else {
            stopDvd()
        }
        applyMetaVisibility()
    }

    private fun loadArtForMode(artUrl: String) {
        when (visualizerMode) {
            FullscreenVisualizer.ART_BACKGROUND -> {
                fsBackdrop.loadUrl(artUrl)
                fsArt.scaleType = ImageView.ScaleType.FIT_CENTER
                fsArt.loadAlbumArt(artUrl, upscaleLowRes = true)
            }
            FullscreenVisualizer.ART_BLACK, FullscreenVisualizer.ART_SOLID -> {
                fsArt.scaleType = ImageView.ScaleType.FIT_CENTER
                fsArt.loadAlbumArt(artUrl, upscaleLowRes = true)
            }
            FullscreenVisualizer.DVD -> {
                fsArt.scaleType = ImageView.ScaleType.CENTER_CROP
                fsArt.loadAlbumArt(artUrl, upscaleLowRes = true)
            }
            else -> {
                fsBackdrop.loadUrl(artUrl)
                fsArt.scaleType = ImageView.ScaleType.FIT_CENTER
                fsArt.loadAlbumArt(artUrl, upscaleLowRes = true)
            }
        }
    }

    private fun applyVisualizerChrome() {
        val settings = KeyValueStorage.getSettings()
        visualizerMode = settings.fullscreenVisualizer
        if (videoMode) {
            solidBg.isVisible = false
            fsBackdrop.isVisible = true
            fsBackdrop.alpha = 0.35f
            overlay.setBackgroundColor(Color.parseColor("#E6282c34"))
            resetArtLayoutForStandard()
            refreshClock()
            return
        }
        when (visualizerMode) {
            FullscreenVisualizer.ART_BACKGROUND -> {
                solidBg.isVisible = false
                fsBackdrop.isVisible = true
                fsBackdrop.alpha = 0.35f
                overlay.setBackgroundColor(Color.parseColor("#E6282c34"))
                resetArtLayoutForStandard()
            }
            FullscreenVisualizer.ART_BLACK -> {
                solidBg.isVisible = true
                solidBg.setBackgroundColor(Color.BLACK)
                fsBackdrop.isVisible = false
                overlay.setBackgroundColor(Color.BLACK)
                resetArtLayoutForStandard()
            }
            FullscreenVisualizer.ART_SOLID -> {
                solidBg.isVisible = true
                solidBg.setBackgroundColor(parseColorSafe(settings.fullscreenSolidColor))
                fsBackdrop.isVisible = false
                overlay.setBackgroundColor(parseColorSafe(settings.fullscreenSolidColor))
                resetArtLayoutForStandard()
            }
            FullscreenVisualizer.DVD -> {
                solidBg.isVisible = true
                solidBg.setBackgroundColor(Color.BLACK)
                fsBackdrop.isVisible = false
                overlay.setBackgroundColor(Color.BLACK)
                prepareDvdArtLayout()
            }
            else -> {
                solidBg.isVisible = false
                fsBackdrop.isVisible = true
                fsBackdrop.alpha = 0.35f
                overlay.setBackgroundColor(Color.parseColor("#E6282c34"))
                resetArtLayoutForStandard()
            }
        }
        refreshClock()
    }

    private fun resetArtLayoutForStandard() {
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        fsArt.layoutParams = lp
        fsArt.translationX = 0f
        fsArt.translationY = 0f
        fsArt.scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private fun prepareDvdArtLayout() {
        val size = dvdArtSizePx
        val lp = FrameLayout.LayoutParams(size, size)
        fsArt.layoutParams = lp
        fsArt.scaleType = ImageView.ScaleType.CENTER_CROP
        fsArt.translationX = 40f * root.resources.displayMetrics.density
        fsArt.translationY = 40f * root.resources.displayMetrics.density
    }

    private fun startDvd() {
        if (dvdRunning || videoMode) return
        val speed = dvdSpeedPxPerSec()
        dvdVx = speed
        dvdVy = speed * 0.72f
        lastDvdFrameMs = SystemClock.uptimeMillis()
        dvdRunning = true
        android.view.Choreographer.getInstance().postFrameCallback(dvdFrameCallback)
    }

    private fun stopDvd() {
        if (!dvdRunning) return
        dvdRunning = false
        android.view.Choreographer.getInstance().removeFrameCallback(dvdFrameCallback)
    }

    private fun dvdSpeedPxPerSec(): Float {
        val density = root.resources.displayMetrics.density
        return when (KeyValueStorage.getSettings().dvdSpeed) {
            FullscreenVisualizer.SPEED_SLOW -> 48f * density
            FullscreenVisualizer.SPEED_FAST -> 160f * density
            else -> 90f * density
        }
    }

    private fun stepDvd(nowMs: Long) {
        val dt = ((nowMs - lastDvdFrameMs).coerceAtMost(50)).toFloat() / 1000f
        lastDvdFrameMs = nowMs
        val parentW = fsMedia.width
        val parentH = fsMedia.height
        if (parentW <= 0 || parentH <= 0) return
        val artW = fsArt.width.takeIf { it > 0 } ?: dvdArtSizePx
        val artH = fsArt.height.takeIf { it > 0 } ?: dvdArtSizePx
        var x = fsArt.translationX + dvdVx * dt
        var y = fsArt.translationY + dvdVy * dt
        val maxX = (parentW - artW).toFloat().coerceAtLeast(0f)
        val maxY = (parentH - artH).toFloat().coerceAtLeast(0f)
        if (x <= 0f) {
            x = 0f
            dvdVx = kotlin.math.abs(dvdVx)
        } else if (x >= maxX) {
            x = maxX
            dvdVx = -kotlin.math.abs(dvdVx)
        }
        if (y <= 0f) {
            y = 0f
            dvdVy = kotlin.math.abs(dvdVy)
        } else if (y >= maxY) {
            y = maxY
            dvdVy = -kotlin.math.abs(dvdVy)
        }
        fsArt.translationX = x
        fsArt.translationY = y
    }

    private fun bindNext(nextSong: Song?) {
        if (nextSong != null) {
            fsNextRow.isVisible = true
            fsNextLabel.text = nextSong.title
            styleExtraBold(fsNextLabel)
            fsNextArtist.text = nextSong.artist
            styleBold(fsNextArtist)
            fsNextRow.contentDescription =
                root.context.getString(R.string.next_up) +
                    ": ${nextSong.title} — ${nextSong.artist}"
        } else {
            fsNextRow.isVisible = false
        }
    }

    private fun styleExtraBold(tv: TextView) {
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.paint.isFakeBoldText = true
        tv.paint.style = Paint.Style.FILL_AND_STROKE
        tv.paint.strokeWidth = 1.15f * root.resources.displayMetrics.density
        tv.invalidate()
    }

    private fun styleBold(tv: TextView) {
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.paint.isFakeBoldText = true
        tv.paint.style = Paint.Style.FILL
        tv.paint.strokeWidth = 0f
        tv.invalidate()
    }

    fun exit() {
        if (!active) return
        active = false
        hideHandler.removeCallbacks(hideRunnable)
        stopDvd()
        stopClockUpdates()
        if (videoMode) moveVideo(toFullscreen = false)
        videoMode = false
        resetArtLayoutForStandard()
        overlay.isVisible = false
        chrome.isVisible = true
        bind.setImmersive(false)
    }

    fun setPlaying(playing: Boolean) {
        fsPlay.setImageDrawable(
            ResourcesCompat.getDrawable(
                root.resources,
                if (playing) R.drawable.ic_pause_icon else R.drawable.ic_play,
                null
            )
        )
    }

    fun setShuffle(shuffling: Boolean) {
        fsShuffle.setImageDrawable(
            ResourcesCompat.getDrawable(
                root.resources,
                if (shuffling) R.drawable.ic_shuffle_fill_primary else R.drawable.ic_shuffle_fill,
                null
            )
        )
    }

    fun setRepeat(mode: RepeatMode) {
        val (icon, label) = when (mode) {
            RepeatMode.ALL -> R.drawable.ic_repeat_primary to R.string.repeat_queue
            RepeatMode.ONE -> R.drawable.ic_repeat_one_primary to R.string.repeat_one
            RepeatMode.OFF -> R.drawable.ic_repeat to R.string.repeat_off
        }
        fsRepeat.setImageDrawable(
            ResourcesCompat.getDrawable(root.resources, icon, null)
        )
        fsRepeat.contentDescription = root.context.getString(label)
    }

    fun setLiked(liked: Boolean) {
        fsLike.setImageResource(if (liked) R.drawable.ic_nav_like else R.drawable.ic_nav_unlike)
        fsLike.contentDescription = root.context.getString(
            if (liked) R.string.unlike_song else R.string.like_song
        )
    }

    fun setProgress(progressPct: Int, currentLabel: String, durationLabel: String) {
        if (!fsSeekBar.isPressed) fsSeekBar.progress = progressPct
        root.findViewById<TextView>(R.id.tv_fs_current_time).text = currentLabel
        root.findViewById<TextView>(R.id.tv_fs_duration).text = durationLabel
    }

    fun bumpControls() = showControls()

    private fun showControls() {
        if (!active) return
        fsControls.visibility = View.VISIBLE
        controlsVisible = true
        applyMetaVisibility()
        resetHideTimer()
    }

    private fun applyMetaVisibility() {
        if (videoMode) {
            fsMeta.isVisible = controlsVisible
            return
        }
        // DVD-style: hide title / artist / up-next until chrome is shown.
        fsMeta.isVisible =
            visualizerMode != FullscreenVisualizer.DVD || controlsVisible
    }

    private fun resetHideTimer() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 4_000)
    }

    private fun hideControls() {
        if (!active) return
        val focused = overlay.findFocus()
        if (focused != null && focused !== fsFocusAnchor && isControl(focused)) {
            lastFocusedControl = focused
        }
        fsControls.visibility = View.GONE
        controlsVisible = false
        applyMetaVisibility()
        fsFocusAnchor.requestFocus()
    }

    private fun startClockUpdates() {
        clockHandler.removeCallbacks(clockRunnable)
        refreshClock()
        clockHandler.post(clockRunnable)
    }

    private fun stopClockUpdates() {
        clockHandler.removeCallbacks(clockRunnable)
        fsClock.isVisible = false
    }

    private fun refreshClock() {
        if (!active) return
        val settings = KeyValueStorage.getSettings()
        val showTime = settings.fullscreenShowClock
        val showDate = settings.fullscreenShowDate
        fsClock.isVisible = showTime || showDate
        fsClockTime.isVisible = showTime
        fsClockDate.isVisible = showDate
        if (!showTime && !showDate) return
        val now = Date()
        val locale = Locale.getDefault()
        if (showTime) {
            fsClockTime.text = DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(now)
            styleExtraBold(fsClockTime)
        }
        if (showDate) {
            fsClockDate.text = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(now)
            styleBold(fsClockDate)
        }
    }

    private fun restoreFocus() {
        (lastFocusedControl?.takeIf { it.isShown } ?: fsPlay).requestFocus()
    }

    private fun rememberFocus(view: View) {
        lastFocusedControl = view
    }

    private fun isControl(view: View): Boolean =
        view === fsSeekBar || fsButtons.any { it === view }

    private fun isInteractionKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        -> true
        else -> false
    }

    private fun formatSec(seconds: Int): String =
        "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"

    private fun parseColorSafe(hex: String): Int = try {
        Color.parseColor(hex)
    } catch (_: Exception) {
        Color.parseColor(FullscreenVisualizer.DEFAULT_SOLID)
    }

    private fun moveVideo(toFullscreen: Boolean) {
        val parent = musicVideoContainer.parent as? ViewGroup
        parent?.removeView(musicVideoContainer)
        if (toFullscreen) {
            fsMedia.addView(
                musicVideoContainer,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            musicVideoContainer.isVisible = videoMode
        } else {
            mediaSlot.addView(
                musicVideoContainer,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }
}
