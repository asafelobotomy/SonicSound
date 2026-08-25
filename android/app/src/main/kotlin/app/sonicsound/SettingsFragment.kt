package app.sonicsound

import android.accounts.Account
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import app.sonicsound.subsonic.SubsonicClient
import app.sonicsound.youtube.YoutubeGoogleAccount
import app.sonicsound.youtube.YoutubeOAuth
import kotlinx.coroutines.launch

class SettingsFragment : Fragment {
    private lateinit var ytStatus: TextView
    private lateinit var ytCode: TextView
    private lateinit var ytSwitch: SwitchCompat

    private val accountChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            finishChooser(result.resultCode, result.data)
        }

    private val recoverLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val account = pendingRecoverAccount
            pendingRecoverAccount = null
            if (account != null) {
                // TV often returns cancelled even after approval — still retry token.
                fetchTokenForAccount(account)
            } else {
                ytStatus.text = getString(R.string.youtube_oauth_signed_out)
                Toast.makeText(
                    requireContext(),
                    "Google approval was not completed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private var pendingRecoverAccount: Account? = null

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
        ytSwitch = view.findViewById(R.id.switch_youtube_videos)
        val ytAny = view.findViewById<SwitchCompat>(R.id.switch_youtube_any_channel)
        ytStatus = view.findViewById(R.id.tv_youtube_oauth_status)
        ytCode = view.findViewById(R.id.tv_youtube_user_code)
        val cacheInfo = view.findViewById<TextView>(R.id.tv_cache_info)
        view.findViewById<View>(R.id.et_youtube_client_id).isVisible = false
        view.findViewById<View>(R.id.et_youtube_client_secret).isVisible = false
        val settings = KeyValueStorage.getSettings()
        transcoding.setText(settings.transcoding)
        eqSwitch.isChecked = settings.eqEnabled
        rgSwitch.isChecked = settings.replayGainEnabled
        ytSwitch.isChecked = settings.youtubeVideosEnabled
        ytAny.isChecked = settings.youtubeAllowAnyChannel
        refreshOauthStatus()
        view.findViewById<TextView>(R.id.tv_youtube_oauth_hint)?.let { hint ->
            hint.text = getString(R.string.youtube_oauth_hint) +
                "\nDebug SHA-1: 12:F6:8F:A8:F6:E9:04:BD:AA:BE:15:BF:D5:B5:21:56:56:08:CE:B8"
        }

        view.findViewById<Button>(R.id.btn_save_settings).setOnClickListener {
            savePrefs(transcoding, eqSwitch, rgSwitch, ytAny)
            Toast.makeText(requireContext(), R.string.save_settings, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.btn_youtube_signin).setOnClickListener {
            savePrefs(transcoding, eqSwitch, rgSwitch, ytAny)
            startGoogleSignIn()
        }
        view.findViewById<Button>(R.id.btn_youtube_signout).setOnClickListener {
            YoutubeGoogleAccount.clear()
            ytCode.text = ""
            refreshOauthStatus()
        }
        view.findViewById<Button>(R.id.btn_youtube_premium).setOnClickListener {
            startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://www.youtube.com/premium")
                )
            )
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
        ytAny: SwitchCompat,
    ) {
        val current = KeyValueStorage.getSettings()
        KeyValueStorage.setSettings(
            current.copy(
                transcoding = transcoding.text?.toString().orEmpty(),
                eqEnabled = eqSwitch.isChecked,
                replayGainEnabled = rgSwitch.isChecked,
                youtubeVideosEnabled = ytSwitch.isChecked,
                youtubeAllowAnyChannel = ytAny.isChecked,
            )
        )
    }

    private fun startGoogleSignIn() {
        ytCode.text = ""
        ytStatus.text = getString(R.string.youtube_oauth_waiting)
        try {
            val accounts = YoutubeGoogleAccount.googleAccounts(requireActivity())
            when {
                accounts.size == 1 -> fetchTokenForAccount(accounts[0])
                accounts.isEmpty() -> {
                    // Chooser can still offer adding / picking an account on TV.
                    YoutubeGoogleAccount.launchAccountChooser(accountChooserLauncher)
                }
                else -> YoutubeGoogleAccount.launchAccountChooser(accountChooserLauncher)
            }
        } catch (e: Exception) {
            showSignInError(YoutubeGoogleAccount.formatError(e))
        }
    }

    private fun finishChooser(resultCode: Int, data: android.content.Intent?) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Always attempt completion — Shield often returns code 0 after a pick.
                val token = YoutubeGoogleAccount.completeAfterChooser(
                    requireActivity(),
                    resultCode,
                    data,
                )
                onSignedIn(token)
            } catch (e: YoutubeGoogleAccount.RecoverableAuth) {
                pendingRecoverAccount = YoutubeGoogleAccount.accountFromChooserResult(data)
                    ?: YoutubeGoogleAccount.googleAccounts(requireActivity()).singleOrNull()
                val intent = e.causeEx.intent
                if (intent != null) recoverLauncher.launch(intent)
                else showSignInError(YoutubeGoogleAccount.formatError(e))
            } catch (e: Exception) {
                showSignInError(YoutubeGoogleAccount.formatError(e))
            }
        }
    }

    private fun fetchTokenForAccount(account: Account) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = YoutubeGoogleAccount.tokenForAccount(requireActivity(), account)
                onSignedIn(token)
            } catch (e: YoutubeGoogleAccount.RecoverableAuth) {
                pendingRecoverAccount = account
                val intent = e.causeEx.intent
                if (intent != null) recoverLauncher.launch(intent)
                else showSignInError(YoutubeGoogleAccount.formatError(e))
            } catch (e: Exception) {
                showSignInError(YoutubeGoogleAccount.formatError(e))
            }
        }
    }

    private fun onSignedIn(token: String) {
        YoutubeGoogleAccount.persistAccessToken(token)
        ytSwitch.isChecked = true
        ytCode.text = ""
        ytStatus.text = getString(R.string.youtube_oauth_signed_in)
        Toast.makeText(requireContext(), R.string.youtube_oauth_signed_in, Toast.LENGTH_SHORT)
            .show()
    }

    private fun showSignInError(msg: String) {
        ytStatus.text = msg
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    }

    private fun refreshOauthStatus() {
        ytStatus.text = if (YoutubeOAuth.hasSession()) {
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
}
