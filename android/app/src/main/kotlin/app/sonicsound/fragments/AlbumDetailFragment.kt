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

/** Browse album tracks; play starts Now Playing (no autoplay on album card select). */
class AlbumDetailFragment : Fragment {
    private lateinit var client: SubsonicClient
    private lateinit var bind: TvActivity.TvActivityBind
    private var albumId: String? = null
    private var titleOverride: String? = null
    private var songs: List<Song> = emptyList()

    constructor() : super()

    constructor(
        client: SubsonicClient,
        bind: TvActivity.TvActivityBind,
        albumId: String,
        title: String,
    ) : super() {
        this.client = client
        this.bind = bind
        this.albumId = albumId
        this.titleOverride = title
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.fragment_album_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!::client.isInitialized || !::bind.isInitialized) return
        val id = albumId ?: return
        val title = view.findViewById<TextView>(R.id.tv_album_detail_title)
        val subtitle = view.findViewById<TextView>(R.id.tv_album_detail_subtitle)
        val empty = view.findViewById<TextView>(R.id.tv_album_detail_empty)
        val recycler = view.findViewById<RecyclerView>(R.id.rv_album_songs)
        titleOverride?.let { title.text = it }
        view.findViewById<Button>(R.id.btn_album_play_all).setOnClickListener {
            if (songs.isNotEmpty()) bind.playAlbum(id, 0)
        }
        val manager = LinearLayoutManager(requireContext())
        val itemH = (48 * resources.displayMetrics.density + 0.5f).toInt()
        val adapter = SonicSoundPlaylistItemAdapter(
            listOf(),
            requireContext(),
            recycler,
            manager,
            itemH,
        ) { index ->
            bind.playAlbum(id, index)
        }
        recycler.layoutManager = manager
        recycler.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            val album = withContext(Dispatchers.IO) { client.getAlbum(id) }
            songs = album.song
            title.text = album.name
            subtitle.text = listOfNotNull(
                album.artist.takeIf { it.isNotBlank() },
                album.year.takeIf { it > 0 }?.toString(),
                "${songs.size} songs",
            ).joinToString(" · ")
            songs.forEach { it.image = client.getAlbumArt(it.coverArt.ifBlank { album.coverArt }) }
            empty.isVisible = songs.isEmpty()
            recycler.isVisible = songs.isNotEmpty()
            adapter.setNewDataSet(songs)
            if (songs.isNotEmpty()) {
                adapter.focusItem(0)
            } else {
                view.findViewById<Button>(R.id.btn_album_play_all).requestFocus()
            }
        }
    }
}
