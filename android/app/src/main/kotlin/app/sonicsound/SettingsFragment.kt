package app.sonicsound

import android.content.Intent
import android.net.Uri
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
import app.sonicsound.models.Settings
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
        val eqSwitch = view.findViewById<SwitchCompat>(R.id.switch_eq)
        val rgSwitch = view.findViewById<SwitchCompat>(R.id.switch_replaygain)
        val ytSwitch = view.findViewById<SwitchCompat>(R.id.switch_youtube_videos)
        val ytAny = view.findViewById<SwitchCompat>(R.id.switch_youtube_any_channel)
        val ytKey = view.findViewById<EditText>(R.id.et_youtube_api_key)
        val cacheInfo = view.findViewById<TextView>(R.id.tv_cache_info)
        val settings = KeyValueStorage.getSettings()
        transcoding.setText(settings.transcoding)
        eqSwitch.isChecked = settings.eqEnabled
        rgSwitch.isChecked = settings.replayGainEnabled
        ytSwitch.isChecked = settings.youtubeVideosEnabled
        ytAny.isChecked = settings.youtubeAllowAnyChannel
        ytKey.setText(settings.youtubeApiKey)

        view.findViewById<Button>(R.id.btn_save_settings).setOnClickListener {
            val current = KeyValueStorage.getSettings()
            KeyValueStorage.setSettings(
                Settings(
                    cacheSize = current.cacheSize,
                    transcoding = transcoding.text?.toString().orEmpty(),
                    eqEnabled = eqSwitch.isChecked,
                    replayGainEnabled = rgSwitch.isChecked,
                    youtubeApiKey = ytKey.text?.toString().orEmpty(),
                    youtubeVideosEnabled = ytSwitch.isChecked,
                    youtubeAllowAnyChannel = ytAny.isChecked,
                )
            )
            Toast.makeText(requireContext(), R.string.save_settings, Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.btn_youtube_premium).setOnClickListener {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/premium")
            )
            startActivity(intent)
        }

        view.findViewById<Button>(R.id.btn_clear_cache).setOnClickListener {
            val client = SubsonicClient(KeyValueStorage.getActiveAccount())
            val freed = client.clearCoverCache()
            Toast.makeText(
                requireContext(),
                "Cleared ${freed / 1024} KB of cached art",
                Toast.LENGTH_SHORT
            ).show()
            refreshCache(cacheInfo)
        }
        refreshCache(cacheInfo)
    }

    private fun refreshCache(cacheInfo: TextView) {
        try {
            val bytes = SubsonicClient(KeyValueStorage.getActiveAccount()).getCoverCacheSizeBytes()
            cacheInfo.text = "Art cache: ${bytes / 1024} KB"
        } catch (_: Exception) {
            cacheInfo.text = "Art cache: unavailable"
        }
    }
}
