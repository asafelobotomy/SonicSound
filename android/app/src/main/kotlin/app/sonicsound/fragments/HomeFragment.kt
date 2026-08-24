package app.sonicsound.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import app.sonicsound.R
import app.sonicsound.subsonic.SubsonicClient
import app.sonicsound.TvActivity
import app.sonicsound.adapters.SonicSoundCardAdapter
import app.sonicsound.models.Album
import app.sonicsound.models.ICardViewModel
import app.sonicsound.models.Song

class HomeFragment(val bind: TvActivity.TvActivityBind, val client: SubsonicClient) : Fragment() {
    private lateinit var topAlbumsAdapter: SonicSoundCardAdapter
    private lateinit var recentAlbumsAdapter: SonicSoundCardAdapter
    private lateinit var newAlbumsAdapter: SonicSoundCardAdapter
    private lateinit var randomSongsAdapter: SonicSoundCardAdapter


    private fun setUpRecyclerView(rc: RecyclerView, a: SonicSoundCardAdapter) {
        rc.setHasFixedSize(true)
        val manager = LinearLayoutManager(this.context)
        manager.orientation = RecyclerView.HORIZONTAL
        rc.layoutManager = manager
        rc.adapter = a
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Load items
        CoroutineScope(Dispatchers.IO).launch {
            val topAlbums = client.getTopAlbums()
            topAlbums.forEach {
                it.image = client.getAlbumArt(it.id)
            }
            requireActivity().runOnUiThread {
                topAlbumsAdapter.setNewDataSet(topAlbums)
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            val randomSongs = client.getRandomSongs()

            randomSongs.forEach {
                it.image = client.getAlbumArt(it.albumId)
            }
            requireActivity().runOnUiThread {
                randomSongsAdapter.setNewDataSet(randomSongs)
            }
        }
        CoroutineScope(Dispatchers.IO).launch {

            val recentAlbums = client.getTopAlbums("recent")
            recentAlbums.forEach {
                it.image = client.getAlbumArt(it.id)
            }
            requireActivity().runOnUiThread {
                recentAlbumsAdapter.setNewDataSet(recentAlbums)
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            val newAlbums = client.getTopAlbums("newest")
            // Load
            newAlbums.forEach {
                it.image = client.getAlbumArt(it.id)
            }
            requireActivity().runOnUiThread {
                newAlbumsAdapter.setNewDataSet(newAlbums)
            }

        }

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val v = inflater.inflate(R.layout.fragment_home, container, false)

        val topAlbumsRecycler = v.findViewById(R.id.rv_topAlbums) as RecyclerView
        val randomSongsRecycler = v.findViewById(R.id.rv_randomSongs) as RecyclerView
        val recentAlbumsRecycler = v.findViewById(R.id.rv_recentAlbums) as RecyclerView
        val newAlbumsRecycler = v.findViewById(R.id.rv_newAlbums) as RecyclerView
        val topAlbums: List<ICardViewModel> = listOf()
        val randomSongs: List<ICardViewModel> = listOf()
        val recentAlbums: List<ICardViewModel> = listOf()
        val newAlbums: List<ICardViewModel> = listOf()
        topAlbumsAdapter =
            SonicSoundCardAdapter(
                topAlbums,
                topAlbumsRecycler,
                bind,
            )
        randomSongsAdapter =
            SonicSoundCardAdapter(
                randomSongs,
                randomSongsRecycler,
                bind,
            )
        recentAlbumsAdapter =
            SonicSoundCardAdapter(
                recentAlbums,
                recentAlbumsRecycler,
                bind,
            )
        newAlbumsAdapter =
            SonicSoundCardAdapter(
                newAlbums,
                newAlbumsRecycler,
                bind,
            )
        setUpRecyclerView(topAlbumsRecycler, topAlbumsAdapter)
        setUpRecyclerView(randomSongsRecycler, randomSongsAdapter)
        setUpRecyclerView(recentAlbumsRecycler, recentAlbumsAdapter)
        setUpRecyclerView(newAlbumsRecycler, newAlbumsAdapter)
        //topAlbumsRecycler.children.first().requestFocus()
        return v
    }

}