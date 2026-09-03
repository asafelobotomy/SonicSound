package app.sonicsound.fragments

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible

internal object NowPlayingFsVideoSlot {
    fun move(
        musicVideoContainer: FrameLayout,
        mediaSlot: FrameLayout,
        fsMedia: FrameLayout,
        toFullscreen: Boolean,
        videoMode: Boolean,
    ) {
        val parent = musicVideoContainer.parent as? ViewGroup
        parent?.removeView(musicVideoContainer)
        if (toFullscreen) {
            fsMedia.addView(
                musicVideoContainer,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            musicVideoContainer.isVisible = videoMode
        } else {
            mediaSlot.addView(
                musicVideoContainer,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }
}
