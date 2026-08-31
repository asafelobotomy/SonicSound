package app.sonicsound.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.sonicsound.R
import app.sonicsound.TvActivity
import app.sonicsound.adapters.SonicSoundCardAdapter
import app.sonicsound.models.ICardViewModel
import app.sonicsound.models.JukeboxCollection
import app.sonicsound.subsonic.SubsonicClient
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JukeboxFragment : Fragment {
    private lateinit var client: SubsonicClient
    private lateinit var bind: TvActivity.TvActivityBind
    private lateinit var tabs: LinearLayout
    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView
    private var adapter: SonicSoundCardAdapter? = null
    private var density = 0f
    private var width = 0

    enum class Tab { RANDOM, GENRE, ARTIST, DECADE, SIMILAR, STARRED, SERVER }

    constructor() : super()

    constructor(client: SubsonicClient, bind: TvActivity.TvActivityBind) : super() {
        this.client = client
        this.bind = bind
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.fragment_jukebox_collections, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!::client.isInitialized) return
        tabs = view.findViewById(R.id.ll_jukebox_tabs)
        recycler = view.findViewById(R.id.rv_jukebox)
        emptyView = view.findViewById(R.id.tv_jukebox_empty)
        view.post {
            // Rapid sidebar nav can detach before this runs.
            if (!isAdded || view !== this.view) return@post
            density = resources.displayMetrics.density
            width = view.width
            viewLifecycleOwner.lifecycleScope.launch {
                buildTabs()
                loadTab(Tab.RANDOM)
            }
        }
    }

    private fun buildTabs() {
        tabs.removeAllViews()
        listOf(
            Tab.RANDOM to getString(R.string.jukebox_tab_random),
            Tab.GENRE to getString(R.string.jukebox_tab_genre),
            Tab.ARTIST to getString(R.string.jukebox_tab_artist),
            Tab.DECADE to getString(R.string.jukebox_tab_decade),
            Tab.SIMILAR to getString(R.string.jukebox_tab_similar),
            Tab.STARRED to getString(R.string.jukebox_tab_starred),
            Tab.SERVER to getString(R.string.jukebox_tab_server),
        ).forEach { (tab, label) ->
            val btn = Button(requireContext()).apply {
                text = label
                isAllCaps = false
                setOnClickListener { loadTab(tab) }
            }
            tabs.addView(btn)
        }
    }

    private fun loadTab(tab: Tab) {
        viewLifecycleOwner.lifecycleScope.launch {
            val similarEnabled = withContext(Dispatchers.IO) {
                try {
                    client.getOpenSubsonicExtensions().sonicSimilarity
                } catch (_: Exception) {
                    false
                }
            }
            val items: List<ICardViewModel> = withContext(Dispatchers.IO) {
                when (tab) {
                    Tab.RANDOM -> listOf(jukeboxCard("random", getString(R.string.jukebox_play_random)))
                    Tab.GENRE -> client.getGenres().map { jukeboxCard("genre:${it.value}", it.value) }
                    Tab.ARTIST -> client.getArtists().take(200).map {
                        jukeboxCard("artist:${it.id}:${it.name}", it.name)
                    }
                    Tab.DECADE -> decades()
                    Tab.SIMILAR -> {
                        if (!similarEnabled) emptyList()
                        else {
                            val seed = bind.getCurrentState()?.currentTrack
                            if (seed != null && seed.id.isNotBlank()) {
                                listOf(jukeboxCard("similar:${seed.id}:${seed.title}", seed.title))
                            } else {
                                listOf(jukeboxCard("", getString(R.string.jukebox_similar_need_playing)))
                            }
                        }
                    }
                    Tab.STARRED -> listOf(jukeboxCard("starred", getString(R.string.jukebox_play_starred)))
                    Tab.SERVER -> client.getPlaylists().map { pl ->
                        jukeboxCard("server:${pl.id}:${pl.name}", pl.name)
                    }
                }
            }
            emptyView.isVisible = items.isEmpty()
            emptyView.text = when (tab) {
                Tab.SIMILAR -> getString(R.string.jukebox_similar_unavailable)
                else -> getString(R.string.jukebox_empty)
            }
            recycler.isVisible = items.isNotEmpty()
            if (adapter == null) {
                adapter = SonicSoundCardAdapter(
                    items,
                    recycler,
                    bind,
                    onItem = { card ->
                        onCardClick(card as JukeboxCardItem)
                        true
                    },
                )
                setUpRecyclerView(recycler, adapter!!)
            } else {
                adapter!!.setNewDataSet(items)
            }
        }
    }

    private fun decades(): List<JukeboxCardItem> {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        return (1960..currentYear step 10).map { decade ->
            val to = (decade + 9).coerceAtMost(currentYear)
            jukeboxCard("decade:$decade:$to", "${decade}s")
        }
    }

    private fun jukeboxCard(id: String, title: String) = JukeboxCardItem(id, title)

    private fun onCardClick(card: JukeboxCardItem) {
        if (card.cardId.isBlank()) return
        val collection = when {
            card.cardId == "random" -> JukeboxCollection.Random
            card.cardId == "starred" -> JukeboxCollection.Starred
            card.cardId.startsWith("genre:") -> JukeboxCollection.Genre(card.cardId.removePrefix("genre:"))
            card.cardId.startsWith("artist:") -> {
                val parts = card.cardId.removePrefix("artist:").split(":", limit = 2)
                JukeboxCollection.Artist(parts[0], parts.getOrElse(1) { card.title })
            }
            card.cardId.startsWith("decade:") -> {
                val parts = card.cardId.removePrefix("decade:").split(":")
                JukeboxCollection.Decade(parts[0].toInt(), parts[1].toInt())
            }
            card.cardId.startsWith("similar:") -> {
                val parts = card.cardId.removePrefix("similar:").split(":", limit = 2)
                JukeboxCollection.Similar(parts[0], parts.getOrElse(1) { card.title })
            }
            card.cardId.startsWith("server:") -> {
                val parts = card.cardId.removePrefix("server:").split(":", limit = 2)
                JukeboxCollection.ServerPlaylist(parts[0], parts.getOrElse(1) { card.title })
            }
            else -> return
        }
        bind.playJukeboxCollection(JukeboxCollection.toJson(collection))
        bind.showPlaying()
    }

    private fun setUpRecyclerView(rc: RecyclerView, a: SonicSoundCardAdapter) {
        rc.setHasFixedSize(true)
        val columns = ceil(width / (170 * density + 0.5f)).toInt().coerceAtLeast(1)
        rc.layoutManager = GridLayoutManager(context, columns)
        rc.adapter = a
    }

    class JukeboxCardItem(
        val cardId: String,
        val title: String,
    ) : ICardViewModel {
        override var image: String = ""
        override fun firstLine(): String = title
        override fun secondLine(): String = ""
    }
}
