package app.sonicsound

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import app.sonicsound.subsonic.SubsonicClient

class SettingsFragment : Fragment {
    constructor() : super()
    constructor(@Suppress("UNUSED_PARAMETER") client: SubsonicClient) : super()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val transcoding = view.findViewById<EditText>(R.id.et_transcoding)
        val cacheSize = view.findViewById<EditText>(R.id.et_cache_size)
        val eqSwitch = view.findViewById<SwitchCompat>(R.id.switch_eq)
        val rgSwitch = view.findViewById<SwitchCompat>(R.id.switch_replaygain)
        val offlineSwitch = view.findViewById<SwitchCompat>(R.id.switch_offline)
        val cacheInfo = view.findViewById<TextView>(R.id.tv_cache_info)
        val settings = KeyValueStorage.getSettings()
        transcoding.setText(settings.transcoding)
        cacheSize.setText(settings.cacheSize.toString())
        eqSwitch.isChecked = settings.eqEnabled
        rgSwitch.isChecked = settings.replayGainEnabled
        offlineSwitch.isChecked = KeyValueStorage.getOfflineMode()
        refreshCache(cacheInfo)

        view.findViewById<Button>(R.id.btn_save_settings).setOnClickListener {
            val parsedCache =
                cacheSize.text?.toString()?.trim()?.toIntOrNull()?.coerceAtLeast(0)
                    ?: 0
            val current = KeyValueStorage.getSettings()
            KeyValueStorage.setSettings(
                current.copy(
                    transcoding = transcoding.text?.toString().orEmpty(),
                    cacheSize = parsedCache,
                    eqEnabled = eqSwitch.isChecked,
                    replayGainEnabled = rgSwitch.isChecked,
                )
            )
            KeyValueStorage.setOfflineMode(offlineSwitch.isChecked)
            Toast.makeText(requireContext(), R.string.save_settings, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.btn_clear_cache).setOnClickListener {
            val freed = SubsonicClient(KeyValueStorage.getActiveAccount()).clearCoverCache()
            Toast.makeText(
                requireContext(),
                getString(R.string.cache_cleared, freed / 1024),
                Toast.LENGTH_SHORT
            ).show()
            refreshCache(cacheInfo)
        }
    }

    private fun refreshCache(cacheInfo: TextView) {
        try {
            val bytes = SubsonicClient(KeyValueStorage.getActiveAccount()).getCoverCacheSizeBytes()
            cacheInfo.text = getString(R.string.art_cache_size, bytes / 1024)
        } catch (_: Exception) {
            cacheInfo.text = getString(R.string.art_cache_unavailable)
        }
    }
}
