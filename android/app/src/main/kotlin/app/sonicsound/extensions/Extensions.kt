package app.sonicsound.extensions

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.ImageView
import app.sonicsound.R
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

fun ImageView.loadUrl(url: String) {
    if (url.isBlank()) {
        Glide.with(context).load(R.drawable.ic_album_art_placeholder).into(this)
        return
    }
    try {
        Glide.with(context).load(url).into(this)
    } catch (_: Exception) {
        Glide.with(context).load(R.drawable.ic_album_art_placeholder).into(this)
    }
}

/**
 * Loads album art and optionally upscales low-resolution covers for TV.
 * High-res images are left as-is. [onReady] receives final bitmap dimensions.
 */
fun ImageView.loadAlbumArt(
    url: String,
    upscaleLowRes: Boolean = true,
    onReady: ((width: Int, height: Int) -> Unit)? = null,
) {
    if (url.isBlank()) {
        loadUrl("")
        return
    }
    try {
        Glide.with(context)
            .asBitmap()
            .load(url)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: Transition<in Bitmap>?,
                ) {
                    val bmp =
                        if (upscaleLowRes) AlbumArtUpscale.maybeUpscale(resource) else resource
                    setImageBitmap(bmp)
                    onReady?.invoke(bmp.width, bmp.height)
                }

                override fun onLoadCleared(placeholder: Drawable?) {}

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    loadUrl("")
                }
            })
    } catch (_: Exception) {
        loadUrl("")
    }
}
