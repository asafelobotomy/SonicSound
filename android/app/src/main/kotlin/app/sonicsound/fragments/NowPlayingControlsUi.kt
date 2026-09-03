package app.sonicsound.fragments

import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import app.sonicsound.R
import app.sonicsound.TvActivity
import app.sonicsound.extensions.loadAlbumArt
import app.sonicsound.extensions.loadUrl
import app.sonicsound.playback.RepeatMode
import app.sonicsound.subsonic.SubsonicClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Control / art UI helpers for [NowPlayingFragment]. */
internal object NowPlayingControlsUi {
    fun secondsToHHSS(seconds: Int): String =
        "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"

    fun setPlayingUi(
        fragment: Fragment,
        btnPlay: ImageButton,
        fullscreen: NowPlayingFullscreen?,
        playing: Boolean,
    ) {
        val icon = if (playing) R.drawable.ic_pause_icon else R.drawable.ic_play
        btnPlay.setImageDrawable(ResourcesCompat.getDrawable(fragment.resources, icon, null))
        fullscreen?.setPlaying(playing)
    }

    fun updateShuffleUi(fragment: Fragment, btnShuffle: ImageButton, shuffling: Boolean) {
        btnShuffle.setImageDrawable(
            ResourcesCompat.getDrawable(
                fragment.resources,
                if (shuffling) R.drawable.ic_shuffle_fill_primary else R.drawable.ic_shuffle_fill,
                null,
            ),
        )
    }

    fun updateRepeatUi(fragment: Fragment, btnRepeat: ImageButton, mode: RepeatMode) {
        val (icon, label) = when (mode) {
            RepeatMode.ALL -> R.drawable.ic_repeat_primary to R.string.repeat_queue
            RepeatMode.ONE -> R.drawable.ic_repeat_one_primary to R.string.repeat_one
            RepeatMode.OFF -> R.drawable.ic_repeat to R.string.repeat_off
        }
        btnRepeat.setImageDrawable(ResourcesCompat.getDrawable(fragment.resources, icon, null))
        btnRepeat.contentDescription = fragment.getString(label)
    }

    fun updateLikeUi(
        fragment: Fragment,
        btnLike: ImageButton,
        fullscreen: NowPlayingFullscreen?,
        liked: Boolean,
    ) {
        btnLike.setImageResource(if (liked) R.drawable.ic_nav_like else R.drawable.ic_nav_unlike)
        btnLike.contentDescription =
            fragment.getString(if (liked) R.string.unlike_song else R.string.like_song)
        fullscreen?.setLiked(liked)
    }

    fun toggleLike(
        fragment: Fragment,
        bind: TvActivity.TvActivityBind,
        client: SubsonicClient,
        liked: Boolean,
        onLikedChanged: (Boolean) -> Unit,
    ) {
        val track = bind.getCurrentState()?.currentTrack ?: return
        if (track.id.isBlank()) return
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (liked) client.unstar(track.id) else client.star(track.id)
                }
                val next = !liked
                track.starred = if (next) "now" else null
                onLikedChanged(next)
            } catch (_: Exception) {
                Toast.makeText(fragment.requireContext(), R.string.like_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun loadAlbumArt(
        image: ImageView,
        backdrop: ImageView,
        url: String,
        lastArtUrl: String?,
        onLoaded: (String?) -> Unit,
        onAspect: (Int, Int) -> Unit,
    ): String? {
        if (url.isBlank()) {
            onLoaded(null)
            image.loadUrl("")
            return null
        }
        if (url == lastArtUrl) return lastArtUrl
        onLoaded(url)
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        image.loadAlbumArt(url, upscaleLowRes = true) { w, h -> onAspect(w, h) }
        backdrop.loadUrl(url)
        backdrop.alpha = 0.35f
        return url
    }

    fun updateProgressLabels(
        sbProgress: SeekBar,
        currentTimeText: TextView,
        durationText: TextView,
        fullscreen: NowPlayingFullscreen?,
        musicVideo: NowPlayingMusicVideo?,
        scrubberArmed: Boolean,
        progress: Double,
        durationSec: Int,
    ) {
        val pct = (progress * NowPlayingScrubber.PROGRESS_STEPS).roundToInt()
            .coerceIn(0, NowPlayingScrubber.PROGRESS_STEPS)
        if (!scrubberArmed) sbProgress.progress = pct
        val current = secondsToHHSS((progress * durationSec).roundToInt().coerceAtMost(durationSec))
        currentTimeText.text = current
        fullscreen?.setProgress(pct, current, durationText.text.toString())
        musicVideo?.onProgress(progress, durationSec)
    }
}
