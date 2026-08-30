package app.sonicsound

import app.sonicsound.subsonic.SubsonicClient
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import app.sonicsound.fragments.AlbumDetailFragment
import app.sonicsound.fragments.AlbumsFragment
import app.sonicsound.fragments.ArtistsFragment
import app.sonicsound.fragments.HomeFragment
import app.sonicsound.fragments.JukeboxFragment
import app.sonicsound.fragments.RemoteFragment
import app.sonicsound.fragments.NowPlayingFragment
import app.sonicsound.fragments.PlaylistDetailFragment
import app.sonicsound.fragments.PlaylistsFragment
import app.sonicsound.fragments.RadioFragment
import app.sonicsound.fragments.SearchFragment
import app.sonicsound.fragments.VideosFragment
import app.sonicsound.extensions.requestPrimaryFocus
import app.sonicsound.models.Playlist
import app.sonicsound.services.MusicService
import app.sonicsound.visualizer.WmpVisualizerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TvActivity : AppCompatActivity() {
    private val client: SubsonicClient = SubsonicClient(KeyValueStorage.getActiveAccount())
    private val activityBind = TvActivityBind()
    private val homeFragment = HomeFragment(activityBind, client)
    private val playingFragment = NowPlayingFragment(activityBind, client)
    private val remoteFragment = RemoteFragment()
    private val jukeboxFragment = JukeboxFragment(client, activityBind)
    private val searchFragment = SearchFragment(client, activityBind)
    private val playlistFragment = PlaylistsFragment(client, activityBind)
    private val radioFragment = RadioFragment(client, activityBind)
    private val albumsFragment = AlbumsFragment(client, activityBind)
    private val artistsFragment = ArtistsFragment(client, activityBind)
    private val videosFragment =
        if (Features.YOUTUBE_MUSIC_VIDEOS) VideosFragment(client, activityBind) else null
    private val accountFragment = AccountFragment(client)
    private val settingsFragment = SettingsFragment(client)
    private lateinit var phoneConnected: ImageView
    private var binder: MusicService.LocalBinder? = null
    private var mBound = false
    private var selectedNavId: Int = R.id.btn_home
    private var navBeforePlaying: Int = R.id.btn_home
    private val topLevelNavIds = listOf(
        R.id.btn_home,
        R.id.btn_artists,
        R.id.btn_albums,
        R.id.btn_search,
        R.id.btn_playlists,
        R.id.btn_radio,
        R.id.btn_account,
        R.id.btn_settings,
        R.id.btn_remote,
        R.id.btn_jukebox,
        R.id.btn_playing,
    )
    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            binder = service as MusicService.LocalBinder
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val current = supportFragmentManager.findFragmentById(R.id.fg_container)
        if (current is NowPlayingFragment && current.handleBackPress()) {
            return
        }
        if (supportFragmentManager.backStackEntryCount == 0) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        } else {
            supportFragmentManager.popBackStack()
        }
    }

    inner class TvActivityObserver : IBroadcastObserver {
        override fun update(action: String?, value: String?) {
            if (action == "WS") {
                runOnUiThread {
                    phoneConnected.visibility =
                        if (value == "true") View.VISIBLE else View.INVISIBLE
                }
            }
            if (action == "EX") {
                Toast.makeText(this@TvActivity, value, Toast.LENGTH_SHORT).show()
            }
        }
    }

    inner class TvActivityBind {
        fun getCurrentState(): CurrentState? = if (mBound) binder!!.getCurrentState() else null
        fun getCurrentPlaylist(): Playlist? = if (mBound) binder!!.getPlaylist() else null

        fun playAlbum(id: String, track: Int) {
            if (mBound) {
                CoroutineScope(Dispatchers.IO).launch { binder!!.playAlbum(id, track) }
            } else {
                val intent = Intent(App.context, MusicService::class.java)
                intent.action = Constants.SERVICE_PLAY_ALBUM
                intent.putExtra("id", id)
                intent.putExtra("track", track)
                App.context.startService(intent)
            }
            showPlaying()
        }

        fun shuffle() {
            if (mBound) binder!!.shuffle()
        }

        fun cycleRepeat() {
            if (mBound) binder!!.cycleRepeat()
        }

        fun playRadio(id: String) {
            if (mBound) {
                CoroutineScope(Dispatchers.IO).launch { binder!!.playRadio(id) }
            } else {
                val intent = Intent(App.context, MusicService::class.java)
                intent.action = Constants.SERVICE_PLAY_RADIO
                intent.putExtra("id", id)
                App.context.startService(intent)
            }
            showPlaying()
        }

        fun playJukeboxCollection(json: String) {
            if (mBound) {
                binder!!.playJukeboxCollection(json)
            } else {
                val intent = Intent(App.context, MusicService::class.java)
                intent.action = Constants.SERVICE_PLAY_JUKEBOX
                intent.putExtra("collection", json)
                App.context.startService(intent)
            }
            showPlaying()
        }

        fun playInternetRadio(streamUrl: String, name: String) {
            if (mBound) {
                CoroutineScope(Dispatchers.IO).launch {
                    binder!!.playInternetRadio(streamUrl, name)
                }
            }
            showPlaying()
        }

        fun playPlaylist(id: String, track: Int) {
            if (mBound) {
                CoroutineScope(Dispatchers.IO).launch { binder!!.playPlaylist(id, track) }
            } else {
                val intent = Intent(App.context, MusicService::class.java)
                intent.action = Constants.SERVICE_PLAY_PLAYLIST
                intent.putExtra("id", id)
                intent.putExtra("track", track)
                App.context.startService(intent)
            }
            showPlaying()
        }

        fun playPause() {
            if (!mBound) return
            if (binder!!.getCurrentState().playing) binder!!.pause() else binder!!.play()
        }

        fun seek(position: Float) {
            if (mBound) binder!!.seek(position)
        }

        fun next() {
            if (mBound) binder!!.next()
        }

        fun prev() {
            if (mBound) binder!!.prev()
        }

        fun skipTo(track: Int) {
            if (mBound) binder!!.skipTo(track)
        }

        fun pauseServer() {
            if (mBound) binder!!.pause()
        }

        fun resumeServer() {
            if (mBound) binder!!.play()
        }

        fun setServerVolume(volume: Int) {
            if (mBound) binder!!.setVolume(volume)
        }

        fun showArtist(id: String, name: String) {
            showDetail(ArtistsFragment(client, activityBind, id, name))
        }

        fun showAlbum(id: String, name: String) {
            showDetail(AlbumDetailFragment(client, activityBind, id, name))
        }

        fun showPlaylist(id: String, name: String) {
            showDetail(PlaylistDetailFragment(client, activityBind, id, name))
        }

        fun showPlaying() {
            val current = supportFragmentManager.findFragmentById(R.id.fg_container)
            if (current === playingFragment) {
                focusContent()
                return
            }
            // Push Now Playing so Back returns here; keep current sidebar selection.
            showDetail(playingFragment)
        }

        /** Hide sidebar + content padding for true fullscreen Now Playing media. */
        fun setImmersive(on: Boolean) {
            findViewById<View>(R.id.menu_container).visibility =
                if (on) View.GONE else View.VISIBLE
            val content = findViewById<View>(R.id.fg_container)
            if (on) {
                content.setPadding(0, 0, 0, 0)
            } else {
                val top = resources.getDimensionPixelSize(R.dimen.tv_content_padding)
                val end = resources.getDimensionPixelSize(R.dimen.tv_content_padding)
                val bottom = resources.getDimensionPixelSize(R.dimen.tv_section_gap)
                content.setPadding(0, top, end, bottom)
            }
        }
    }

    /** Top-level sidebar destinations: replace content and clear nested history. */
    private fun showTopLevel(navId: Int, fragment: Fragment) {
        val current = supportFragmentManager.findFragmentById(R.id.fg_container)
        if (current === fragment && supportFragmentManager.backStackEntryCount == 0) {
            highlightNav(navId)
            focusContent()
            return
        }
        // Pop detail pages (e.g. Now Playing) first. If the restored fragment is already
        // the target, skip replace — re-adding the same instance crashes.
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            supportFragmentManager.executePendingTransactions()
        }
        val afterPop = supportFragmentManager.findFragmentById(R.id.fg_container)
        if (afterPop !== fragment) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fg_container, fragment)
                .commit()
            supportFragmentManager.executePendingTransactions()
        }
        highlightNav(navId)
        focusContent()
    }

    /** Nested pages (album/artist detail, Now Playing from play): keep Back working. */
    private fun showDetail(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fg_container, fragment)
            .addToBackStack(null)
            .commit()
        supportFragmentManager.executePendingTransactions()
        focusContent()
    }

    private fun highlightNav(navId: Int) {
        selectedNavId = navId
        topLevelNavIds.forEach { id ->
            findViewById<Button>(id)?.isSelected = id == navId
        }
    }

    private fun focusContent() {
        currentFocus?.clearFocus()
        findViewById<View>(R.id.fg_container).requestPrimaryFocus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        setContentView(R.layout.activity_tv)
        supportActionBar?.hide()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fg_container, homeFragment)
            .commit()
        App.context.bindService(
            Intent(App.context, MusicService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
        Globals.RegisterObserver(TvActivityObserver())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        phoneConnected = findViewById(R.id.iv_phone_connected)
        findViewById<View>(R.id.btn_videos).visibility =
            if (Features.YOUTUBE_MUSIC_VIDEOS) View.VISIBLE else View.GONE
        val topLevel = mutableMapOf(
            R.id.btn_home to homeFragment,
            R.id.btn_artists to artistsFragment,
            R.id.btn_albums to albumsFragment,
            R.id.btn_search to searchFragment,
            R.id.btn_playlists to playlistFragment,
            R.id.btn_radio to radioFragment,
            R.id.btn_account to accountFragment,
            R.id.btn_settings to settingsFragment,
            R.id.btn_remote to remoteFragment,
            R.id.btn_jukebox to jukeboxFragment,
        )
        videosFragment?.let { topLevel[R.id.btn_videos] = it }
        topLevel.forEach { (id, fragment) ->
            findViewById<Button>(id).setOnClickListener { showTopLevel(id, fragment) }
        }
        // Now Playing is a detail push so Back returns to the prior section.
        findViewById<Button>(R.id.btn_playing).setOnClickListener { showPlayingFromSidebar() }
        supportFragmentManager.addOnBackStackChangedListener {
            val current = supportFragmentManager.findFragmentById(R.id.fg_container)
            if (current !== playingFragment && selectedNavId == R.id.btn_playing) {
                highlightNav(navBeforePlaying)
            }
        }
        highlightNav(R.id.btn_home)
    }

    private fun showPlayingFromSidebar() {
        val current = supportFragmentManager.findFragmentById(R.id.fg_container)
        if (current === playingFragment) {
            highlightNav(R.id.btn_playing)
            focusContent()
            return
        }
        navBeforePlaying = selectedNavId
        showDetail(playingFragment)
        highlightNav(R.id.btn_playing)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != WmpVisualizerView.REQUEST_RECORD_AUDIO) return
        val granted = grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        playingFragment.onRecordAudioPermissionResult(granted)
    }
}
