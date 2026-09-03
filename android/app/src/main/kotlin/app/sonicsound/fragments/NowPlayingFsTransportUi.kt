package app.sonicsound.fragments

import android.view.View
import android.widget.ImageButton
import androidx.core.content.res.ResourcesCompat
import app.sonicsound.R
import app.sonicsound.playback.RepeatMode

/** Transport button state for [NowPlayingFullscreen]. */
internal object NowPlayingFsTransportUi {
    fun setPlaying(root: View, fsPlay: ImageButton, visualizer: NowPlayingVisualizerHost, playing: Boolean) {
        visualizer.setPlaying(playing)
        fsPlay.setImageDrawable(
            ResourcesCompat.getDrawable(
                root.resources,
                if (playing) R.drawable.ic_pause_icon else R.drawable.ic_play,
                null,
            ),
        )
    }

    fun setShuffle(root: View, fsShuffle: ImageButton, shuffling: Boolean) {
        fsShuffle.setImageDrawable(
            ResourcesCompat.getDrawable(
                root.resources,
                if (shuffling) R.drawable.ic_shuffle_fill_primary else R.drawable.ic_shuffle_fill,
                null,
            ),
        )
    }

    fun setRepeat(root: View, fsRepeat: ImageButton, mode: RepeatMode) {
        val (icon, label) = when (mode) {
            RepeatMode.ALL -> R.drawable.ic_repeat_primary to R.string.repeat_queue
            RepeatMode.ONE -> R.drawable.ic_repeat_one_primary to R.string.repeat_one
            RepeatMode.OFF -> R.drawable.ic_repeat to R.string.repeat_off
        }
        fsRepeat.setImageDrawable(ResourcesCompat.getDrawable(root.resources, icon, null))
        fsRepeat.contentDescription = root.context.getString(label)
    }

    fun setLiked(root: View, fsLike: ImageButton, liked: Boolean) {
        fsLike.setImageResource(if (liked) R.drawable.ic_nav_like else R.drawable.ic_nav_unlike)
        fsLike.contentDescription = root.context.getString(
            if (liked) R.string.unlike_song else R.string.like_song,
        )
    }
}
