package app.sonicsound

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("SonicSound", "Recevied intent with action " + intent.action)
        Globals.NotifyObservers(intent.action, null)
    }
}