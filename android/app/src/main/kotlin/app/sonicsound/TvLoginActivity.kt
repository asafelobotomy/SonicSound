package app.sonicsound

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import app.sonicsound.discovery.SubsonicLanDiscovery
import app.sonicsound.models.Account
import app.sonicsound.remote.QuickConnect
import app.sonicsound.remote.QuickConnectHttpServer
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
    private var loginUiReady = false
    private var quickConnectPanel: QuickConnectPanel? = null
    private var quickConnectPage: QuickConnectHttpServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        // Accept phone QR / websocket login even while splash auto-login runs.
        ensureWsLoginObserver()
        MessageServer.enablePairing()
        val account = KeyValueStorage.getActiveAccount()
        if (account.username != null) {
            // Keep SonicSound splash visible while restoring the session.
            setContentView(R.layout.activity_splash)
            window.setBackgroundDrawable(
                ColorDrawable(ContextCompat.getColor(this, R.color.sonicsound_background))
            )
            tryLogin(account)
        } else {
            showLoginUi()
        }
    }

    override fun onDestroy() {
        MessageServer.disablePairing()
        QuickConnect.stop()
        quickConnectPage?.stop()
        quickConnectPage = null
        wsLoginObserver?.let { Globals.UnregisterObserver(it) }
        wsLoginObserver = null
        super.onDestroy()
    }

    private fun ensureWsLoginObserver() {
        if (wsLoginObserver == null) {
            wsLoginObserver = WsLogin().also { Globals.RegisterObserver(it) }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val panel = quickConnectPanel
        if (panel != null && panel.isShowing) {
            panel.hide()
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun showLoginUi() {
        setContentView(R.layout.activity_tv_login)
        // Drop splash launch-theme background so it doesn't show behind the form.
        window.setBackgroundDrawable(
            ColorDrawable(ContextCompat.getColor(this, R.color.sonicsound_background))
        )
        bindUi()
        ensureWsLoginObserver()
        MessageServer.enablePairing()
        quickConnectPage = QuickConnectHttpServer(assets).also { it.start() }
        loginUiReady = true
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

    private fun tryLogin(account: Account, fromQuickConnect: Boolean = false) {
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
                if (fromQuickConnect) {
                    QuickConnect.stop()
                    Globals.NotifyObservers(QuickConnectExchange.ACTION_RESULT_OK, "")
                }
                startActivity(Intent(this@TvLoginActivity, TvActivity::class.java))
                MessageServer.disablePairing()
                finish()
            } catch (e: Exception) {
                if (fromQuickConnect) {
                    Globals.NotifyObservers(
                        QuickConnectExchange.ACTION_RESULT_ERROR,
                        e.message ?: "The server rejected the login.",
                    )
                }
                // Leave splash if auto-login or QR-during-splash failed.
                if (!loginUiReady) {
                    showLoginUi()
                }
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
            if (action == QuickConnectExchange.ACTION_LOGIN) {
                try {
                    val account = Gson().fromJson(value, Account::class.java)
                    runOnUiThread {
                        Toast.makeText(
                            this@TvLoginActivity,
                            R.string.quick_connect_signing_in,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    tryLogin(account, fromQuickConnect = true)
                } catch (_: Exception) {
                    Globals.NotifyObservers(
                        QuickConnectExchange.ACTION_RESULT_ERROR,
                        "The account received is malformed.",
                    )
                    runOnUiThread {
                        if (!loginUiReady) showLoginUi()
                        Toast.makeText(
                            this@TvLoginActivity,
                            "The account received is malformed.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
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
        val panel = QuickConnectPanel(this).also { quickConnectPanel = it }
        val qrButton: Button = findViewById(R.id.btn_tv_qr)
        qrButton.setOnClickListener { panel.toggle() }
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
