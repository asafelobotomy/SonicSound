package app.sonicsound.playback

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import app.sonicsound.Globals
import app.sonicsound.models.Song

/** MediaSession metadata and playback-state updates (includes SEEK_TO for Fire TV). */
class MediaSessionController {
    private val mediaSession: MediaSessionCompat = Globals.GetMediaSession()
    private val metadataBuilder: MediaMetadataCompat.Builder = MediaMetadataCompat.Builder()

    val sessionToken get() = mediaSession.sessionToken

    fun playbackStateBuilder(): PlaybackStateCompat.Builder {
        return PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY
                        or PlaybackStateCompat.ACTION_PAUSE
                        or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                        or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                        or PlaybackStateCompat.ACTION_SEEK_TO
            )
    }

    fun updatePlaybackState(playbackState: PlaybackStateCompat) {
        mediaSession.setPlaybackState(playbackState)
        mediaSession.isActive = true
    }

    fun setPlayingState(positionMs: Long, speed: Float = 1f) {
        updatePlaybackState(
            playbackStateBuilder()
                .setState(PlaybackStateCompat.STATE_PLAYING, positionMs, speed)
                .build()
        )
    }

    fun setPausedState(positionMs: Long) {
        updatePlaybackState(
            playbackStateBuilder()
                .setState(PlaybackStateCompat.STATE_PAUSED, positionMs, 0f)
                .build()
        )
    }

    fun updateMediaMetadata(currentTrack: Song, albumArtBitmap: Bitmap?) {
        metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArtBitmap)
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, currentTrack.artist)
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_ARTIST, currentTrack.artist)
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_TITLE, currentTrack.title)
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_ALBUM, currentTrack.album)
        metadataBuilder.putLong(
            MediaMetadata.METADATA_KEY_DURATION,
            (currentTrack.duration * 1000).toLong()
        )
        mediaSession.setMetadata(metadataBuilder.build())
    }

    fun putAlbumAndDuration(currentTrack: Song) {
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_ALBUM, currentTrack.album)
        metadataBuilder.putLong(
            MediaMetadata.METADATA_KEY_DURATION,
            (currentTrack.duration * 1000).toLong()
        )
    }
}
