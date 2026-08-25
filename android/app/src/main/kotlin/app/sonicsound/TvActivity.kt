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
import app.sonicsound.fragments.HomeFragment
import app.sonicsound.fragments.JukeboxFragment
import app.sonicsound.fragments.NowPlayingFragment
import app.sonicsound.fragments.PlaylistsFragment
import app.sonicsound.fragments.RadioFragment
import app.sonicsound.fragments.SearchFragment
import app.sonicsound.models.Playlist
import app.sonicsound.services.MusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TvActivity : AppCompatActivity() {
    private val client: SubsonicClient = SubsonicClient(KeyValueStorage.getActiveAccount())
    private val activityBind = TvActivityBind()
    private val homeFragment: HomeFragment = HomeFragment(activityBind, client)
    private val playingFragment: NowPlayingFragment = NowPlayingFragment(activityBind, client)
    private val jukeboxFragment: JukeboxFragment = JukeboxFragment()
    private val searchFragment: SearchFragment = SearchFragment(client, activityBind)
    private val playlistFragment: PlaylistsFragment = PlaylistsFragment(client, activityBind)
    private val radioFragment: RadioFragment = RadioFragment(client, activityBind)
    private val accountFragment: AccountFragment = AccountFragment(client)
    private lateinit var phoneConnected: ImageView
    private var binder: MusicService.LocalBinder? = null
    private var mBound = false
    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.i("ServiceBinder", "Binding service")
            binder = service as MusicService.LocalBinder
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            Log.i("ServiceBinder", "Unbinding service")
            mBound = false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val count = supportFragmentManager.backStackEntryCount
        if (count == 0) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        } else {
            supportFragmentManager.popBackStack()
        }
    }

    inner class TvActivityObserver : IBroadcastObserver {
        override fun update(action: String?, value: String?) {
            if (action == "WS") {
                this@TvActivity.runOnUiThread {
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
        fun getCurrentState(): CurrentState? {
            if (mBound) {
                return binder!!.getCurrentState()
            }
            return null
        }

        fun getCurrentPlaylist(): Playlist? {
            if (mBound) {
                return binder!!.getPlaylist()
            }
            return null
        }

        fun playAlbum(id: String, track: Int) {
            if (mBound) {
                CoroutineScope(Dispatchers.IO).launch {
                    binder!!.playAlbum(id, track)
                }
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
            if (mBound) {
                binder!!.shuffle()
            }
        }

        fun playRadio(id: String) {
            if (mBound) {
                CoroutineScope(Dispatchers.IO).launch {
                    binder!!.playRadio(id)
                }
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
                CoroutineScope(Dispatchers.IO).launch {
                    binder!!.playPlaylist(id, track)
                }
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
            if (mBound) {
                if (binder!!.getCurrentState().playing) {
                    binder!!.pause()
                } else {
                    binder!!.play()
                }
            }
        }

        fun seek(position: Float) {
            if (mBound) {
                binder!!.seek(position)
            }
        }

        fun next() {
            if (mBound) {
                binder!!.next()
            }
        }

        fun prev() {
            if (mBound) {
                binder!!.prev()
            }
        }

        private fun showPlaying() {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fg_container, playingFragment)
                .addToBackStack(null)
                .commit()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        setContentView(R.layout.activity_tv)
        supportActionBar?.hide()
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fg_container, homeFragment)
            .addToBackStack(null)
            .commit()
        val intent = Intent(App.context, MusicService::class.java)
        App.context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        Globals.RegisterObserver(this.TvActivityObserver())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        phoneConnected = findViewById(R.id.iv_phone_connected)
        findViewById<Button>(R.id.btn_home).setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fg_container, homeFragment)
                .addToBackStack(null)
                .commit()
        }
        findViewById<Button>(R.id.btn_playing).setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fg_container, playingFragment)
                .addToBackStack(null)
                .commit()
        }
        findViewById<Button>(R.id.btn_jukebox).setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fg_container, jukeboxFragment)
                .addToBackStack(null)
                .commit()
        }
        findViewById<Button>(R.id.btn_search).setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fg_container, searchFragment)
                .addToBackStack(null)
                .commit()
        }
        findViewById<Button>(R.id.btn_playlists).setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fg_container, playlistFragment)
                .addToBackStack(null)
                .commit()
        }
        findViewById<Button>(R.id.btn_radio).setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fg_container, radioFragment)
                .addToBackStack(null)
                .commit()
        }
        findViewById<Button>(R.id.btn_account).setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fg_container, accountFragment)
                .addToBackStack(null)
                .commit()
        }
    }
}
