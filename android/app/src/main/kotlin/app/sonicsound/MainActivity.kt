package app.sonicsound

import android.app.SearchManager
import android.app.UiModeManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    companion object {
        @JvmField
        var requestPermissionLauncher: ActivityResultLauncher<String>? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Clear splash window background once the WebView activity is up.
        window.setBackgroundDrawableResource(android.R.color.transparent)
        registerPlugin(BackendPlugin::class.java)
        registerPlugin(AndroidTVPlugin::class.java)
        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        handleMediaSearchIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            startActivity(Intent(this, TvLoginActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        Globals.NotifyObservers("RESUMED", "")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleMediaSearchIntent(intent)
    }

    private fun handleMediaSearchIntent(intent: Intent?) {
        if (intent?.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH &&
            intent?.action != "android.media.action.MEDIA_PLAY_FROM_SEARCH"
        ) {
            return
        }
        val mediaFocus = intent.getStringExtra(MediaStore.EXTRA_MEDIA_FOCUS)
        val query = intent.getStringExtra(SearchManager.QUERY)
        val album = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM)
        val artist = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST)
        val title = intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE)

        if (query == null) {
            Globals.NotifyObservers("SLPLAY", "")
            return
        }
        when {
            mediaFocus == null -> Globals.NotifyObservers("SLPLAYSEARCH", query)
            mediaFocus == "vnd.android.cursor.item/*" -> {
                if (query.isNotEmpty()) {
                    Globals.NotifyObservers(
                        "SLPLAYSEARCH",
                        query.replace("on sonicsound", "", ignoreCase = true)
                    )
                } else {
                    Globals.NotifyObservers("SLPLAY", "")
                }
            }
            mediaFocus == MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE ->
                Globals.NotifyObservers("SLPLAYSEARCHARTIST", artist)
            mediaFocus == MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE ->
                Globals.NotifyObservers("SLPLAYSEARCHALBUM", "$album $artist")
            mediaFocus == "vnd.android.cursor.item/audio" ->
                Globals.NotifyObservers("SLPLAYSEARCH", title)
            else -> Globals.NotifyObservers("SLPLAYSEARCH", query)
        }
    }
}
