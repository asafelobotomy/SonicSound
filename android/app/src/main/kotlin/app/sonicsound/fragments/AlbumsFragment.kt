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
import app.sonicsound.library.AlbumSort
import app.sonicsound.library.sortAlbums
import app.sonicsound.models.Album
import app.sonicsound.models.ICardViewModel
import app.sonicsound.subsonic.SubsonicClient
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlbumsFragment : Fragment {
    private lateinit var client: SubsonicClient
    private lateinit var bind: TvActivity.TvActivityBind
    private var allAlbums: List<Album> = emptyList()
    private var sort = AlbumSort.NAME_ASC
    private var density = 0f
    private var width = 0

    constructor() : super()

    constructor(client: SubsonicClient, bind: TvActivity.TvActivityBind) : super() {
        this.client = client
        this.bind = bind
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.fragment_albums, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!::client.isInitialized || !::bind.isInitialized) return
        val recycler = view.findViewById<RecyclerView>(R.id.rv_albums)
        val empty = view.findViewById<TextView>(R.id.tv_albums_empty)
        val spinner = view.findViewById<Spinner>(R.id.sp_albums_sort)
        val labels = listOf(
            getString(R.string.sort_name_asc),
            getString(R.string.sort_name_desc),
            getString(R.string.sort_year_asc),
            getString(R.string.sort_year_desc),
            getString(R.string.sort_artist_asc),
        )
        spinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            labels
        )
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                v: View?,
                position: Int,
                id: Long,
            ) {
                sort = AlbumSort.entries[position]
                render(recycler, empty)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        view.post {
            density = resources.displayMetrics.density
            width = view.width
            viewLifecycleOwner.lifecycleScope.launch {
                allAlbums = withContext(Dispatchers.IO) {
                    client.getAlbums().onEach { it.image = client.getAlbumArt(it.coverArt) }
                }
                render(recycler, empty)
            }
        }
    }

    private fun render(recycler: RecyclerView, empty: TextView) {
        val albums: List<ICardViewModel> = sortAlbums(allAlbums, sort)
        empty.isVisible = albums.isEmpty()
        recycler.isVisible = albums.isNotEmpty()
        val adapter = SonicSoundCardAdapter(albums, recycler, bind)
        recycler.setHasFixedSize(true)
        val columns = ceil(width / (170 * density + 0.5f)).toInt().coerceAtLeast(1)
        recycler.layoutManager = GridLayoutManager(context, columns)
        recycler.adapter = adapter
        recycler.layoutManager?.getChildAt(0)?.post {
            val child = recycler.layoutManager?.getChildAt(0) ?: return@post
            val w = child.width + child.marginLeft + child.marginRight
            if (w > 0) {
                (recycler.layoutManager as GridLayoutManager).spanCount =
                    ceil(width / w.toFloat()).toInt().coerceAtLeast(1)
            }
        }
    }
}
