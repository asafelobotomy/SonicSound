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
import androidx.lifecycle.lifecycleScope
import app.sonicsound.models.Settings
import app.sonicsound.subsonic.SubsonicClient
import app.sonicsound.youtube.YoutubeOAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment {
    private var oauthPolling = false

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
        val ytClientId = view.findViewById<EditText>(R.id.et_youtube_client_id)
        val ytClientSecret = view.findViewById<EditText>(R.id.et_youtube_client_secret)
        val ytStatus = view.findViewById<TextView>(R.id.tv_youtube_oauth_status)
        val ytCode = view.findViewById<TextView>(R.id.tv_youtube_user_code)
        val cacheInfo = view.findViewById<TextView>(R.id.tv_cache_info)
        val settings = KeyValueStorage.getSettings()
        transcoding.setText(settings.transcoding)
        eqSwitch.isChecked = settings.eqEnabled
        rgSwitch.isChecked = settings.replayGainEnabled
        ytSwitch.isChecked = settings.youtubeVideosEnabled
        ytAny.isChecked = settings.youtubeAllowAnyChannel
        ytClientId.setText(settings.youtubeOauthClientId)
        ytClientSecret.setText(settings.youtubeOauthClientSecret)
        refreshOauthStatus(ytStatus)

        view.findViewById<Button>(R.id.btn_save_settings).setOnClickListener {
            savePrefs(transcoding, eqSwitch, rgSwitch, ytSwitch, ytAny, ytClientId, ytClientSecret)
            Toast.makeText(requireContext(), R.string.save_settings, Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.btn_youtube_signin).setOnClickListener {
            savePrefs(transcoding, eqSwitch, rgSwitch, ytSwitch, ytAny, ytClientId, ytClientSecret)
            startOauth(ytStatus, ytCode)
        }
        view.findViewById<Button>(R.id.btn_youtube_signout).setOnClickListener {
            YoutubeOAuth.clearTokens(KeyValueStorage.getSettings())
            ytCode.text = ""
            refreshOauthStatus(ytStatus)
        }
        view.findViewById<Button>(R.id.btn_youtube_premium).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/premium")))
        }
        view.findViewById<Button>(R.id.btn_clear_cache).setOnClickListener {
            val freed = SubsonicClient(KeyValueStorage.getActiveAccount()).clearCoverCache()
            Toast.makeText(
                requireContext(),
                "Cleared ${freed / 1024} KB of cached art",
                Toast.LENGTH_SHORT
            ).show()
            refreshCache(cacheInfo)
        }
        refreshCache(cacheInfo)
    }

    private fun savePrefs(
        transcoding: EditText,
        eqSwitch: SwitchCompat,
        rgSwitch: SwitchCompat,
        ytSwitch: SwitchCompat,
        ytAny: SwitchCompat,
        ytClientId: EditText,
        ytClientSecret: EditText,
    ) {
        val current = KeyValueStorage.getSettings()
        KeyValueStorage.setSettings(
            current.copy(
                transcoding = transcoding.text?.toString().orEmpty(),
                eqEnabled = eqSwitch.isChecked,
                replayGainEnabled = rgSwitch.isChecked,
                youtubeVideosEnabled = ytSwitch.isChecked,
                youtubeAllowAnyChannel = ytAny.isChecked,
                youtubeOauthClientId = ytClientId.text?.toString().orEmpty(),
                youtubeOauthClientSecret = ytClientSecret.text?.toString().orEmpty(),
            )
        )
    }

    private fun startOauth(status: TextView, codeView: TextView) {
        val s = KeyValueStorage.getSettings()
        if (s.youtubeOauthClientId.isBlank()) {
            Toast.makeText(requireContext(), R.string.youtube_oauth_needs_client, Toast.LENGTH_LONG)
                .show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val auth = withContext(Dispatchers.IO) {
                    YoutubeOAuth.startDeviceAuth(s.youtubeOauthClientId)
                }
                codeView.text = getString(R.string.youtube_oauth_code_fmt, auth.userCode)
                status.text = getString(R.string.youtube_oauth_waiting)
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(auth.verificationUrl)))
                oauthPolling = true
                withContext(Dispatchers.IO) {
                    val deadline = System.currentTimeMillis() + auth.expiresInSec * 1000L
                    while (isActive && oauthPolling && System.currentTimeMillis() < deadline) {
                        delay(auth.intervalSec * 1000L)
                        val tokens = try {
                            YoutubeOAuth.pollToken(
                                s.youtubeOauthClientId,
                                s.youtubeOauthClientSecret,
                                auth.deviceCode,
                            )
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                status.text = e.message
                                oauthPolling = false
                            }
                            return@withContext
                        }
                        if (tokens != null) {
                            YoutubeOAuth.persistTokens(KeyValueStorage.getSettings(), tokens)
                            withContext(Dispatchers.Main) {
                                status.text = getString(R.string.youtube_oauth_signed_in)
                                codeView.text = ""
                            }
                            oauthPolling = false
                            return@withContext
                        }
                    }
                }
            } catch (e: Exception) {
                status.text = e.message
            }
        }
    }

    private fun refreshOauthStatus(status: TextView) {
        status.text = if (YoutubeOAuth.hasSession()) {
            getString(R.string.youtube_oauth_signed_in)
        } else {
            getString(R.string.youtube_oauth_signed_out)
        }
    }

    private fun refreshCache(cacheInfo: TextView) {
        try {
            val bytes = SubsonicClient(KeyValueStorage.getActiveAccount()).getCoverCacheSizeBytes()
            cacheInfo.text = "Art cache: ${bytes / 1024} KB"
        } catch (_: Exception) {
            cacheInfo.text = "Art cache: unavailable"
        }
    }

    override fun onDestroyView() {
        oauthPolling = false
        super.onDestroyView()
    }
}
