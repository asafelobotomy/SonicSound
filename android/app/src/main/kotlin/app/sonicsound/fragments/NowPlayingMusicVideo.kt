package app.sonicsound.fragments

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import app.sonicsound.KeyValueStorage
import app.sonicsound.R
import app.sonicsound.TvActivity
import app.sonicsound.models.Song
import app.sonicsound.youtube.YoutubeDataApi
import app.sonicsound.youtube.YoutubeIframeController
import app.sonicsound.youtube.YoutubeOAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Music-video mode for Now Playing.
 * Uses YouTube audio (server muted) when an MV matches the current queue track;
 * otherwise falls back to server audio and keeps scanning the queue.
 */
class NowPlayingMusicVideo(
    private val fragment: Fragment,
    private val bind: TvActivity.TvActivityBind,
    private val art: ImageView,
    private val container: FrameLayout,
    private val button: View,
) {
    private var controller: YoutubeIframeController? = null
    private var modeEnabled = false
    private var videoActive = false
    private var ytPlaying = false
    private var activeTrackId: String? = null
    private var lastSeekSec = -1f
    private var advancing = false

    val isModeEnabled: Boolean get() = modeEnabled
    val isVideoActive: Boolean get() = videoActive

    init {
        button.setOnClickListener { toggle() }
    }

    fun onPlay() {
        if (!modeEnabled) return
        if (videoActive) {
            bind.setServerVolume(0)
            bind.pauseServer()
            controller?.play()
            ytPlaying = true
        }
    }

    fun onPause() {
        if (modeEnabled && videoActive) {
            controller?.pause()
            ytPlaying = false
        }
    }

    fun togglePlayPause(): Boolean {
        if (!modeEnabled || !videoActive) return false
        if (ytPlaying) {
            controller?.pause()
            ytPlaying = false
        } else {
            bind.setServerVolume(0)
            bind.pauseServer()
            controller?.play()
            ytPlaying = true
        }
        return true
    }

    fun isYtPlaying(): Boolean = ytPlaying

    fun onProgress(fraction: Double, durationSec: Int) {
        if (!modeEnabled || !videoActive || durationSec <= 0) return
        val sec = (fraction * durationSec).toFloat()
        if (lastSeekSec >= 0 && kotlin.math.abs(sec - lastSeekSec) > 2f) {
            controller?.seekTo(sec)
        }
        lastSeekSec = sec
    }

    fun onUserSeek(fraction: Float, durationSec: Int) {
        if (!modeEnabled || !videoActive || durationSec <= 0) return
        val sec = fraction * durationSec
        controller?.seekTo(sec)
        lastSeekSec = sec
    }

    fun onTrackChanged(track: Song?, playing: Boolean) {
        if (!modeEnabled || track == null || track.id.isBlank()) return
        if (track.id == activeTrackId) return
        activeTrackId = track.id
        advancing = false
        // Mute server immediately so queue advance does not audibly leak Subsonic audio.
        bind.setServerVolume(0)
        bind.pauseServer()
        loadFor(track, playing || true)
    }

    fun destroy() {
        restoreServerAudio()
        controller?.destroy()
        controller = null
    }

    private fun toggle() {
        if (modeEnabled) disable() else enable()
    }

    private fun enable() {
        val settings = KeyValueStorage.getSettings()
        if (!settings.youtubeVideosEnabled ||
            (YoutubeOAuth.validAccessToken().isBlank() &&
                settings.youtubeApiKey.isBlank())
        ) {
            Toast.makeText(
                fragment.requireContext(),
                R.string.music_video_needs_api,
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val state = bind.getCurrentState()
        val track = state?.currentTrack
        if (track == null || track.id.isBlank()) return
        modeEnabled = true
        button.contentDescription =
            fragment.getString(R.string.stop_music_video)
        if (button is Button) button.setText(R.string.stop_music_video)
        ensureController()
        activeTrackId = null
        loadFor(track, state.playing || true)
    }

    private fun disable() {
        modeEnabled = false
        videoActive = false
        activeTrackId = null
        lastSeekSec = -1f
        advancing = false
        button.contentDescription =
            fragment.getString(R.string.play_music_video)
        if (button is Button) button.setText(R.string.play_music_video)
        container.isVisible = false
        art.isVisible = true
        controller?.pause()
        restoreServerAudio()
        if (bind.getCurrentState()?.playing == false) {
            bind.resumeServer()
        }
    }

    private fun ensureController() {
        if (controller != null) return
        val c = YoutubeIframeController(fragment.requireContext())
        c.onEnded = {
            if (modeEnabled && !advancing) {
                advancing = true
                // Advance the play queue; next track will load MV or fall back to server audio.
                bind.next()
            }
        }
        container.removeAllViews()
        container.addView(
            c.webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        controller = c
    }

    private fun loadFor(track: Song, playing: Boolean) {
        val settings = KeyValueStorage.getSettings()
        val allowAny = settings.youtubeAllowAnyChannel
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val video = withContext(Dispatchers.IO) {
                YoutubeDataApi.searchMusicVideo(
                    track.artist,
                    track.title,
                    allowAnyChannel = allowAny,
                )
            }
            if (!modeEnabled) return@launch
            if (video == null) {
                // Stay in MV mode; play this queue item from the server, try again on next.
                showArtFallback()
                return@launch
            }
            showVideo()
            bind.setServerVolume(0)
            bind.pauseServer()
            activeTrackId = track.id
            lastSeekSec = 0f
            ytPlaying = playing
            controller?.load(video.id, 0f, playing)
        }
    }

    private fun showVideo() {
        videoActive = true
        art.isVisible = false
        container.isVisible = true
    }

    private fun showArtFallback() {
        videoActive = false
        container.isVisible = false
        art.isVisible = true
        controller?.pause()
        restoreServerAudio()
        bind.resumeServer()
    }

    private fun restoreServerAudio() {
        bind.setServerVolume(100)
    }
}
