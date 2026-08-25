package app.sonicsound.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.sonicsound.R
import app.sonicsound.TvActivity
import app.sonicsound.adapters.SonicSoundCardAdapter
import app.sonicsound.models.ICardViewModel
import app.sonicsound.subsonic.SubsonicClient
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchFragment : Fragment {
    private lateinit var client: SubsonicClient
    private lateinit var bind: TvActivity.TvActivityBind
    private lateinit var searchBox: TextInputEditText
    private var job: Job? = null
    private lateinit var albumsRecycler: RecyclerView
    private lateinit var songsRecycler: RecyclerView
    private lateinit var albumsAdapter: SonicSoundCardAdapter
    private lateinit var songsAdapter: SonicSoundCardAdapter
    private lateinit var albumsHeader: TextView
    private lateinit var songsHeader: TextView

    constructor() : super()

    constructor(client: SubsonicClient, bind: TvActivity.TvActivityBind) : super() {
        this.client = client
        this.bind = bind
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!::client.isInitialized || !::bind.isInitialized) {
            return
        }
        albumsHeader = view.findViewById(R.id.header_search_albums)
        albumsHeader.visibility = View.INVISIBLE
        songsHeader = view.findViewById(R.id.header_search_songs)
        songsHeader.visibility = View.INVISIBLE
        searchBox = view.findViewById(R.id.search_input)
        searchBox.doOnTextChanged { text, _, _, _ ->
            if (text.isNullOrBlank()) {
                return@doOnTextChanged
            }
            job?.cancel()
            job = viewLifecycleOwner.lifecycleScope.launch {
                delay(500)
                if (!isActive || text.isNullOrBlank()) {
                    return@launch
                }
                val searchResult = withContext(Dispatchers.IO) {
                    client.search(text.toString().trim())
                }
                if (!isActive) {
                    return@launch
                }
                val songs = searchResult.song.orEmpty()
                if (songs.isNotEmpty()) {
                    songs.forEach { it.image = client.getAlbumArt(it.albumId) }
                    songsAdapter.setNewDataSet(songs)
                    songsRecycler.visibility = View.VISIBLE
                    songsHeader.visibility = View.VISIBLE
                } else {
                    songsRecycler.visibility = View.INVISIBLE
                    songsHeader.visibility = View.INVISIBLE
                }
                val albums = searchResult.album.orEmpty()
                if (albums.isNotEmpty()) {
                    albums.forEach { it.image = client.getAlbumArt(it.id) }
                    albumsAdapter.setNewDataSet(albums)
                    albumsRecycler.visibility = View.VISIBLE
                    albumsHeader.visibility = View.VISIBLE
                } else {
                    albumsRecycler.visibility = View.INVISIBLE
                    albumsHeader.visibility = View.INVISIBLE
                }
            }
        }

        albumsRecycler = view.findViewById(R.id.rv_search_albums)
        songsRecycler = view.findViewById(R.id.rv_search_songs)
        val empty: List<ICardViewModel> = listOf()
        albumsAdapter = SonicSoundCardAdapter(empty, albumsRecycler, bind)
        songsAdapter = SonicSoundCardAdapter(empty, songsRecycler, bind)
        setUpRecyclerView(albumsRecycler, albumsAdapter)
        setUpRecyclerView(songsRecycler, songsAdapter)
    }

    private fun setUpRecyclerView(rc: RecyclerView, a: SonicSoundCardAdapter) {
        rc.setHasFixedSize(true)
        val manager = LinearLayoutManager(this.context)
        manager.orientation = RecyclerView.HORIZONTAL
        rc.layoutManager = manager
        rc.adapter = a
    }
}
