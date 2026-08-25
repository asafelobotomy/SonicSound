package app.sonicsound.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
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
import app.sonicsound.library.ArtistSort
import app.sonicsound.library.sortArtists
import app.sonicsound.models.Artist
import app.sonicsound.models.ICardViewModel
import app.sonicsound.subsonic.SubsonicClient
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArtistsFragment : Fragment {
    private lateinit var client: SubsonicClient
    private lateinit var bind: TvActivity.TvActivityBind
    private var artistId: String? = null
    private var titleOverride: String? = null
    private var allArtists: List<Artist> = emptyList()
    private var sort = ArtistSort.NAME_ASC
    private var density = 0f
    private var width = 0

    constructor() : super()

    constructor(client: SubsonicClient, bind: TvActivity.TvActivityBind) : super() {
        this.client = client
        this.bind = bind
    }

    constructor(
        client: SubsonicClient,
        bind: TvActivity.TvActivityBind,
        artistId: String,
        title: String,
    ) : super() {
        this.client = client
        this.bind = bind
        this.artistId = artistId
        this.titleOverride = title
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.fragment_artists, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!::client.isInitialized || !::bind.isInitialized) return
        val title = view.findViewById<TextView>(R.id.tv_artists_title)
        titleOverride?.let { title.text = it }
        val recycler = view.findViewById<RecyclerView>(R.id.rv_artists)
        val empty = view.findViewById<TextView>(R.id.tv_artists_empty)
        val spinner = view.findViewById<Spinner>(R.id.sp_artists_sort)
        val detailId = artistId
        spinner.isVisible = detailId == null
        if (detailId == null) {
            val labels = listOf(
                getString(R.string.sort_name_asc),
                getString(R.string.sort_name_desc),
                getString(R.string.sort_albums_asc),
                getString(R.string.sort_albums_desc),
            )
            spinner.adapter = ArrayAdapter(
                requireContext(),
                R.layout.spinner_item_white,
                labels
            ).also { it.setDropDownViewResource(R.layout.spinner_dropdown_white) }
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    v: View?,
                    position: Int,
                    id: Long,
                ) {
                    sort = ArtistSort.entries[position]
                    renderArtists(recycler, empty)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        view.post {
            density = resources.displayMetrics.density
            width = view.width
            viewLifecycleOwner.lifecycleScope.launch {
                if (detailId != null) {
                    val albums: List<ICardViewModel> = withContext(Dispatchers.IO) {
                        client.getArtist(detailId).album.onEach {
                            it.image = client.getAlbumArt(it.coverArt)
                        }
                    }
                    empty.isVisible = albums.isEmpty()
                    recycler.isVisible = albums.isNotEmpty()
                    setGrid(recycler, SonicSoundCardAdapter(albums, recycler, bind))
                } else {
                    allArtists = withContext(Dispatchers.IO) {
                        client.getArtists().onEach { artist ->
                            artist.image = when {
                                artist.coverArt.startsWith("http", ignoreCase = true) ->
                                    artist.coverArt
                                artist.coverArt.isNotBlank() ->
                                    client.getAlbumArt(artist.coverArt)
                                else -> ""
                            }
                        }
                    }
                    renderArtists(recycler, empty)
                    hydrateArtistArts(recycler, empty)
                }
            }
        }
    }

    private fun renderArtists(recycler: RecyclerView, empty: TextView) {
        val cards: List<ICardViewModel> = sortArtists(allArtists, sort)
        empty.isVisible = cards.isEmpty()
        recycler.isVisible = cards.isNotEmpty()
        setGrid(recycler, SonicSoundCardAdapter(cards, recycler, bind))
    }

    private fun hydrateArtistArts(recycler: RecyclerView, empty: TextView) {
        viewLifecycleOwner.lifecycleScope.launch {
            val missing = allArtists.filter { it.image.isBlank() }
            for (chunk in missing.chunked(4)) {
                withContext(Dispatchers.IO) {
                    chunk.forEach { artist ->
                        try {
                            val url = client.getArtistArt(artist.id)
                            if (url.isNotBlank()) artist.image = url
                        } catch (_: Exception) {
                        }
                    }
                }
                if (view == null) return@launch
                renderArtists(recycler, empty)
            }
        }
    }

    private fun setGrid(rc: RecyclerView, a: SonicSoundCardAdapter) {
        rc.setHasFixedSize(true)
        val columns = ceil(width / (170 * density + 0.5f)).toInt().coerceAtLeast(1)
        rc.layoutManager = GridLayoutManager(context, columns)
        rc.adapter = a
        rc.layoutManager?.getChildAt(0)?.post {
            val child = rc.layoutManager?.getChildAt(0) ?: return@post
            val w = child.width + child.marginLeft + child.marginRight
            if (w > 0) {
                (rc.layoutManager as GridLayoutManager).spanCount =
                    ceil(width / w.toFloat()).toInt().coerceAtLeast(1)
            }
        }
    }
}
