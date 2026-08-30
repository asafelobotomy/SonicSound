package app.sonicsound.extensions

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.ImageView
import app.sonicsound.R
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

fun ImageView.loadUrl(url: String) {
    clearAlbumArtTarget()
    if (url.isBlank()) {
        Glide.with(context.applicationContext).load(R.drawable.ic_album_art_placeholder).into(this)
        return
    }
    try {
        Glide.with(context.applicationContext).load(url).into(this)
    } catch (_: Exception) {
        Glide.with(context.applicationContext).load(R.drawable.ic_album_art_placeholder).into(this)
    }
}

/** Cancels any in-flight [loadAlbumArt] request bound to this view. */
fun ImageView.clearAlbumArtTarget() {
    @Suppress("UNCHECKED_CAST")
    val prior = getTag(R.id.tag_album_art_target) as? CustomTarget<Bitmap>
    setTag(R.id.tag_album_art_url, null)
    setTag(R.id.tag_album_art_target, null)
    if (prior != null) {
        runCatching { Glide.with(context.applicationContext).clear(prior) }
    }
    runCatching { Glide.with(context.applicationContext).clear(this) }
}

/**
 * Loads album art and optionally upscales low-resolution covers for TV.
 * High-res images are left as-is (as an owned software copy). [onReady] receives final dimensions.
 */
fun ImageView.loadAlbumArt(
    url: String,
    upscaleLowRes: Boolean = true,
    onReady: ((width: Int, height: Int) -> Unit)? = null,
) {
    clearAlbumArtTarget()
    if (url.isBlank()) {
        loadUrl("")
        return
    }
    setTag(R.id.tag_album_art_url, url)
    try {
        val target = object : CustomTarget<Bitmap>() {
            override fun onResourceReady(
                resource: Bitmap,
                transition: Transition<in Bitmap>?,
            ) {
                if (getTag(R.id.tag_album_art_url) != url) return
                if (!isAttachedToWindow) return
                val bmp = try {
                    if (upscaleLowRes) {
                        AlbumArtUpscale.maybeUpscale(resource)
                    } else {
                        AlbumArtUpscale.toSoftware(resource)
                    }
                } catch (_: Exception) {
                    AlbumArtUpscale.toSoftware(resource)
                }
                setImageBitmap(bmp)
                onReady?.invoke(bmp.width, bmp.height)
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                // Do not touch the ImageView if a newer load owns it, or the view is gone.
                if (getTag(R.id.tag_album_art_url) != url) return
                if (!isAttachedToWindow) return
                // Avoid clearing a bitmap we own and still display — Glide only owned [resource].
                if (placeholder != null) setImageDrawable(placeholder)
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                if (getTag(R.id.tag_album_art_url) != url) return
                if (!isAttachedToWindow) return
                loadUrl("")
            }
        }
        setTag(R.id.tag_album_art_target, target)
        Glide.with(context.applicationContext)
            .asBitmap()
            .load(url)
            .into(target)
    } catch (_: Exception) {
        loadUrl("")
    }
}
