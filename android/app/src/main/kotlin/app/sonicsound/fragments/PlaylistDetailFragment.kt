package app.sonicsound.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.sonicsound.R
import app.sonicsound.TvActivity
import app.sonicsound.adapters.SonicSoundPlaylistItemAdapter
import app.sonicsound.models.Song
import app.sonicsound.subsonic.SubsonicClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistDetailFragment : Fragment {
    private lateinit var client: SubsonicClient
    private lateinit var bind: TvActivity.TvActivityBind
    private var playlistId: String? = null
    private var titleOverride: String? = null
    private var songs: List<Song> = emptyList()

    constructor() : super()

    constructor(
        client: SubsonicClient,
        bind: TvActivity.TvActivityBind,
        playlistId: String,
        title: String,
    ) : super() {
        this.client = client
        this.bind = bind
        this.playlistId = playlistId
        this.titleOverride = title
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.fragment_playlist_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!::client.isInitialized || !::bind.isInitialized) return
        val id = playlistId ?: return
        val title = view.findViewById<TextView>(R.id.tv_playlist_detail_title)
        val subtitle = view.findViewById<TextView>(R.id.tv_playlist_detail_subtitle)
        val empty = view.findViewById<TextView>(R.id.tv_playlist_detail_empty)
        val recycler = view.findViewById<RecyclerView>(R.id.rv_playlist_songs)
        titleOverride?.let { title.text = it }

        view.findViewById<Button>(R.id.btn_playlist_play).setOnClickListener {
            bind.playPlaylist(id, 0)
        }
        view.findViewById<Button>(R.id.btn_playlist_rename).setOnClickListener {
            TvLibraryDialogs.showRenamePlaylist(
                this,
                client,
                id,
                title.text.toString(),
            ) { loadPlaylist(id, title, subtitle, empty, recycler) }
        }
        view.findViewById<Button>(R.id.btn_playlist_delete).setOnClickListener {
            TvLibraryDialogs.showDeletePlaylist(
                this,
                client,
                id,
                title.text.toString(),
            ) {
                parentFragmentManager.popBackStack()
            }
        }

        val manager = LinearLayoutManager(requireContext())
        val itemH = (48 * resources.displayMetrics.density + 0.5f).toInt()
        val adapter = SonicSoundPlaylistItemAdapter(
            listOf(),
            requireContext(),
            recycler,
            manager,
            itemH,
            onItemClick = { index -> bind.playPlaylist(id, index) },
            onItemLongClick = { index ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) { client.removeFromPlaylist(id, index) }
                        loadPlaylist(id, title, subtitle, empty, recycler)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(
                            requireContext(),
                            e.message ?: getString(R.string.error_generic),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                true
            },
        )
        recycler.layoutManager = manager
        recycler.adapter = adapter
        loadPlaylist(id, title, subtitle, empty, recycler)
    }

    private fun loadPlaylist(
        id: String,
        title: TextView,
        subtitle: TextView,
        empty: TextView,
        recycler: RecyclerView,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val playlist = withContext(Dispatchers.IO) { client.getPlaylist(id) }
            songs = playlist.entry.orEmpty()
            title.text = playlist.name
            subtitle.text = listOfNotNull(
                playlist.comment?.takeIf { it.isNotBlank() },
                playlist.owner.takeIf { it.isNotBlank() }?.let { getString(R.string.playlist_by, it) },
                getString(R.string.playlist_song_count, songs.size),
            ).joinToString(" · ")
            songs.forEach { it.image = client.getAlbumArt(it.coverArt.ifBlank { playlist.coverArt ?: "" }) }
            empty.isVisible = songs.isEmpty()
            recycler.isVisible = songs.isNotEmpty()
            (recycler.adapter as? SonicSoundPlaylistItemAdapter)?.setNewDataSet(songs)
            if (songs.isNotEmpty()) {
                (recycler.adapter as? SonicSoundPlaylistItemAdapter)?.focusItem(0)
            } else {
                view?.findViewById<Button>(R.id.btn_playlist_play)?.requestFocus()
            }
        }
    }
}
