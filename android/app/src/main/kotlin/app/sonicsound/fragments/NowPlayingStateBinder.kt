package app.sonicsound.fragments

import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import app.sonicsound.TvActivity
import app.sonicsound.adapters.SonicSoundPlaylistItemAdapter
import app.sonicsound.playback.RepeatMode
import app.sonicsound.subsonic.SubsonicClient

internal object NowPlayingStateBinder {
    fun applyMediaAspect(mediaFrame: android.widget.FrameLayout, image: ImageView, widthPx: Int, heightPx: Int) {
        if (heightPx <= 0) return
        val ratio = widthPx.toFloat() / heightPx.toFloat()
        val params = mediaFrame.layoutParams as ConstraintLayout.LayoutParams
        params.dimensionRatio = when {
            ratio in 1.20f..1.45f -> "H,4:3"
            ratio < 1.15f -> "H,1:1"
            else -> "H,16:9"
        }
        mediaFrame.layoutParams = params
        image.scaleType = ImageView.ScaleType.FIT_CENTER
    }

    fun bindState(
        bind: TvActivity.TvActivityBind,
        client: SubsonicClient,
        firstLine: TextView,
        secondLine: TextView,
        durationText: TextView,
        playlistRecyclerView: RecyclerView,
        playlistAdapter: SonicSoundPlaylistItemAdapter,
        fullscreen: NowPlayingFullscreen?,
        musicVideo: NowPlayingMusicVideo?,
        lastQueueIds: String,
        keepQueueFocus: Boolean,
        liked: Boolean,
        secondsToHHSS: (Int) -> String,
        loadAlbumArt: (String) -> Unit,
        setPlayingUi: (Boolean) -> Unit,
        startProgressTicker: (Boolean) -> Unit,
        updateShuffleUi: (Boolean) -> Unit,
        updateRepeatUi: (RepeatMode) -> Unit,
        updateLikeUi: () -> Unit,
        setLiked: (Boolean) -> Unit,
        setLastQueueIds: (String) -> Unit,
        setKeepQueueFocus: (Boolean) -> Unit,
    ): Boolean {
        val currentState = bind.getCurrentState()
        if (currentState == null || currentState.currentTrack.id == "") return false
        firstLine.text = currentState.currentTrack.title
        secondLine.text = currentState.currentTrack.artist
        val artUrl = client.getAlbumArtForDisplay(currentState.currentTrack.albumId)
        loadAlbumArt(artUrl)
        durationText.text = secondsToHHSS(currentState.currentTrack.duration)
        setPlayingUi(currentState.playing)
        startProgressTicker(currentState.playing)
        updateShuffleUi(currentState.shuffling)
        updateRepeatUi(RepeatMode.fromWire(currentState.repeatMode))
        setLiked(currentState.currentTrack.isStarred)
        updateLikeUi()
        val entries = bind.getCurrentPlaylist()?.entry.orEmpty()
        val idx = entries.indexOfFirst { it.id == currentState.currentTrack.id }
        val next = entries.getOrNull(idx + 1)
        var queueIds = lastQueueIds
        var keepFocus = keepQueueFocus
        if (entries.isNotEmpty()) {
            val ids = entries.joinToString(",") { it.id }
            if (ids != queueIds) {
                queueIds = ids
                for (song in entries) song.image = client.getAlbumArt(song.albumId)
                playlistAdapter.setNewDataSet(entries)
            }
            val retain = keepFocus || playlistRecyclerView.hasFocus()
            keepFocus = false
            playlistAdapter.updateSelected(idx, keepFocus = retain)
        }
        setLastQueueIds(queueIds)
        setKeepQueueFocus(keepFocus)
        if (fullscreen?.active == true) {
            fullscreen.updateTrack(
                artUrl,
                currentState.currentTrack.title,
                currentState.currentTrack.artist,
                next,
                musicVideo?.isVideoActive == true,
            )
            fullscreen.setPlaying(currentState.playing)
            fullscreen.setShuffle(currentState.shuffling)
            fullscreen.setRepeat(RepeatMode.fromWire(currentState.repeatMode))
            fullscreen.setLiked(currentState.currentTrack.isStarred)
        }
        return true
    }
}
