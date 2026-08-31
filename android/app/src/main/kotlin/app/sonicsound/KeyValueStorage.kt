package app.sonicsound

import android.content.Context
import android.content.SharedPreferences
import app.sonicsound.models.Account
import app.sonicsound.models.Album
import app.sonicsound.models.Playlist
import app.sonicsound.models.Settings
import app.sonicsound.models.Song
import app.sonicsound.playback.AudioProfile
import com.google.gson.Gson

class KeyValueStorage {
    companion object {
        private const val PREFS_NAME = "sonicSound"
        private const val PREFS_LEGACY = "sonicLair"
        private const val MIGRATED_KEY = "_migratedFromSonicLair"

        private fun prefs(): SharedPreferences {
            val ctx = App.context
            val current = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!current.getBoolean(MIGRATED_KEY, false)) {
                val legacy = ctx.getSharedPreferences(PREFS_LEGACY, Context.MODE_PRIVATE)
                if (legacy.all.isNotEmpty()) {
                    val editor = current.edit()
                    for ((key, value) in legacy.all) {
                        when (value) {
                            is String -> editor.putString(key, value)
                            is Boolean -> editor.putBoolean(key, value)
                            is Int -> editor.putInt(key, value)
                            is Long -> editor.putLong(key, value)
                            is Float -> editor.putFloat(key, value)
                            is Set<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                editor.putStringSet(key, value as Set<String>)
                            }
                        }
                    }
                    editor.putBoolean(MIGRATED_KEY, true)
                    editor.apply()
                } else {
                    current.edit().putBoolean(MIGRATED_KEY, true).apply()
                }
            }
            return current
        }

        fun getSettings(): Settings {
            val raw = prefs().getString("settings", "")
            return try {
                Gson().fromJson(raw, Settings::class.java) ?: Settings()
            } catch (_: Exception) {
                Settings()
            }
        }

        fun setSettings(settings: Settings) {
            val previous = getSettings()
            val normalized = AudioProfile.normalize(settings)
            prefs().edit().putString("settings", Gson().toJson(normalized)).apply()
            val audioChanged =
                AudioProfile.resolve(previous) != AudioProfile.resolve(normalized) ||
                    previous.replayGainEnabled != normalized.replayGainEnabled
            if (audioChanged) {
                Globals.NotifyObservers("AUDIO_SETTINGS", "")
            }
            if (previous.fullscreenVisualizer != normalized.fullscreenVisualizer ||
                previous.fullscreenSolidColor != normalized.fullscreenSolidColor ||
                previous.dvdSpeed != normalized.dvdSpeed ||
                previous.fullscreenShowClock != normalized.fullscreenShowClock ||
                previous.fullscreenShowDate != normalized.fullscreenShowDate
            ) {
                Globals.NotifyObservers("VISUALIZER_SETTINGS", normalized.fullscreenVisualizer)
            }
        }

        fun getActiveAccount(): Account {
            val raw = prefs().getString("activeAccount", "")
            return try {
                Gson().fromJson(raw, Account::class.java)
            } catch (_: Exception) {
                Account(null, "", "", "", false)
            }
        }

        fun setActiveAccount(account: Account) {
            prefs().edit().putString("activeAccount", Gson().toJson(account)).apply()
        }

        fun getAccounts(): List<Account> {
            val raw = prefs().getString("accounts", "")
            return try {
                Gson().fromJson(raw, Array<Account>::class.java).toList()
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun setAccounts(accounts: List<Account>) {
            prefs().edit().putString("accounts", Gson().toJson(accounts)).apply()
        }

        fun getCachedSongs(): List<Song> {
            val raw = prefs().getString("cachedSongs", "")
            return try {
                Gson().fromJson(raw, Array<Song>::class.java).toList()
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun setCachedSongs(songs: List<Song>) {
            prefs().edit().putString("cachedSongs", Gson().toJson(songs)).apply()
        }

        fun getCachedPlaylists(): List<Playlist> {
            val raw = prefs().getString("cachedPlaylists", "")
            return try {
                Gson().fromJson(raw, Array<Playlist>::class.java).toList()
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun setCachedPlaylists(playlists: List<Playlist>) {
            prefs().edit().putString("cachedPlaylists", Gson().toJson(playlists)).apply()
        }

        fun getCachedAlbums(): List<Album> {
            val raw = prefs().getString("cachedAlbums", "")
            return try {
                Gson().fromJson(raw, Array<Album>::class.java).toList()
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun setCachedAlbums(albums: List<Album>) {
            prefs().edit().putString("cachedAlbums", Gson().toJson(albums)).apply()
        }

        fun getOfflineMode(): Boolean = prefs().getBoolean("offlineMode", false)

        fun setOfflineMode(value: Boolean) {
            prefs().edit().putBoolean("offlineMode", value).apply()
        }

        fun getTranscoding(): String = prefs().getString("transcoding", "") ?: ""

        fun setTranscoding(value: String) {
            prefs().edit().putString("transcoding", value).apply()
        }

        fun getRemoteDeviceName(): String = prefs().getString("remoteDeviceName", "") ?: ""

        fun setRemoteDeviceName(value: String) {
            prefs().edit().putString("remoteDeviceName", value).apply()
        }

        fun getLastRemoteIp(): String = prefs().getString("lastRemoteIp", "") ?: ""

        fun setLastRemoteIp(value: String) {
            prefs().edit().putString("lastRemoteIp", value).apply()
        }

        fun getLastRemoteDeviceName(): String = prefs().getString("lastRemoteDeviceName", "") ?: ""

        fun setLastRemoteDeviceName(value: String) {
            prefs().edit().putString("lastRemoteDeviceName", value).apply()
        }
    }
}
