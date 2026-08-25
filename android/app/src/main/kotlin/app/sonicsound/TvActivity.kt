package app.sonicsound

import app.sonicsound.subsonic.SubsonicClient
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import app.sonicsound.fragments.AlbumsFragment
import app.sonicsound.fragments.ArtistsFragment
import app.sonicsound.fragments.HomeFragment
import app.sonicsound.fragments.JukeboxFragment
import app.sonicsound.fragments.NowPlayingFragment
import app.sonicsound.fragments.PlaylistsFragment
import app.sonicsound.fragments.RadioFragment
import app.sonicsound.fragments.SearchFragment
import app.sonicsound.fragments.VideosFragment
import app.sonicsound.models.Playlist
import app.sonicsound.services.MusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TvActivity : AppCompatActivity() {
    private val client: SubsonicClient = SubsonicClient(KeyValueStorage.getActiveAccount())
    private val activityBind = TvActivityBind()
    private val homeFragment = HomeFragment(activityBind, client)
    private val playingFragment = NowPlayingFragment(activityBind, client)
    private val jukeboxFragment = JukeboxFragment()
    private val searchFragment = SearchFragment(client, activityBind)
    private val playlistFragment = PlaylistsFragment(client, activityBind)
    private val radioFragment = RadioFragment(client, activityBind)
    private val albumsFragment = AlbumsFragment(client, activityBind)
    private val artistsFragment = ArtistsFragment(client, activityBind)
    private val videosFragment = VideosFragment(client, activityBind)
    private val accountFragment = AccountFragment(client)
    private val settingsFragment = SettingsFragment(client)
    private lateinit var phoneConnected: ImageView
    private var binder: MusicService.LocalBinder? = null
    private var mBound = false
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
            show(ArtistsFragment(client, activityBind, id, name))
        }

        private fun showPlaying() {
            show(playingFragment)
        }
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fg_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        setContentView(R.layout.activity_tv)
        supportActionBar?.hide()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fg_container, homeFragment)
            .addToBackStack(null)
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
        mapOf(
            R.id.btn_home to homeFragment,
            R.id.btn_artists to artistsFragment,
            R.id.btn_albums to albumsFragment,
            R.id.btn_search to searchFragment,
            R.id.btn_playlists to playlistFragment,
            R.id.btn_radio to radioFragment,
            R.id.btn_videos to videosFragment,
            R.id.btn_account to accountFragment,
            R.id.btn_settings to settingsFragment,
            R.id.btn_jukebox to jukeboxFragment,
            R.id.btn_playing to playingFragment,
        ).forEach { (id, fragment) ->
            findViewById<Button>(id).setOnClickListener { show(fragment) }
        }
    }
}
