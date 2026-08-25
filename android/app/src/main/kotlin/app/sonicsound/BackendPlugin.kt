package app.sonicsound

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import app.sonicsound.KeyValueStorage.Companion.getActiveAccount
import app.sonicsound.plugins.BackendAccounts
import app.sonicsound.plugins.BackendLibrary
import app.sonicsound.plugins.BackendPlayback
import app.sonicsound.plugins.BackendResponses
import app.sonicsound.plugins.BackendSpotify
import app.sonicsound.plugins.BackendWebsocket
import app.sonicsound.services.MusicService
import app.sonicsound.services.MusicService.LocalBinder
import app.sonicsound.subsonic.SubsonicClient
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import org.json.JSONException
import java.io.IOException
import java.util.concurrent.TimeUnit

@CapacitorPlugin(name = "VLC")
class BackendPlugin : Plugin(), IBroadcastObserver {
    private var registered = false
    private var gson: Gson? = null
    private var binder: LocalBinder? = null
    private var mBound = false

    private val mClient: OkHttpClient = OkHttpClient.Builder()
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private lateinit var responses: BackendResponses
    private lateinit var accounts: BackendAccounts
    private lateinit var library: BackendLibrary
    private lateinit var playback: BackendPlayback
    private lateinit var spotify: BackendSpotify
    private lateinit var websocket: BackendWebsocket

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.i("ServiceBinder", "Binding service")
            binder = service as LocalBinder
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            Log.i("ServiceBinder", "Unbinding service")
            mBound = false
        }
    }

    private fun initHelpers() {
        responses = BackendResponses { gson!! }
        accounts = BackendAccounts(responses) { subsonicClient }
        library = BackendLibrary(responses, { subsonicClient }, { gson!! })
        websocket = BackendWebsocket(
            responses,
            { gson!! },
            mClient,
            { binder },
            { mBound }
        ) { action, value -> notifyListeners(action, value) }
        playback = BackendPlayback(
            responses,
            { gson!! },
            { binder },
            { mBound },
            { websocket.webSocketConnected },
            { websocket.mWebSocket }
        )
        spotify = BackendSpotify(responses)
    }

    override fun handleOnDestroy() {
        super.handleOnDestroy()
        mClient.dispatcher.executorService.shutdown()
        Globals.UnregisterObserver(this)
    }

    override fun handleOnPause() {
        super.handleOnPause()
    }

    override fun handleOnResume() {
        super.handleOnResume()
        if (!registered) {
            registered = true
            Globals.RegisterObserver(this)
        }
        if (mBound) {
            val state = binder!!.getCurrentState()
            notifyListeners("progress", JSObject("{\"time\": ${state.position}}"))
            notifyListeners(if (state.playing) "play" else "pause", null)
            notifyListeners(
                "currentTrack",
                JSObject("{\"currentTrack\": ${gson!!.toJson(state.currentTrack)}}")
            )
        }
    }

    override fun load() {
        if (subsonicClient == null) {
            subsonicClient = SubsonicClient(getActiveAccount())
        }
        val builder = GsonBuilder()
        builder.serializeNulls()
        gson = builder.create()
        initHelpers()
        val intent = Intent(App.context, MusicService::class.java)
        App.context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        if (!registered) {
            registered = true
            Globals.RegisterObserver(this)
        }
    }

    @PluginMethod fun login(call: PluginCall) = accounts.login(call)
    @PluginMethod fun getCameraPermissionStatus(call: PluginCall) =
        accounts.getCameraPermissionStatus(call)
    @PluginMethod fun getCameraPermission(call: PluginCall) = accounts.getCameraPermission(call)
    @PluginMethod fun getTopAlbums(call: PluginCall) = library.getTopAlbums(call)
    @PluginMethod fun getAlbums(call: PluginCall) = library.getAlbums(call)
    @PluginMethod fun getAlbum(call: PluginCall) = library.getAlbum(call)
    @PluginMethod fun getArtists(call: PluginCall) = library.getArtists(call)
    @PluginMethod fun getArtist(call: PluginCall) = library.getArtist(call)
    @PluginMethod fun getRandomSongs(call: PluginCall) = library.getRandomSongs(call)
    @PluginMethod fun shufflePlaylist(call: PluginCall) = playback.shufflePlaylist(call)
    @PluginMethod fun getOfflineMode(call: PluginCall) = accounts.getOfflineMode(call)
    @PluginMethod fun setOfflineMode(call: PluginCall) = accounts.setOfflineMode(call)
    @PluginMethod fun getActiveAccount(call: PluginCall) = accounts.getActiveAccount(call)
    @PluginMethod fun setSettings(call: PluginCall) = accounts.setSettings(call)
    @PluginMethod fun getSettings(call: PluginCall) = accounts.getSettings(call)
    @PluginMethod fun getAccounts(call: PluginCall) = accounts.getAccounts(call)
    @PluginMethod fun getAlbumArt(call: PluginCall) = library.getAlbumArt(call)
    @PluginMethod fun getArtistArt(call: PluginCall) = library.getArtistArt(call)
    @PluginMethod fun star(call: PluginCall) = library.star(call)
    @PluginMethod fun unstar(call: PluginCall) = library.unstar(call)
    @PluginMethod fun play(call: PluginCall) = playback.play(call)
    @PluginMethod fun pause(call: PluginCall) = playback.pause(call)
    @PluginMethod fun seek(call: PluginCall) = playback.seek(call)
    @PluginMethod fun setVolume(call: PluginCall) = playback.setVolume(call)
    @PluginMethod fun playRadio(call: PluginCall) = playback.playRadio(call)
    @PluginMethod fun playInternetRadio(call: PluginCall) = playback.playInternetRadio(call)
    @PluginMethod fun playAlbum(call: PluginCall) = playback.playAlbum(call)
    @PluginMethod fun downloadAlbum(call: PluginCall) = library.downloadAlbum(call)
    @PluginMethod fun next(call: PluginCall) = playback.next(call)

    @PluginMethod
    @Throws(IOException::class)
    fun getSpotifyToken(call: PluginCall) = spotify.getSpotifyToken(call)

    @PluginMethod fun search(call: PluginCall) = library.search(call)
    @PluginMethod fun prev(call: PluginCall) = playback.prev(call)

    @PluginMethod
    @Throws(JSONException::class)
    fun getCurrentState(call: PluginCall) = playback.getCurrentState(call)

    @PluginMethod fun getSongStatus(call: PluginCall) = library.getSongStatus(call)
    @PluginMethod fun qrLogin(call: PluginCall) = websocket.qrLogin(call)
    @PluginMethod fun getWebsocketStatus(call: PluginCall) = websocket.getWebsocketStatus(call)
    @PluginMethod fun disconnectWebsocket(call: PluginCall) = websocket.disconnectWebsocket(call)
    @PluginMethod fun sendUdpBroadcast(call: PluginCall) = websocket.sendUdpBroadcast(call)
    @PluginMethod fun getCurrentPlaylist(call: PluginCall) = playback.getCurrentPlaylist(call)
    @PluginMethod fun getPlaylists(call: PluginCall) = library.getPlaylists(call)
    @PluginMethod fun getPlaylist(call: PluginCall) = library.getPlaylist(call)
    @PluginMethod fun removeFromPlaylist(call: PluginCall) = library.removeFromPlaylist(call)
    @PluginMethod fun addToPlaylist(call: PluginCall) = library.addToPlaylist(call)
    @PluginMethod fun createPlaylist(call: PluginCall) = library.createPlaylist(call)
    @PluginMethod fun updatePlaylist(call: PluginCall) = library.updatePlaylist(call)
    @PluginMethod fun removePlaylist(call: PluginCall) = library.removePlaylist(call)
    @PluginMethod fun skipTo(call: PluginCall) = playback.skipTo(call)
    @PluginMethod fun playPlaylist(call: PluginCall) = playback.playPlaylist(call)
    @PluginMethod fun clearCoverCache(call: PluginCall) = library.clearCoverCache(call)
    @PluginMethod fun getCoverCacheSize(call: PluginCall) = library.getCoverCacheSize(call)
    @PluginMethod fun getLyrics(call: PluginCall) = library.getLyrics(call)
    @PluginMethod fun getInternetRadioStations(call: PluginCall) =
        library.getInternetRadioStations(call)

    override fun update(action: String?, value: String?) {
        try {
            if (action == null) {
                return
            }
            if (action == "SLCANCEL" && mBound) {
                App.context.unbindService(connection)
                mBound = false
                binder = null
            } else if (action.startsWith("MS")) {
                if (value != null) {
                    notifyListeners(action.replace("MS", ""), JSObject(value))
                } else {
                    notifyListeners(action.replace("MS", ""), null)
                }
            } else {
                websocket.onObserverUpdate(action, value)
            }
        } catch (e: Exception) {
            Log.e("SonicSound", e.message ?: "observer update failed")
        }
    }

    companion object {
        private var subsonicClient: SubsonicClient? = null
    }
}
