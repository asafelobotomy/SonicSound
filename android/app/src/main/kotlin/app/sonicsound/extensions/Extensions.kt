package app.sonicsound.extensions

import android.widget.ImageView
import com.bumptech.glide.Glide
import app.sonicsound.R

fun ImageView.loadUrl(url: String) {
    if (url.isBlank()) {
        Glide.with(context).load(R.drawable.ic_album_art_placeholder).into(this)
        return
    }
    try {
        Glide.with(context).load(url).into(this)
    } catch (e: Exception) {
        Glide.with(context).load(R.drawable.ic_album_art_placeholder).into(this)
    }
}