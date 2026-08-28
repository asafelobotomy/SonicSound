package app.sonicsound.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.sonicsound.KeyValueStorage
import app.sonicsound.R
import app.sonicsound.TvActivity
import app.sonicsound.adapters.SonicSoundCardAdapter
import app.sonicsound.models.ICardViewModel
import app.sonicsound.models.YoutubeVideo
import app.sonicsound.subsonic.SubsonicClient
import app.sonicsound.youtube.YoutubeDataApi
import app.sonicsound.youtube.YoutubeOAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideosFragment : Fragment {
    private lateinit var client: SubsonicClient
    private lateinit var bind: TvActivity.TvActivityBind

    constructor() : super()

    constructor(client: SubsonicClient, bind: TvActivity.TvActivityBind) : super() {
        this.client = client
        this.bind = bind
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.fragment_videos, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!::bind.isInitialized) return
        val search = view.findViewById<EditText>(R.id.et_video_search)
        val empty = view.findViewById<TextView>(R.id.tv_videos_empty)
        val recycler = view.findViewById<RecyclerView>(R.id.rv_videos)
        search.setOnEditorActionListener { _, actionId, event ->
            val go = actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (go) {
                runSearch(search.text?.toString().orEmpty(), empty, recycler)
                true
            } else {
                false
            }
        }
    }

    private fun runSearch(query: String, empty: TextView, recycler: RecyclerView) {
        val settings = KeyValueStorage.getSettings()
        if (!settings.youtubeVideosEnabled ||
            (YoutubeOAuth.validAccessToken().isBlank() && settings.youtubeApiKey.isBlank())
        ) {
            empty.text = getString(R.string.videos_empty)
            empty.isVisible = true
            recycler.isVisible = false
            Toast.makeText(requireContext(), R.string.youtube_api_hint, Toast.LENGTH_LONG).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val results: List<ICardViewModel> = withContext(Dispatchers.IO) {
                YoutubeDataApi.searchAuthed(query)
            }
            empty.isVisible = results.isEmpty()
            recycler.isVisible = results.isNotEmpty()
            if (results.isEmpty()) {
                empty.text = getString(R.string.videos_empty)
            }
            val adapter = SonicSoundCardAdapter(results, recycler, bind) { item ->
                if (item is YoutubeVideo) openYoutube(item.id)
                item is YoutubeVideo
            }
            recycler.layoutManager = LinearLayoutManager(context)
            recycler.adapter = adapter
        }
    }

    private fun openYoutube(videoId: String) {
        // Official YouTube app / site — Premium subscribers get ad-free playback there.
        val app = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/watch?v=$videoId")
        )
        try {
            startActivity(app)
        } catch (_: Exception) {
            startActivity(web)
        }
    }
}
