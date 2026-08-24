package app.sonicsound

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import app.sonicsound.models.Account
import app.sonicsound.subsonic.SubsonicClient

class AccountFragment : Fragment() {
    private lateinit var user: TextView
    private lateinit var server: TextView
    private lateinit var type: TextView
    private lateinit var plaintext: TextView
    private lateinit var cacheInfo: TextView
    private lateinit var logout: Button
    private lateinit var clearCache: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_account, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        user = view.findViewById(R.id.tv_account_user)
        server = view.findViewById(R.id.tv_account_server)
        type = view.findViewById(R.id.tv_account_type)
        plaintext = view.findViewById(R.id.tv_plaintext_warning)
        cacheInfo = view.findViewById(R.id.tv_cache_info)
        logout = view.findViewById(R.id.btn_logout)
        clearCache = view.findViewById(R.id.btn_clear_cache)

        val account = KeyValueStorage.getActiveAccount()
        user.text = account.username
        server.text = account.url
        type.text = account.type
        plaintext.visibility = if (account.usePlaintext) View.VISIBLE else View.INVISIBLE

        refreshCacheInfo()

        clearCache.setOnClickListener {
            val client = SubsonicClient(KeyValueStorage.getActiveAccount())
            val freed = client.clearCoverCache()
            Toast.makeText(
                requireContext(),
                "Cleared ${freed / 1024} KB of cached art",
                Toast.LENGTH_SHORT
            ).show()
            refreshCacheInfo()
        }

        logout.setOnClickListener {
            KeyValueStorage.setActiveAccount(Account(null, "", "", "", false))
            val intent = Intent(activity, TvLoginActivity::class.java)
            startActivity(intent)
        }
    }

    private fun refreshCacheInfo() {
        try {
            val client = SubsonicClient(KeyValueStorage.getActiveAccount())
            val bytes = client.getCoverCacheSizeBytes()
            cacheInfo.text = "Art cache: ${bytes / 1024} KB"
        } catch (_: Exception) {
            cacheInfo.text = "Art cache: unavailable"
        }
    }
}
