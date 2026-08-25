package app.sonicsound.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.sonicsound.R
import app.sonicsound.TvActivity
import app.sonicsound.adapters.SonicSoundCardAdapter
import app.sonicsound.models.ICardViewModel
import app.sonicsound.subsonic.SubsonicClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment {
    private lateinit var bind: TvActivity.TvActivityBind
    private lateinit var client: SubsonicClient
    private lateinit var topAlbumsAdapter: SonicSoundCardAdapter
    private lateinit var recentAlbumsAdapter: SonicSoundCardAdapter
    private lateinit var newAlbumsAdapter: SonicSoundCardAdapter
    private lateinit var randomSongsAdapter: SonicSoundCardAdapter

    constructor() : super()

    constructor(bind: TvActivity.TvActivityBind, client: SubsonicClient) : super() {
        this.bind = bind
        this.client = client
    }

    private fun setUpRecyclerView(rc: RecyclerView, a: SonicSoundCardAdapter) {
        rc.setHasFixedSize(true)
        val manager = LinearLayoutManager(this.context)
        manager.orientation = RecyclerView.HORIZONTAL
        rc.layoutManager = manager
        rc.adapter = a
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!::client.isInitialized || !::bind.isInitialized) {
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val topAlbums = withContext(Dispatchers.IO) {
                client.getTopAlbums().onEach { it.image = client.getAlbumArt(it.id) }
            }
            topAlbumsAdapter.setNewDataSet(topAlbums)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val randomSongs = withContext(Dispatchers.IO) {
                client.getRandomSongs().onEach { it.image = client.getAlbumArt(it.albumId) }
            }
            randomSongsAdapter.setNewDataSet(randomSongs)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val recentAlbums = withContext(Dispatchers.IO) {
                client.getTopAlbums("recent").onEach { it.image = client.getAlbumArt(it.id) }
            }
            recentAlbumsAdapter.setNewDataSet(recentAlbums)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val newAlbums = withContext(Dispatchers.IO) {
                client.getTopAlbums("newest").onEach { it.image = client.getAlbumArt(it.id) }
            }
            newAlbumsAdapter.setNewDataSet(newAlbums)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val v = inflater.inflate(R.layout.fragment_home, container, false)
        if (!::bind.isInitialized) {
            return v
        }
        val topAlbumsRecycler = v.findViewById<RecyclerView>(R.id.rv_topAlbums)
        val randomSongsRecycler = v.findViewById<RecyclerView>(R.id.rv_randomSongs)
        val recentAlbumsRecycler = v.findViewById<RecyclerView>(R.id.rv_recentAlbums)
        val newAlbumsRecycler = v.findViewById<RecyclerView>(R.id.rv_newAlbums)
        val empty: List<ICardViewModel> = listOf()
        topAlbumsAdapter = SonicSoundCardAdapter(empty, topAlbumsRecycler, bind)
        randomSongsAdapter = SonicSoundCardAdapter(empty, randomSongsRecycler, bind)
        recentAlbumsAdapter = SonicSoundCardAdapter(empty, recentAlbumsRecycler, bind)
        newAlbumsAdapter = SonicSoundCardAdapter(empty, newAlbumsRecycler, bind)
        setUpRecyclerView(topAlbumsRecycler, topAlbumsAdapter)
        setUpRecyclerView(randomSongsRecycler, randomSongsAdapter)
        setUpRecyclerView(recentAlbumsRecycler, recentAlbumsAdapter)
        setUpRecyclerView(newAlbumsRecycler, newAlbumsAdapter)
        return v
    }
}
