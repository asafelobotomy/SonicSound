package app.sonicsound.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import app.sonicsound.App
import app.sonicsound.Helpers.Companion.encodeAsBitmap
import app.sonicsound.R

class JukeboxFragment : Fragment {
    constructor() : super()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_jukebox, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val image: ImageView = view.findViewById(R.id.iv_jukebox_qr)
        val text: TextView = view.findViewById(R.id.tv_jukebox_ip)
        val ip = if (App.localIp == null) "127.0.0.1" else "${App.localIp}j"
        image.setImageBitmap(encodeAsBitmap(ip))
        text.text = ip.trimEnd('j')
    }
}
