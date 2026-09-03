package app.sonicsound.fragments

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import app.sonicsound.R
import app.sonicsound.TvActivity
import app.sonicsound.models.Song
import app.sonicsound.playback.RepeatMode

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
    private val wmpVisualizer = root.findViewById<app.sonicsound.visualizer.WmpVisualizerView>(R.id.wmp_visualizer)
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
    private val fsVisualizer: ImageButton = root.findViewById(R.id.btn_fs_visualizer)
    private val fsShuffle: ImageButton = root.findViewById(R.id.btn_fs_shuffle)
    private val fsRepeat: ImageButton = root.findViewById(R.id.btn_fs_repeat)
    private val fsLike: ImageButton = root.findViewById(R.id.btn_fs_like)
    private val fsMusicVideo: ImageButton = root.findViewById(R.id.btn_fs_music_video)
    private val fsToast: TextView = root.findViewById(R.id.tv_fs_toast)
    private val fsSeekBar = root.findViewById<android.widget.SeekBar>(R.id.sb_fs_progress)
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }
    private val chromeUi = NowPlayingFsChrome(
        root = root,
        fsClock = fsClock,
        fsClockTime = fsClockTime,
        fsClockDate = fsClockDate,
        fsToast = fsToast,
        isActive = { active },
    )
    private val fsScrubber = NowPlayingScrubber(
        root.findViewById(R.id.ll_fs_scrubber),
        fsSeekBar,
        durationProvider = durationProvider,
        onSeek = onSeek,
        onTimePreview = { sec ->
            val labels = timeLabels()
            setProgress(fsSeekBar.progress, chromeUi.formatSec(sec), labels.second)
        },
    )
    private val fsButtons = mutableListOf<ImageButton>()
    private var lastFocusedControl: View? = null
    var active = false
        private set
    private var videoMode = false
    private var controlsVisible = true

    private val visualizer = NowPlayingVisualizerHost(
        root = root,
        overlay = overlay,
        solidBg = solidBg,
        fsBackdrop = fsBackdrop,
        fsArt = fsArt,
        fsMedia = fsMedia,
        wmpVisualizer = wmpVisualizer,
        fsVisualizer = fsVisualizer,
        isVideoMode = { videoMode },
        isActive = { active },
        isPlaying = { bind.getCurrentState()?.playing == true },
        onChromeApplied = { chromeUi.refreshClock() },
        onToast = { chromeUi.showAppToast(it) },
    )

    init {
        fsScrubber.wire()
        chromeUi.styleExtraBold(fsClockTime)
        chromeUi.styleBold(fsClockDate)
        if (enableMusicVideo) {
            fsMusicVideo.visibility = View.VISIBLE
            fsMusicVideo.isFocusable = true
            fsButtons.addAll(
                listOf(fsShuffle, fsRepeat, fsPrev, fsPlay, fsNextBtn, fsVisualizer, fsMusicVideo, fsLike),
            )
        } else {
            fsMusicVideo.visibility = View.GONE
            fsMusicVideo.isFocusable = false
            fsButtons.addAll(
                listOf(fsShuffle, fsRepeat, fsPrev, fsPlay, fsNextBtn, fsVisualizer, fsLike),
            )
        }
        visualizer.refreshButton()
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
                    R.id.btn_fs_visualizer -> visualizer.cycle()
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
        visualizer.applyChrome()
        applyTrack(artUrl, title, subtitle, nextSong, video)
        lastFocusedControl = fsPlay
        showControls()
        fsPlay.requestFocus()
        chromeUi.startClockUpdates()
    }

    fun reloadVisualizerFromSettings() {
        visualizer.reloadFromSettings(reloadArt = true)
        applyMetaVisibility()
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
        chromeUi.styleExtraBold(fsTitle)
        fsSubtitle.text = subtitle
        chromeUi.styleBold(fsSubtitle)
        bindNext(nextSong)
        if (!video) visualizer.applyChrome()
        when {
            video && !wasVideo -> {
                visualizer.stopAll()
                visualizer.resetArtLayoutForStandard()
                fsArt.isVisible = false
                fsBackdrop.isVisible = true
                solidBg.isVisible = false
                moveVideo(toFullscreen = true)
            }
            !video && wasVideo -> {
                moveVideo(toFullscreen = false)
                fsArt.isVisible = true
                visualizer.applyChrome()
                visualizer.loadArtForMode(artUrl)
            }
            !video -> visualizer.loadArtForMode(artUrl)
        }
        visualizer.syncMotionForMode()
        applyMetaVisibility()
    }

    private fun bindNext(nextSong: Song?) {
        if (nextSong != null) {
            fsNextRow.isVisible = true
            fsNextLabel.text = nextSong.title
            chromeUi.styleExtraBold(fsNextLabel)
            fsNextArtist.text = nextSong.artist
            chromeUi.styleBold(fsNextArtist)
            fsNextRow.contentDescription =
                root.context.getString(R.string.next_up) +
                    ": ${nextSong.title} — ${nextSong.artist}"
        } else {
            fsNextRow.isVisible = false
        }
    }



    fun exit() {
        if (!active) return
        active = false
        hideHandler.removeCallbacks(hideRunnable)
        chromeUi.cancelToast()
        visualizer.stopAll()
        chromeUi.stopClockUpdates()
        if (videoMode) moveVideo(toFullscreen = false)
        videoMode = false
        visualizer.resetArtLayoutForStandard()
        visualizer.clearArtLoads()
        overlay.isVisible = false
        chrome.isVisible = true
        bind.setImmersive(false)
    }

    fun releaseResources() {
        if (active) {
            exit()
        } else {
            hideHandler.removeCallbacks(hideRunnable)
            visualizer.stopAll()
            chromeUi.stopClockUpdates()
            visualizer.clearArtLoads()
            bind.setImmersive(false)
        }
    }

    fun setPlaying(playing: Boolean) =
        NowPlayingFsTransportUi.setPlaying(root, fsPlay, visualizer, playing)

    fun setShuffle(shuffling: Boolean) =
        NowPlayingFsTransportUi.setShuffle(root, fsShuffle, shuffling)

    fun setRepeat(mode: RepeatMode) =
        NowPlayingFsTransportUi.setRepeat(root, fsRepeat, mode)

    fun setLiked(liked: Boolean) =
        NowPlayingFsTransportUi.setLiked(root, fsLike, liked)


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
        fsMeta.isVisible = when {
            visualizer.metaHidesUntilControls() -> controlsVisible
            else -> true
        }
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


    private fun moveVideo(toFullscreen: Boolean) {
        NowPlayingFsVideoSlot.move(musicVideoContainer, mediaSlot, fsMedia, toFullscreen, videoMode)
    }
}
