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
import app.sonicsound.models.InternetRadioStation
import app.sonicsound.subsonic.SubsonicClient
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RadioFragment : Fragment {
    private lateinit var client: SubsonicClient
    private lateinit var bind: TvActivity.TvActivityBind
    private lateinit var radioRecycler: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var addButton: Button
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
    ): View? = inflater.inflate(R.layout.fragment_radio, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!::client.isInitialized || !::bind.isInitialized) return
        radioRecycler = view.findViewById(R.id.rv_radio)
        emptyView = view.findViewById(R.id.tv_radio_empty)
        addButton = view.findViewById(R.id.btn_add_radio_station)
        addButton.setOnClickListener {
            TvLibraryDialogs.showRadioStationForm(this, client, null) { refreshStations() }
        }
        view.post {
            density = resources.displayMetrics.density
            width = view.width
            refreshStations()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::client.isInitialized && view != null) {
            refreshStations()
        }
    }

    private fun refreshStations() {
        viewLifecycleOwner.lifecycleScope.launch {
            val stations: List<ICardViewModel> = withContext(Dispatchers.IO) {
                client.getInternetRadioStations()
            }
            emptyView.isVisible = stations.isEmpty()
            radioRecycler.isVisible = stations.isNotEmpty()
            if (adapter == null) {
                adapter = SonicSoundCardAdapter(
                    stations,
                    radioRecycler,
                    bind,
                    onItemLongClick = { item ->
                        if (item is InternetRadioStation) {
                            showStationOptions(item)
                            true
                        } else {
                            false
                        }
                    },
                )
                setUpRecyclerView(radioRecycler, adapter!!)
                radioRecycler.layoutManager?.getChildAt(0)?.post {
                    val child = radioRecycler.layoutManager?.getChildAt(0) ?: return@post
                    val w = child.width + child.marginLeft + child.marginRight
                    if (w > 0) {
                        val columns = ceil(width / w.toFloat()).toInt().coerceAtLeast(1)
                        (radioRecycler.layoutManager as GridLayoutManager).spanCount = columns
                    }
                }
            } else {
                adapter!!.setNewDataSet(stations)
            }
        }
    }

    private fun showStationOptions(station: InternetRadioStation) {
        val options = arrayOf(
            getString(R.string.edit),
            getString(R.string.delete),
        )
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(station.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> TvLibraryDialogs.showRadioStationForm(
                        this, client, station
                    ) { refreshStations() }
                    1 -> TvLibraryDialogs.showDeleteRadioStation(
                        this, client, station
                    ) { refreshStations() }
                }
            }
            .show()
    }

    private fun setUpRecyclerView(rc: RecyclerView, a: SonicSoundCardAdapter) {
        rc.setHasFixedSize(true)
        val columns = ceil(width / (170 * density + 0.5f)).toInt().coerceAtLeast(1)
        rc.layoutManager = GridLayoutManager(context, columns)
        rc.adapter = a
    }
}
