package app.sonicsound

fun interface IBroadcastObserver {
    fun update(action: String?, value: String?)
}
