package app.sonicsound.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
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
import app.sonicsound.subsonic.SubsonicClient
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
    private lateinit var btnPlay: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnLike: ImageButton
    private lateinit var sbProgress: SeekBar
    private lateinit var playlistRecyclerView: RecyclerView
    private lateinit var playlistAdapter: SonicSoundPlaylistItemAdapter
    private lateinit var currentTimeText: TextView
    private lateinit var durationText: TextView
    private var musicVideo: NowPlayingMusicVideo? = null
    private var liked = false
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
                    btnPlay.setImageDrawable(
                        ResourcesCompat.getDrawable(resources, R.drawable.ic_pause_icon, null)
                    )
                    musicVideo?.onPlay()
                }
                "MSpaused" -> {
                    btnPlay.setImageDrawable(
                        ResourcesCompat.getDrawable(resources, R.drawable.ic_play, null)
                    )
                    musicVideo?.onPause()
                }
                "MSprogress" -> {
                    val time = JSObject(value)
                    val progress: Double? = try {
                        time.getDouble("time")
                    } catch (_: Exception) {
                        null
                    }
                    if (progress != null) {
                        sbProgress.progress = floor(progress * 100).toInt()
                        val state = bind.getCurrentState()
                        if (state != null) {
                            val dur = state.currentTrack.duration
                            currentTimeText.text =
                                secondsToHHSS(floor(progress * dur).toInt())
                            musicVideo?.onProgress(progress, dur)
                        }
                    }
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
        btnPlay = view.findViewById(R.id.btn_play_pause)
        btnPlay.setOnClickListener {
            val mv = musicVideo
            if (mv != null && mv.togglePlayPause()) {
                btnPlay.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        resources,
                        if (mv.isYtPlaying()) R.drawable.ic_pause_icon else R.drawable.ic_play,
                        null
                    )
                )
            } else {
                bind.playPause()
            }
        }
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
        sbProgress = view.findViewById(R.id.sb_now_playing_progress)
        sbProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentTimeText.text = secondsToHHSS(
                        floor(
                            (progress / 100.0) *
                                (bind.getCurrentState()?.currentTrack?.duration ?: 0)
                        ).toInt()
                    )
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seekBar?.progress ?: return
                val state = bind.getCurrentState()
                val mv = musicVideo
                if (mv != null && mv.isVideoActive && state != null) {
                    mv.onUserSeek(progress / 100f, state.currentTrack.duration)
                } else {
                    bind.seek(progress / 100f)
                }
            }
        })
        currentTimeText = view.findViewById(R.id.tv_current_time)
        durationText = view.findViewById(R.id.tv_track_duration)
        image.clipToOutline = true
        musicVideo = NowPlayingMusicVideo(
            this,
            bind,
            image,
            view.findViewById(R.id.fl_music_video),
            view.findViewById(R.id.btn_music_video),
        )

        playlistRecyclerView = view.findViewById(R.id.rv_now_playing_playlist)
        val manager = LinearLayoutManager(this.context)
        manager.orientation = RecyclerView.VERTICAL
        val itemH = (48 * resources.displayMetrics.density + 0.5f).toInt()
        playlistAdapter = SonicSoundPlaylistItemAdapter(
            listOf(),
            requireContext(),
            playlistRecyclerView,
            manager,
            itemH,
        ) { index ->
            bind.skipTo(index)
        }
        playlistRecyclerView.setHasFixedSize(true)
        playlistRecyclerView.layoutManager = manager
        playlistRecyclerView.adapter = playlistAdapter
        playlistRecyclerView.isFocusable = true
        playlistRecyclerView.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        getCurrentState()
    }

    private fun secondsToHHSS(seconds: Int): String {
        return "${(seconds / 60).toString().padStart(2, '0')}:${
            (seconds % 60).toString().padStart(2, '0')
        }"
    }

    @SuppressLint("SetTextI18n")
    fun getCurrentState() {
        if (!::bind.isInitialized || !::client.isInitialized) return
        val currentState: CurrentState? = bind.getCurrentState()
        if (currentState != null && currentState.currentTrack.id != "") {
            firstLine.text = currentState.currentTrack.title
            secondLine.text =
                "by ${currentState.currentTrack.artist} from ${currentState.currentTrack.album}"
            val artUrl = client.getAlbumArt(currentState.currentTrack.albumId)
            image.loadUrl(artUrl)
            backdrop.loadUrl(artUrl)
            backdrop.alpha = 0.35f
            durationText.text = secondsToHHSS(currentState.currentTrack.duration)
            btnPlay.setImageDrawable(
                ResourcesCompat.getDrawable(
                    resources,
                    if (currentState.playing) R.drawable.ic_pause_icon else R.drawable.ic_play,
                    null
                )
            )
            btnShuffle.setImageDrawable(
                ResourcesCompat.getDrawable(
                    resources,
                    if (currentState.shuffling) {
                        R.drawable.ic_shuffle_fill_primary
                    } else {
                        R.drawable.ic_shuffle_fill
                    },
                    null
                )
            )
            liked = currentState.currentTrack.isStarred
            updateLikeUi()
            val entries = bind.getCurrentPlaylist()?.entry.orEmpty()
            if (entries.isNotEmpty()) {
                for (song in entries) {
                    song.image = client.getAlbumArt(song.albumId)
                }
                playlistAdapter.setNewDataSet(entries)
                val idx = entries.indexOfFirst { it.id == currentState.currentTrack.id }
                playlistAdapter.updateSelected(idx)
            }
        }
    }

    private fun updateLikeUi() {
        if (!::btnLike.isInitialized) return
        btnLike.setImageResource(
            if (liked) R.drawable.ic_nav_like else R.drawable.ic_nav_unlike
        )
        btnLike.contentDescription =
            getString(if (liked) R.string.unlike_song else R.string.like_song)
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
                Toast.makeText(
                    requireContext(),
                    R.string.like_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
