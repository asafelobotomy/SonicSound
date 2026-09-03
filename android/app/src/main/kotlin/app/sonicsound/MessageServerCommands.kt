package app.sonicsound

import android.content.Intent
import com.google.gson.Gson
import org.java_websocket.WebSocket
import app.sonicsound.App.Companion.context
import app.sonicsound.models.SetPlaylistAndPlayRequest
import app.sonicsound.models.WebSocketCommand
import app.sonicsound.services.MusicService
import app.sonicsound.services.MusicService.LocalBinder
import java.lang.Integer.parseInt

/** Playback command dispatch for authenticated [MessageServer] remotes. */
internal object MessageServerCommands {
    fun handle(
        command: WebSocketCommand,
        conn: WebSocket,
        binder: LocalBinder?,
        mBound: Boolean,
        gson: Gson,
        constructMessage: (String, String) -> String,
    ) {
        when (command.command) {
            "play" -> if (mBound) binder!!.play()
            "pause" -> if (mBound) binder!!.pause()
            "next" -> if (mBound) binder!!.next()
            "prev" -> if (mBound) binder!!.prev()
            "skipTo" -> {
                val track: Int
                try {
                    track = parseInt(command.data)
                } catch (_: Exception) {
                    conn.send(constructMessage("The parameter track is empty or malformed", "error"))
                    return
                }
                if (mBound) binder!!.skipTo(track)
            }
            "playAlbum" -> {
                val id = command.data.substringBefore('|')
                val track = parseInt(command.data.substringAfter('|'))
                if (id.isBlank()) {
                    conn.send(constructMessage("The parameter id is empty", "error"))
                    return
                }
                if (mBound) {
                    binder!!.playAlbum(id, track)
                } else {
                    val intent = Intent(context, MusicService::class.java)
                    intent.action = Constants.SERVICE_PLAY_ALBUM
                    intent.putExtra("id", id)
                    intent.putExtra("track", track)
                    context.startService(intent)
                }
            }
            "playPlaylist" -> {
                val id = command.data.substringBefore('|')
                val track = parseInt(command.data.substringAfter('|'))
                if (id.isBlank()) {
                    conn.send(constructMessage("The parameter id is empty", "error"))
                    return
                }
                if (mBound) {
                    binder!!.playPlaylist(id, track)
                } else {
                    val intent = Intent(context, MusicService::class.java)
                    intent.action = Constants.SERVICE_PLAY_PLAYLIST
                    intent.putExtra("id", id)
                    intent.putExtra("track", track)
                    context.startService(intent)
                }
            }
            "playRadio" -> {
                if (command.data.isBlank()) {
                    conn.send(constructMessage("The parameter id is empty", "error"))
                    return
                }
                if (mBound) {
                    binder!!.playRadio(command.data)
                } else {
                    val intent = Intent(context, MusicService::class.java)
                    intent.action = Constants.SERVICE_PLAY_RADIO
                    intent.putExtra("id", command.data)
                    context.startService(intent)
                }
            }
            "setPlaylistAndPlay" -> {
                if (command.data.isBlank()) {
                    conn.send(constructMessage("The parameters id is empty", "error"))
                    return
                }
                val request: SetPlaylistAndPlayRequest
                try {
                    request = gson.fromJson(command.data, SetPlaylistAndPlayRequest::class.java)
                    if (request.track >= request.playlist.entry.orEmpty().size) {
                        throw Exception("The track parameter was out of bounds")
                    }
                } catch (e: Exception) {
                    Globals.NotifyObservers("EX", e.message)
                    return
                }
                if (mBound) {
                    binder!!.setPlaylistAndPlay(
                        request.playlist, request.track, request.seek, request.playing,
                    )
                }
            }
            "playJukeboxCollection" -> {
                if (command.data.isBlank()) {
                    conn.send(constructMessage("Collection payload is empty", "error"))
                    return
                }
                if (mBound) binder!!.playJukeboxCollection(command.data)
            }
            "shufflePlaylist" -> {
                if (mBound) binder!!.shuffle()
            }
            "cycleRepeat" -> {
                if (mBound) binder!!.cycleRepeat()
            }
            "seek" -> {
                if (command.data.isBlank()) {
                    conn.send(constructMessage("The parameter time is empty", "error"))
                    return
                }
                val time = command.data.toFloatOrNull()
                if (time == null) {
                    conn.send(constructMessage("The parameter time is malformed", "error"))
                    return
                }
                if (mBound) binder!!.seek(time)
            }
            "setVolume" -> {
                val volume = command.data.toIntOrNull()
                if (volume == null) {
                    conn.send(constructMessage("The parameter volume is empty or malformed", "error"))
                    return
                }
                if (mBound) binder!!.setVolume(volume.coerceIn(0, 100))
            }
            "playInternetRadio" -> {
                if (command.data.isBlank()) {
                    conn.send(constructMessage("The radio payload is empty", "error"))
                    return
                }
                try {
                    val payload = gson.fromJson(command.data, Map::class.java)
                    val streamUrl = payload["streamUrl"] as? String
                    val name = (payload["name"] as? String) ?: "Radio"
                    if (streamUrl.isNullOrBlank()) {
                        conn.send(constructMessage("The parameter streamUrl is empty", "error"))
                        return
                    }
                    if (mBound) binder!!.playInternetRadio(streamUrl, name)
                } catch (e: Exception) {
                    conn.send(
                        constructMessage(e.message ?: "Malformed playInternetRadio payload", "error"),
                    )
                }
            }
        }
    }
}
