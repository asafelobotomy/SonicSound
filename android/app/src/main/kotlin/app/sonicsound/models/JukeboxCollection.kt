package app.sonicsound.models

import com.google.gson.Gson
import com.google.gson.JsonObject

/** Continuous Jukebox playback source (Collection). */
sealed class JukeboxCollection {
    abstract val label: String

    data object Random : JukeboxCollection() {
        override val label: String = "Random"
    }

    data class Genre(val genre: String) : JukeboxCollection() {
        override val label: String = genre
    }

    data class Artist(val artistId: String, val artistName: String) : JukeboxCollection() {
        override val label: String = artistName
    }

    data class Decade(val fromYear: Int, val toYear: Int) : JukeboxCollection() {
        override val label: String = decadeLabel(fromYear)
    }

    data class Similar(
        val seedSongId: String,
        val seedTitle: String,
        var currentSeedId: String = seedSongId,
    ) : JukeboxCollection() {
        override val label: String = "Similar · $seedTitle"
    }

    data object Starred : JukeboxCollection() {
        override val label: String = "Starred"
    }

    data class ServerPlaylist(val playlistId: String, val playlistName: String) : JukeboxCollection() {
        override val label: String = playlistName
    }

    companion object {
        private val gson = Gson()

        fun decadeLabel(fromYear: Int): String = "${fromYear / 10 * 10}s"

        fun toJson(collection: JukeboxCollection): String {
            val obj = JsonObject()
            when (collection) {
                is Random -> obj.addProperty("type", "random")
                is Genre -> {
                    obj.addProperty("type", "genre")
                    obj.addProperty("genre", collection.genre)
                }
                is Artist -> {
                    obj.addProperty("type", "artist")
                    obj.addProperty("artistId", collection.artistId)
                    obj.addProperty("artistName", collection.artistName)
                }
                is Decade -> {
                    obj.addProperty("type", "decade")
                    obj.addProperty("fromYear", collection.fromYear)
                    obj.addProperty("toYear", collection.toYear)
                }
                is Similar -> {
                    obj.addProperty("type", "similar")
                    obj.addProperty("seedSongId", collection.seedSongId)
                    obj.addProperty("seedTitle", collection.seedTitle)
                    obj.addProperty("currentSeedId", collection.currentSeedId)
                }
                is Starred -> obj.addProperty("type", "starred")
                is ServerPlaylist -> {
                    obj.addProperty("type", "server")
                    obj.addProperty("playlistId", collection.playlistId)
                    obj.addProperty("playlistName", collection.playlistName)
                }
            }
            return gson.toJson(obj)
        }

        fun fromJson(json: String): JukeboxCollection {
            val tree = gson.fromJson(json, JsonObject::class.java)
            return when (tree.get("type")?.asString) {
                "random" -> Random
                "genre" -> Genre(tree.get("genre").asString)
                "artist" -> Artist(
                    tree.get("artistId").asString,
                    tree.get("artistName").asString,
                )
                "decade" -> Decade(
                    tree.get("fromYear").asInt,
                    tree.get("toYear").asInt,
                )
                "similar" -> Similar(
                    tree.get("seedSongId").asString,
                    tree.get("seedTitle").asString,
                    tree.get("currentSeedId")?.asString ?: tree.get("seedSongId").asString,
                )
                "starred" -> Starred
                "server" -> ServerPlaylist(
                    tree.get("playlistId").asString,
                    tree.get("playlistName").asString,
                )
                else -> Random
            }
        }
    }
}
