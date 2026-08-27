package app.sonicsound

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import app.sonicsound.models.Account
import app.sonicsound.subsonic.SubsonicClient

class AccountFragment : Fragment {
    constructor() : super()

    constructor(@Suppress("UNUSED_PARAMETER") client: SubsonicClient) : super()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.fragment_account, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val account = KeyValueStorage.getActiveAccount()
        view.findViewById<TextView>(R.id.tv_account_user).text = account.username
        view.findViewById<TextView>(R.id.tv_account_server).text = account.url
        view.findViewById<TextView>(R.id.tv_account_type).text = account.type
        view.findViewById<TextView>(R.id.tv_plaintext_warning).visibility =
            if (account.usePlaintext) View.VISIBLE else View.INVISIBLE

        view.findViewById<Button>(R.id.btn_logout).setOnClickListener {
            KeyValueStorage.setActiveAccount(Account(null, "", "", "", false))
            val intent = Intent(activity, TvLoginActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            activity?.finish()
        }
    }
}
