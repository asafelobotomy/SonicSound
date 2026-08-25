package app.sonicsound.fragments

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.sonicsound.CurrentState
import app.sonicsound.Globals
import app.sonicsound.IBroadcastObserver
import app.sonicsound.R
import app.sonicsound.TvActivity
import app.sonicsound.adapters.SonicSoundPlaylistItemAdapter
import app.sonicsound.extensions.loadUrl
import app.sonicsound.extensions.requestPrimaryFocus
import app.sonicsound.subsonic.SubsonicClient
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.getcapacitor.JSObject
import kotlin.math.floor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NowPlayingFragment : Fragment {
    private lateinit var bind: TvActivity.TvActivityBind
    private lateinit var client: SubsonicClient
    private lateinit var firstLine: TextView
    private lateinit var secondLine: TextView
    private lateinit var image: ImageView
    private lateinit var backdrop: ImageView
    private lateinit var mediaFrame: FrameLayout
    private lateinit var chrome: View
    private lateinit var btnPlay: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnLike: ImageButton
    private lateinit var sbProgress: SeekBar
    private lateinit var playlistRecyclerView: RecyclerView
    private lateinit var playlistAdapter: SonicSoundPlaylistItemAdapter
    private lateinit var currentTimeText: TextView
    private lateinit var durationText: TextView
    private var scrubber: NowPlayingScrubber? = null
    private var musicVideo: NowPlayingMusicVideo? = null
    private var fullscreen: NowPlayingFullscreen? = null
    private var liked = false
    private var keepQueueFocus = false
    private var lastQueueIds = ""
    private val observer = NowPlayingObserver()

    constructor() : super()

    constructor(bind: TvActivity.TvActivityBind, client: SubsonicClient) : super() {
        this.bind = bind
        this.client = client
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (::bind.isInitialized) Globals.RegisterObserver(observer)
    }

    override fun onDestroy() {
        musicVideo?.destroy()
        if (::bind.isInitialized) Globals.UnregisterObserver(observer)
        super.onDestroy()
    }

    /** @return true if back was consumed (scrub disarm or exit fullscreen). */
    fun handleBackPress(): Boolean {
        if (scrubber?.disarmOnBack() == true) return true
        val fs = fullscreen ?: return false
        if (!fs.active) return false
        if (fs.handleBackPress()) return true
        fs.exit()
        mediaFrame.requestFocus()
        return true
    }

    inner class NowPlayingObserver : IBroadcastObserver {
        override fun update(action: String?, value: String?) {
            if (!::btnPlay.isInitialized) return
            when (action) {
                "MSplaylistUpdated", "MScurrentTrack" -> {
                    getCurrentState()
                    musicVideo?.onTrackChanged(
                        bind.getCurrentState()?.currentTrack,
                        bind.getCurrentState()?.playing == true
                    )
                }
                "MSplay" -> {
                    setPlayingUi(true)
                    musicVideo?.onPlay()
                }
                "MSpaused" -> {
                    setPlayingUi(false)
                    musicVideo?.onPause()
                }
                "MSprogress" -> {
                    val progress = JSObject(value).optDouble("time", Double.NaN)
                    if (!progress.isNaN()) applyProgress(progress)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.fragment_now_playing, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!::client.isInitialized || !::bind.isInitialized) return
        chrome = view.findViewById(R.id.ll_now_playing_chrome)
        btnPlay = view.findViewById(R.id.btn_play_pause)
        btnPlay.setOnClickListener { togglePlayPause() }
        view.findViewById<ImageButton>(R.id.btn_prev).setOnClickListener { bind.prev() }
        view.findViewById<ImageButton>(R.id.btn_next).setOnClickListener { bind.next() }
        btnShuffle = view.findViewById(R.id.btn_shuffle)
        btnShuffle.setOnClickListener { bind.shuffle() }
        btnLike = view.findViewById(R.id.btn_like)
        btnLike.setOnClickListener { toggleLike() }
        firstLine = view.findViewById(R.id.tv_now_playing_first_line)
        secondLine = view.findViewById(R.id.tv_now_playing_second_line)
        image = view.findViewById(R.id.img_now_playing_album_art)
        backdrop = view.findViewById(R.id.img_now_playing_backdrop)
        mediaFrame = view.findViewById(R.id.fl_now_playing_media)
        mediaFrame.setOnClickListener { enterFullscreen() }
        mediaFrame.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                enterFullscreen()
                true
            } else {
                false
            }
        }
        sbProgress = view.findViewById(R.id.sb_now_playing_progress)
        currentTimeText = view.findViewById(R.id.tv_current_time)
        durationText = view.findViewById(R.id.tv_track_duration)
        scrubber = NowPlayingScrubber(
            view.findViewById(R.id.ll_scrubber_row),
            sbProgress,
            durationProvider = { bind.getCurrentState()?.currentTrack?.duration ?: 0 },
            onSeek = { seekTo(it) },
            onTimePreview = { currentTimeText.text = secondsToHHSS(it) },
        ).also { it.wire(); it.updateUi() }
        val mvContainer = view.findViewById<FrameLayout>(R.id.fl_music_video)
        val mvButton = view.findViewById<View>(R.id.btn_music_video)
        musicVideo = NowPlayingMusicVideo(this, bind, image, mvContainer, mvButton)
        fullscreen = NowPlayingFullscreen(
            view, bind, chrome, mediaFrame, mvContainer,
            onPlayPause = { togglePlayPause() },
            onSeek = { seekTo(it) },
            onShuffle = { bind.shuffle() },
            onLike = { toggleLike() },
            onMusicVideo = { mvButton.performClick() },
            durationProvider = { bind.getCurrentState()?.currentTrack?.duration ?: 0 },
            timeLabels = {
                currentTimeText.text.toString() to durationText.text.toString()
            },
        )
        playlistRecyclerView = view.findViewById(R.id.rv_now_playing_playlist)
        val manager = LinearLayoutManager(this.context)
        val itemH = (48 * resources.displayMetrics.density + 0.5f).toInt()
        playlistAdapter = SonicSoundPlaylistItemAdapter(
            listOf(), requireContext(), playlistRecyclerView, manager, itemH,
        ) { index ->
            keepQueueFocus = true
            bind.skipTo(index)
        }
        playlistRecyclerView.setHasFixedSize(true)
        playlistRecyclerView.layoutManager = manager
        playlistRecyclerView.adapter = playlistAdapter
        playlistRecyclerView.isFocusable = true
        playlistRecyclerView.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        lastQueueIds = ""
        getCurrentState()
        view.requestPrimaryFocus()
    }

    private fun togglePlayPause() {
        val mv = musicVideo
        if (mv != null && mv.togglePlayPause()) {
            setPlayingUi(mv.isYtPlaying())
        } else {
            bind.playPause()
        }
        fullscreen?.bumpControls()
    }

    private fun seekTo(fraction: Float) {
        val state = bind.getCurrentState()
        val mv = musicVideo
        if (mv != null && mv.isVideoActive && state != null) {
            mv.onUserSeek(fraction, state.currentTrack.duration)
        } else {
            bind.seek(fraction)
        }
    }

    private fun enterFullscreen() {
        val state = bind.getCurrentState() ?: return
        if (state.currentTrack.id.isBlank()) return
        val artUrl = client.getAlbumArt(state.currentTrack.albumId)
        val entries = bind.getCurrentPlaylist()?.entry.orEmpty()
        val idx = entries.indexOfFirst { it.id == state.currentTrack.id }
        val next = entries.getOrNull(idx + 1)
        fullscreen?.enter(
            artUrl,
            state.currentTrack.title,
            state.currentTrack.artist,
            next,
            musicVideo?.isVideoActive == true,
        )
        fullscreen?.setPlaying(state.playing || (musicVideo?.isYtPlaying() == true))
        fullscreen?.setShuffle(state.shuffling)
        fullscreen?.setLiked(state.currentTrack.isStarred)
        fullscreen?.setProgress(
            sbProgress.progress,
            currentTimeText.text.toString(),
            durationText.text.toString(),
        )
    }

    private fun setPlayingUi(playing: Boolean) {
        val icon = if (playing) R.drawable.ic_pause_icon else R.drawable.ic_play
        btnPlay.setImageDrawable(ResourcesCompat.getDrawable(resources, icon, null))
        fullscreen?.setPlaying(playing)
    }

    private fun applyProgress(progress: Double) {
        val pct = floor(progress * 100).toInt()
        if (scrubber?.armed != true) sbProgress.progress = pct
        val state = bind.getCurrentState() ?: return
        val dur = state.currentTrack.duration
        val current = secondsToHHSS(floor(progress * dur).toInt())
        currentTimeText.text = current
        fullscreen?.setProgress(pct, current, durationText.text.toString())
        musicVideo?.onProgress(progress, dur)
    }

    private fun secondsToHHSS(seconds: Int): String =
        "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"

    private fun loadAlbumArt(url: String) {
        if (!::image.isInitialized) return
        if (url.isBlank()) {
            image.loadUrl("")
            return
        }
        Glide.with(this).asBitmap().load(url).into(object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                image.setImageBitmap(resource)
                applyMediaAspect(resource.width, resource.height)
            }

            override fun onLoadCleared(placeholder: Drawable?) {}
            override fun onLoadFailed(errorDrawable: Drawable?) {
                image.loadUrl("")
            }
        })
        backdrop.loadUrl(url)
        backdrop.alpha = 0.35f
    }

    private fun applyMediaAspect(widthPx: Int, heightPx: Int) {
        if (!::mediaFrame.isInitialized || heightPx <= 0) return
        val ratio = widthPx.toFloat() / heightPx.toFloat()
        val params = mediaFrame.layoutParams as ConstraintLayout.LayoutParams
        params.dimensionRatio = when {
            ratio in 1.20f..1.45f -> "H,4:3"
            ratio < 1.15f -> "H,1:1"
            else -> "H,16:9"
        }
        mediaFrame.layoutParams = params
        image.scaleType = ImageView.ScaleType.FIT_CENTER
    }

    @SuppressLint("SetTextI18n")
    fun getCurrentState() {
        if (!::bind.isInitialized || !::client.isInitialized) return
        val currentState: CurrentState? = bind.getCurrentState()
        if (currentState == null || currentState.currentTrack.id == "") return
        firstLine.text = currentState.currentTrack.title
        secondLine.text = currentState.currentTrack.artist
        val artUrl = client.getAlbumArt(currentState.currentTrack.albumId)
        loadAlbumArt(artUrl)
        durationText.text = secondsToHHSS(currentState.currentTrack.duration)
        setPlayingUi(currentState.playing)
        btnShuffle.setImageDrawable(
            ResourcesCompat.getDrawable(
                resources,
                if (currentState.shuffling) R.drawable.ic_shuffle_fill_primary
                else R.drawable.ic_shuffle_fill,
                null
            )
        )
        liked = currentState.currentTrack.isStarred
        updateLikeUi()
        val entries = bind.getCurrentPlaylist()?.entry.orEmpty()
        val idx = entries.indexOfFirst { it.id == currentState.currentTrack.id }
        val next = entries.getOrNull(idx + 1)
        if (entries.isNotEmpty()) {
            val ids = entries.joinToString(",") { it.id }
            if (ids != lastQueueIds) {
                lastQueueIds = ids
                for (song in entries) song.image = client.getAlbumArt(song.albumId)
                playlistAdapter.setNewDataSet(entries)
            }
            val retain = keepQueueFocus || playlistRecyclerView.hasFocus()
            keepQueueFocus = false
            playlistAdapter.updateSelected(idx, keepFocus = retain)
        }
        if (fullscreen?.active == true) {
            fullscreen?.updateTrack(
                artUrl,
                currentState.currentTrack.title,
                currentState.currentTrack.artist,
                next,
                musicVideo?.isVideoActive == true,
            )
            fullscreen?.setPlaying(currentState.playing)
            fullscreen?.setShuffle(currentState.shuffling)
            fullscreen?.setLiked(liked)
        }
    }

    private fun updateLikeUi() {
        if (!::btnLike.isInitialized) return
        btnLike.setImageResource(if (liked) R.drawable.ic_nav_like else R.drawable.ic_nav_unlike)
        btnLike.contentDescription =
            getString(if (liked) R.string.unlike_song else R.string.like_song)
        fullscreen?.setLiked(liked)
    }

    private fun toggleLike() {
        val track = bind.getCurrentState()?.currentTrack ?: return
        if (track.id.isBlank()) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (liked) client.unstar(track.id) else client.star(track.id)
                }
                liked = !liked
                track.starred = if (liked) "now" else null
                updateLikeUi()
            } catch (_: Exception) {
                Toast.makeText(requireContext(), R.string.like_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
