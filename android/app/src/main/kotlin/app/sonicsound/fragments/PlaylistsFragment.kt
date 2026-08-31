package app.sonicsound.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.sonicsound.R
import app.sonicsound.TvActivity
import app.sonicsound.adapters.SonicSoundCardAdapter
import app.sonicsound.models.ICardViewModel
import app.sonicsound.models.Playlist
import app.sonicsound.subsonic.SubsonicClient
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistsFragment : Fragment {
    private lateinit var client: SubsonicClient
    private lateinit var bind: TvActivity.TvActivityBind
    private lateinit var playlistsRecycler: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var newButton: Button
    private var density: Float = 0f
    private var width: Int = 0
    private var adapter: SonicSoundCardAdapter? = null

    constructor() : super()

    constructor(client: SubsonicClient, bind: TvActivity.TvActivityBind) : super() {
        this.client = client
        this.bind = bind
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.fragment_playlists, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!::client.isInitialized || !::bind.isInitialized) return
        playlistsRecycler = view.findViewById(R.id.rv_playlists)
        emptyView = view.findViewById(R.id.tv_playlists_empty)
        newButton = view.findViewById(R.id.btn_new_playlist)
        newButton.setOnClickListener {
            TvLibraryDialogs.showCreatePlaylist(this, client) { refreshPlaylists() }
        }
        view.post {
            if (!isAdded || view !== this.view) return@post
            density = resources.displayMetrics.density
            width = view.width
            refreshPlaylists()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::client.isInitialized && view != null) {
            refreshPlaylists()
        }
    }

    private fun refreshPlaylists() {
        if (!isAdded || !::client.isInitialized) return
        viewLifecycleOwner.lifecycleScope.launch {
            val playlists: List<ICardViewModel> = withContext(Dispatchers.IO) {
                client.getPlaylists().onEach { playlist ->
                    playlist.image = client.getAlbumArt(playlist.coverArt ?: "")
                }
            }
            if (!isAdded) return@launch
            emptyView.isVisible = playlists.isEmpty()
            newButton.isVisible = true
            playlistsRecycler.isVisible = playlists.isNotEmpty()
            if (adapter == null) {
                adapter = SonicSoundCardAdapter(
                    playlists,
                    playlistsRecycler,
                    bind,
                    onItem = { item ->
                        if (item is Playlist) {
                            bind.showPlaylist(item.id, item.name)
                            true
                        } else {
                            false
                        }
                    },
                    onItemLongClick = { item ->
                        if (item is Playlist) {
                            TvLibraryDialogs.showDeletePlaylist(
                                this@PlaylistsFragment,
                                client,
                                item.id,
                                item.name,
                            ) { refreshPlaylists() }
                            true
                        } else {
                            false
                        }
                    },
                )
                setUpRecyclerView(playlistsRecycler, adapter!!)
                playlistsRecycler.layoutManager?.getChildAt(0)?.post {
                    val child = playlistsRecycler.layoutManager?.getChildAt(0) ?: return@post
                    val w = child.width + child.marginLeft + child.marginRight
                    if (w > 0) {
                        val columns = ceil(width / w.toFloat()).toInt().coerceAtLeast(1)
                        (playlistsRecycler.layoutManager as GridLayoutManager).spanCount = columns
                    }
                }
            } else {
                adapter!!.setNewDataSet(playlists)
            }
        }
    }

    private fun setUpRecyclerView(rc: RecyclerView, a: SonicSoundCardAdapter) {
        rc.setHasFixedSize(true)
        val columns = ceil(width / (170 * density + 0.5f)).toInt().coerceAtLeast(1)
        rc.layoutManager = GridLayoutManager(context, columns)
        rc.adapter = a
    }
}
