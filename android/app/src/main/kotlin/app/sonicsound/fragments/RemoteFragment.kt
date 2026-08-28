package app.sonicsound.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import app.sonicsound.App
import app.sonicsound.Globals
import app.sonicsound.Helpers.Companion.encodeAsBitmap
import app.sonicsound.IBroadcastObserver
import app.sonicsound.R

class RemoteFragment : Fragment(), IBroadcastObserver {
    private var statusView: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.fragment_remote, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val image: ImageView = view.findViewById(R.id.iv_remote_qr)
        val ipText: TextView = view.findViewById(R.id.tv_remote_ip)
        statusView = view.findViewById(R.id.tv_remote_status)
        val ip = App.localIp ?: "127.0.0.1"
        image.setImageBitmap(encodeAsBitmap("${ip}j"))
        ipText.text = ip
    }

    override fun onResume() {
        super.onResume()
        Globals.RegisterObserver(this)
    }

    override fun onPause() {
        Globals.UnregisterObserver(this)
        super.onPause()
    }

    override fun update(action: String?, value: String?) {
        if (action == "WS") {
            activity?.runOnUiThread {
                statusView?.text = if (value == "true") {
                    getString(R.string.remote_phone_connected)
                } else {
                    getString(R.string.remote_waiting_phone)
                }
            }
        }
    }
}
