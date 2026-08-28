package app.sonicsound.fragments

import android.app.AlertDialog
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import app.sonicsound.R
import app.sonicsound.models.InternetRadioStation
import app.sonicsound.subsonic.SubsonicClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shared TV dialogs for playlist and internet-radio library management. */
object TvLibraryDialogs {
    fun showCreatePlaylist(fragment: Fragment, client: SubsonicClient, onDone: () -> Unit) {
        val input = EditText(fragment.requireContext()).apply {
            hint = fragment.getString(R.string.playlist_name_hint)
            setText(fragment.getString(R.string.new_playlist_default_name))
        }
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.new_playlist)
            .setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text.toString().trim().ifBlank {
                    fragment.getString(R.string.new_playlist_default_name)
                }
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            client.createPlaylist(emptyList(), name)
                        }
                        toast(fragment, R.string.playlist_created)
                        onDone()
                    } catch (e: Exception) {
                        toast(fragment, e.message ?: fragment.getString(R.string.error_generic))
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showAddToPlaylist(fragment: Fragment, client: SubsonicClient, songId: String) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val playlists = try {
                withContext(Dispatchers.IO) { client.getPlaylists() }
            } catch (e: Exception) {
                toast(fragment, e.message ?: fragment.getString(R.string.error_generic))
                return@launch
            }
            val labels = buildList {
                add(fragment.getString(R.string.create_playlist_and_add))
                addAll(playlists.map { it.name })
            }
            AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.add_to_playlist)
                .setItems(labels.toTypedArray()) { _, which ->
                    fragment.viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                if (which == 0) {
                                    client.createPlaylist(
                                        listOf(songId),
                                        fragment.getString(R.string.new_playlist_default_name)
                                    )
                                } else {
                                    client.addToPlaylist(playlists[which - 1].id, songId)
                                }
                            }
                            toast(fragment, R.string.song_added_to_playlist)
                        } catch (e: Exception) {
                            toast(fragment, e.message ?: fragment.getString(R.string.error_generic))
                        }
                    }
                }
                .show()
        }
    }

    fun showRenamePlaylist(
        fragment: Fragment,
        client: SubsonicClient,
        playlistId: String,
        currentName: String,
        onDone: () -> Unit,
    ) {
        val input = EditText(fragment.requireContext()).apply {
            setText(currentName)
            hint = fragment.getString(R.string.playlist_name_hint)
        }
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.rename_playlist)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val playlist = withContext(Dispatchers.IO) {
                            client.getPlaylist(playlistId)
                        }
                        withContext(Dispatchers.IO) {
                            client.renamePlaylist(
                                playlistId,
                                name,
                                playlist.comment,
                                playlist.public
                            )
                        }
                        toast(fragment, R.string.playlist_saved)
                        onDone()
                    } catch (e: Exception) {
                        toast(fragment, e.message ?: fragment.getString(R.string.error_generic))
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showDeletePlaylist(
        fragment: Fragment,
        client: SubsonicClient,
        playlistId: String,
        playlistName: String,
        onDone: () -> Unit,
    ) {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.delete_playlist)
            .setMessage(fragment.getString(R.string.delete_playlist_confirm, playlistName))
            .setPositiveButton(R.string.delete) { _, _ ->
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) { client.removePlaylist(playlistId) }
                        toast(fragment, R.string.playlist_deleted)
                        onDone()
                    } catch (e: Exception) {
                        toast(fragment, e.message ?: fragment.getString(R.string.error_generic))
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showRadioStationForm(
        fragment: Fragment,
        client: SubsonicClient,
        existing: InternetRadioStation?,
        onDone: () -> Unit,
    ) {
        val view = LayoutInflater.from(fragment.requireContext())
            .inflate(R.layout.dialog_radio_station, null, false)
        val nameInput = view.findViewById<EditText>(R.id.et_radio_name)
        val streamInput = view.findViewById<EditText>(R.id.et_radio_stream_url)
        val homeInput = view.findViewById<EditText>(R.id.et_radio_homepage_url)
        existing?.let {
            nameInput.setText(it.name)
            streamInput.setText(it.streamUrl)
            homeInput.setText(it.homePageUrl.orEmpty())
        }
        val title = if (existing == null) R.string.add_radio_station else R.string.edit_radio_station
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(title)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameInput.text.toString().trim()
                val streamUrl = streamInput.text.toString().trim()
                val homePageUrl = homeInput.text.toString().trim()
                if (name.isEmpty() || streamUrl.isEmpty()) {
                    toast(fragment, R.string.radio_fields_required)
                    return@setPositiveButton
                }
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            if (existing == null) {
                                client.createInternetRadioStation(name, streamUrl, homePageUrl)
                            } else {
                                client.updateInternetRadioStation(
                                    existing.id, name, streamUrl, homePageUrl
                                )
                            }
                        }
                        toast(
                            fragment,
                            if (existing == null) R.string.radio_station_created
                            else R.string.radio_station_saved
                        )
                        onDone()
                    } catch (e: Exception) {
                        val msg = e.message.orEmpty()
                        toast(
                            fragment,
                            if (msg.contains("50", ignoreCase = true) ||
                                msg.contains("admin", ignoreCase = true) ||
                                msg.contains("authorization", ignoreCase = true)
                            ) {
                                fragment.getString(R.string.radio_admin_required)
                            } else {
                                msg.ifBlank { fragment.getString(R.string.error_generic) }
                            }
                        )
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showDeleteRadioStation(
        fragment: Fragment,
        client: SubsonicClient,
        station: InternetRadioStation,
        onDone: () -> Unit,
    ) {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.delete_radio_station)
            .setMessage(fragment.getString(R.string.delete_radio_confirm, station.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            client.deleteInternetRadioStation(station.id)
                        }
                        toast(fragment, R.string.radio_station_deleted)
                        onDone()
                    } catch (e: Exception) {
                        val msg = e.message.orEmpty()
                        toast(
                            fragment,
                            if (msg.contains("admin", ignoreCase = true) ||
                                msg.contains("authorization", ignoreCase = true)
                            ) {
                                fragment.getString(R.string.radio_admin_required)
                            } else {
                                msg.ifBlank { fragment.getString(R.string.error_generic) }
                            }
                        )
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(fragment: Fragment, messageRes: Int) {
        Toast.makeText(fragment.requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun toast(fragment: Fragment, message: String) {
        Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
