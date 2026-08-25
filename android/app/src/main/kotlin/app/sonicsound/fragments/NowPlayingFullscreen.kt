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
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import app.sonicsound.R
import app.sonicsound.TvActivity
import app.sonicsound.extensions.loadUrl
import app.sonicsound.models.Song

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
    private val onLike: () -> Unit,
    private val onMusicVideo: () -> Unit,
    private val durationProvider: () -> Int,
    private val timeLabels: () -> Pair<String, String>,
) {
    private val overlay: FrameLayout = root.findViewById(R.id.fl_fullscreen_overlay)
    private val fsBackdrop: ImageView = root.findViewById(R.id.img_fs_backdrop)
    private val fsArt: ImageView = root.findViewById(R.id.img_fs_art)
    private val fsMedia: FrameLayout = root.findViewById(R.id.fl_fs_media)
    private val fsFocusAnchor: View = root.findViewById(R.id.v_fs_focus_anchor)
    private val fsTitle: TextView = root.findViewById(R.id.tv_fs_title)
    private val fsSubtitle: TextView = root.findViewById(R.id.tv_fs_subtitle)
    private val fsNextRow: LinearLayout = root.findViewById(R.id.ll_fs_next)
    private val fsNextLabel: TextView = root.findViewById(R.id.tv_fs_next_label)
    private val fsControls: LinearLayout = root.findViewById(R.id.ll_fs_controls)
    private val fsPlay: ImageButton = root.findViewById(R.id.btn_fs_play)
    private val fsPrev: ImageButton = root.findViewById(R.id.btn_fs_prev)
    private val fsNextBtn: ImageButton = root.findViewById(R.id.btn_fs_next)
    private val fsShuffle: ImageButton = root.findViewById(R.id.btn_fs_shuffle)
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
    private val fsButtons = mutableListOf<ImageButton>()
    private var lastFocusedControl: View? = null
    var active = false
        private set
    private var videoMode = false
    private var controlsVisible = true

    init {
        fsScrubber.wire()
        fsButtons.addAll(listOf(fsShuffle, fsPrev, fsPlay, fsNextBtn, fsMusicVideo, fsLike))
        fsButtons.forEach { btn ->
            btn.setOnClickListener {
                rememberFocus(btn)
                when (btn.id) {
                    R.id.btn_fs_prev -> bind.prev()
                    R.id.btn_fs_next -> bind.next()
                    R.id.btn_fs_play -> onPlayPause()
                    R.id.btn_fs_shuffle -> onShuffle()
                    R.id.btn_fs_like -> onLike()
                    R.id.btn_fs_music_video -> onMusicVideo()
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
        applyTrack(artUrl, title, subtitle, nextSong, video)
        lastFocusedControl = fsPlay
        showControls()
        fsPlay.requestFocus()
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
        fsBackdrop.loadUrl(artUrl)
        fsTitle.text = title
        fsSubtitle.text = subtitle
        bindNext(nextSong)
        when {
            video && !wasVideo -> {
                fsArt.isVisible = false
                moveVideo(toFullscreen = true)
            }
            !video && wasVideo -> {
                moveVideo(toFullscreen = false)
                fsArt.isVisible = true
                fsArt.loadUrl(artUrl)
            }
            !video -> fsArt.loadUrl(artUrl)
        }
    }

    private fun bindNext(nextSong: Song?) {
        if (nextSong != null) {
            fsNextRow.isVisible = true
            fsNextLabel.text = nextSong.title
            fsNextRow.contentDescription =
                root.context.getString(R.string.next_up) + ": ${nextSong.title}"
        } else {
            fsNextRow.isVisible = false
        }
    }

    fun exit() {
        if (!active) return
        active = false
        hideHandler.removeCallbacks(hideRunnable)
        if (videoMode) moveVideo(toFullscreen = false)
        videoMode = false
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
        fsControls.isVisible = true
        controlsVisible = true
        resetHideTimer()
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
        fsControls.isVisible = false
        controlsVisible = false
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

    private fun formatSec(seconds: Int): String =
        "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"

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
