package app.sonicsound

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import app.sonicsound.discovery.SubsonicLanDiscovery
import app.sonicsound.models.Account
import app.sonicsound.subsonic.SubsonicClient
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TvLoginActivity : AppCompatActivity() {
    private lateinit var userInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var urlInput: EditText
    private lateinit var userLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var urlLayout: TextInputLayout
    private lateinit var loginButton: Button
    private lateinit var discoverButton: Button
    private lateinit var discoverResults: LinearLayout
    private lateinit var plaintext: SwitchCompat
    private var wsLoginObserver: WsLogin? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_login)
        supportActionBar?.hide()
        bindUi()
        wsLoginObserver = WsLogin().also { Globals.RegisterObserver(it) }
        val account = KeyValueStorage.getActiveAccount()
        if (account.username != null) {
            tryLogin(account)
        }
    }

    override fun onDestroy() {
        wsLoginObserver?.let { Globals.UnregisterObserver(it) }
        wsLoginObserver = null
        super.onDestroy()
    }

    private fun getFormData(): FormData {
        return FormData(
            userInput.text.toString(),
            passwordInput.text.toString(),
            urlInput.text.toString(),
            plaintext.isChecked
        )
    }

    class FormData(
        val username: String,
        val password: String,
        val url: String,
        val plaintext: Boolean
    ) {
        fun validate(): String {
            if (username.trim() == "") {
                return "The username is required"
            }
            if (password.trim() == "") {
                return "The password is required"
            }
            if (url.trim() == "") {
                return "The url is required"
            }
            return ""
        }
    }

    private fun tryLogin(account: Account) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SubsonicClient(account).login(
                        account.username!!,
                        account.password,
                        account.url,
                        account.usePlaintext
                    )
                }
                startActivity(Intent(this@TvLoginActivity, TvActivity::class.java))
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@TvLoginActivity,
                    e.message ?: "There was an unexpected error",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    inner class WsLogin : IBroadcastObserver {
        override fun update(action: String?, value: String?) {
            if (action == "WSLOGIN") {
                try {
                    val account = Gson().fromJson(value, Account::class.java)
                    tryLogin(account)
                } catch (_: Exception) {
                    Toast.makeText(
                        this@TvLoginActivity,
                        "The account received is malformed.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun bindUi() {
        plaintext = findViewById(R.id.switch_plaintext)
        userInput = findViewById(R.id.username_input)
        passwordInput = findViewById(R.id.password_input)
        urlInput = findViewById(R.id.url_input)
        userLayout = findViewById(R.id.username_input_layout)
        passwordLayout = findViewById(R.id.password_input_layout)
        urlLayout = findViewById(R.id.url_input_layout)
        discoverResults = findViewById(R.id.ll_discover_results)
        userInput.doOnTextChanged { text, _, _, _ ->
            if ((text?.toString() ?: "") == "") {
                userLayout.isErrorEnabled = true
                userLayout.error = "The username is required"
            } else {
                userLayout.isErrorEnabled = false
            }
        }
        passwordInput.doOnTextChanged { text, _, _, _ ->
            if ((text?.toString() ?: "") == "") {
                passwordLayout.isErrorEnabled = true
                passwordLayout.error = "The password is required"
            } else {
                passwordLayout.isErrorEnabled = false
            }
        }
        urlInput.doOnTextChanged { text, _, _, _ ->
            if ((text?.toString() ?: "") == "") {
                urlLayout.isErrorEnabled = true
                urlLayout.error = "The url is required"
            } else {
                urlLayout.isErrorEnabled = false
            }
        }
        loginButton = findViewById(R.id.btn_tv_login)
        loginButton.setOnClickListener {
            val formdata = getFormData()
            val errors = formdata.validate()
            if (errors == "") {
                tryLogin(Account(formdata))
            } else {
                Toast.makeText(this, errors, Toast.LENGTH_SHORT).show()
            }
        }
        discoverButton = findViewById(R.id.btn_discover_servers)
        discoverButton.setOnClickListener { runDiscovery() }
        val image: ImageView = findViewById(R.id.iv_qr_login)
        val text: TextView = findViewById(R.id.tv_qr_login)
        val layout: LinearLayout = findViewById(R.id.qr_login_container)
        val ip = App.localIp ?: "127.0.0.1"
        image.setImageBitmap(Helpers.encodeAsBitmap(ip))
        text.text = ip
        val qrButton: Button = findViewById(R.id.btn_tv_qr)
        qrButton.setOnClickListener {
            layout.visibility =
                if (layout.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
        }
    }

    private fun runDiscovery() {
        discoverButton.isEnabled = false
        discoverResults.removeAllViews()
        Toast.makeText(this, R.string.discovering_servers, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) {
                SubsonicLanDiscovery.discover(this@TvLoginActivity)
            }
            discoverButton.isEnabled = true
            if (found.isEmpty()) {
                Toast.makeText(
                    this@TvLoginActivity,
                    R.string.no_servers_found,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            for (base in found) {
                val button = Button(this@TvLoginActivity).apply {
                    text = base
                    setTextColor(0xFFFFFFFF.toInt())
                    setBackgroundResource(R.drawable.round_outline_selector)
                    isAllCaps = false
                    setOnClickListener {
                        urlInput.setText(base)
                        urlLayout.isErrorEnabled = false
                    }
                }
                discoverResults.addView(button)
            }
        }
    }
}
