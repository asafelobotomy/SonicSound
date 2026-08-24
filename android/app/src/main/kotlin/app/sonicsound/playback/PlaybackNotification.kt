package app.sonicsound.playback

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.graphics.Bitmap
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import app.sonicsound.App
import app.sonicsound.MainActivity
import app.sonicsound.NotificationBroadcastReceiver
import app.sonicsound.R
import app.sonicsound.models.Song

/** Foreground media notification for MusicService. */
class PlaybackNotification(
    private val service: android.app.Service,
    sessionToken: MediaSessionCompat.Token
) {
    private val notificationManager: NotificationManagerCompat =
        NotificationManagerCompat.from(App.context)
    private val channel: NotificationChannelCompat = NotificationChannelCompat
        .Builder("sonicsound", NotificationManagerCompat.IMPORTANCE_LOW)
        .setName("SonicSound")
        .setDescription("Currently playing notification")
        .build()

    private val mediaStyle: MediaStyle = MediaStyle()
        .setMediaSession(sessionToken)
        .setShowActionsInCompactView(1, 2)

    var prevAction: NotificationCompat.Action? = null
        private set
    var pauseAction: NotificationCompat.Action? = null
        private set
    var playAction: NotificationCompat.Action? = null
        private set
    var nextAction: NotificationCompat.Action? = null
        private set
    var cancelAction: NotificationCompat.Action? = null
        private set

    private var isForeground: Boolean = false
    val notifId = 1

    fun createChannel() {
        notificationManager.createNotificationChannel(channel)
    }

    fun buildActions() {
        prevAction = action("SLPREV", R.drawable.ic_skip_previous, "PREV")
        pauseAction = action("SLPAUSE", R.drawable.ic_pause, "PAUSE")
        playAction = action("SLPAUSE", R.drawable.ic_play_arrow, "PLAY")
        nextAction = action("SLNEXT", R.drawable.ic_skip_next, "NEXT")
        cancelAction = action("SLCANCEL", R.drawable.ic_action_cancel, "CANCEL")
    }

    private fun action(actionName: String, icon: Int, title: String): NotificationCompat.Action {
        val intent = Intent(App.context, NotificationBroadcastReceiver::class.java)
        intent.action = actionName
        val pending = PendingIntent.getBroadcast(
            App.context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(icon, title, pending).build()
    }

    private fun builder(): NotificationCompat.Builder {
        return NotificationCompat.Builder(App.context, "sonicsound")
            .setSmallIcon(R.drawable.ic_stat_sonicsound)
            .setStyle(mediaStyle)
            .setChannelId("sonicsound")
    }

    fun update(currentTrack: Song, albumArtBitmap: Bitmap?, showPlay: Boolean = false) {
        val notificationBuilder = builder()
        if (albumArtBitmap != null) {
            notificationBuilder.setLargeIcon(albumArtBitmap)
        }
        val intent = Intent(service, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        notificationBuilder.setContentIntent(
            PendingIntent.getActivity(service, 0, intent, flags)
        )
        notificationBuilder.setContentTitle(currentTrack.title)
        notificationBuilder.setContentText(currentTrack.album)
        notificationBuilder.setChannelId("sonicsound")
        notificationBuilder.clearActions()
        notificationBuilder.addAction(prevAction)
        notificationBuilder.addAction(if (showPlay) playAction else pauseAction)
        notificationBuilder.addAction(nextAction)
        notificationBuilder.addAction(cancelAction)
        notificationBuilder.setOngoing(true)

        val notif = notificationBuilder.build()
        if (!isForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                service.startForeground(notifId, notif, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                service.startForeground(notifId, notif)
            }
            isForeground = true
        }
        notificationManager.notify(notifId, notif)
    }

    fun cancel() {
        notificationManager.cancel(notifId)
    }

    fun stopForeground() {
        service.stopForeground(true)
        isForeground = false
    }
}
