package app.sonicsound

import android.app.Activity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.sonicsound.remote.QuickConnect

/** TV login overlay showing the Quick Connect code and a QR for the browser page. */
class QuickConnectPanel(activity: Activity) {
    private val container: LinearLayout = activity.findViewById(R.id.qr_login_container)
    private val codeText: TextView = activity.findViewById(R.id.tv_qc_code)
    private val qrImage: ImageView = activity.findViewById(R.id.iv_qr_login)
    private val urlText: TextView = activity.findViewById(R.id.tv_qr_login)

    val isShowing: Boolean get() = container.visibility == View.VISIBLE

    fun toggle() = if (isShowing) hide() else show()

    fun show() {
        val code = QuickConnect.currentOrStart()
        MessageServer.enablePairing(QuickConnect.SESSION_MS)
        val ip = App.localIp ?: "127.0.0.1"
        val pageUrl = "http://$ip:${QuickConnect.HTTP_PORT}/"
        codeText.text = "${code.substring(0, 3)} ${code.substring(3)}"
        // Code rides in the fragment so it never appears in an HTTP request line.
        qrImage.setImageBitmap(Helpers.encodeAsBitmap("$pageUrl#code=$code"))
        urlText.text = pageUrl
        container.visibility = View.VISIBLE
    }

    fun hide() {
        container.visibility = View.GONE
    }
}
