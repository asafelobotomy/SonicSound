package app.sonicsound.extensions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import app.sonicsound.App
import kotlin.math.roundToInt

/**
 * Upscales only low-resolution album art for large displays.
 * High-resolution covers are returned unchanged (as a software copy when needed).
 */
object AlbumArtUpscale {
    /** Shortest side below this is treated as low-res. */
    private const val LOW_RES_MAX = 512
    /** TV / large display target after upscale. */
    private const val TARGET_MIN_TV = 1280
    /** Phone target when upscale is requested. */
    private const val TARGET_MIN_PHONE = 768
    private const val HIGH_RES_MIN_TV = 960
    private const val HIGH_RES_MIN_PHONE = 640

    /**
     * Returns a display-safe bitmap owned by the caller.
     * Never returns a HARDWARE bitmap; never recycles [source] (Glide may own it).
     */
    fun maybeUpscale(source: Bitmap, forTv: Boolean = App.isTv): Bitmap {
        val software = toSoftware(source)
        val shortSide = minOf(software.width, software.height)
        val highResMin = if (forTv) HIGH_RES_MIN_TV else HIGH_RES_MIN_PHONE
        if (shortSide >= highResMin || shortSide >= LOW_RES_MAX || shortSide <= 0) {
            return software
        }
        val targetMin = if (forTv) TARGET_MIN_TV else TARGET_MIN_PHONE
        val scale = targetMin.toFloat() / shortSide.toFloat()
        if (scale <= 1.05f) return software
        val w = (software.width * scale).roundToInt().coerceAtLeast(1)
        val h = (software.height * scale).roundToInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val matrix = Matrix().apply { setScale(scale, scale) }
        canvas.drawBitmap(software, matrix, paint)
        if (software !== source && !software.isRecycled) {
            // We made a software copy solely for upscale; recycle the intermediate.
            runCatching { software.recycle() }
        }
        return if (forTv && scale >= 1.75f) lightUnsharp(out) else out
    }

    fun needsUpscale(width: Int, height: Int, forTv: Boolean = App.isTv): Boolean {
        val shortSide = minOf(width, height)
        val highResMin = if (forTv) HIGH_RES_MIN_TV else HIGH_RES_MIN_PHONE
        if (shortSide >= highResMin) return false
        return shortSide in 1 until LOW_RES_MAX
    }

    /** Ensures a software ARGB_8888 bitmap owned by the caller (never Glide-pooled). */
    fun toSoftware(source: Bitmap): Bitmap {
        if (source.isRecycled) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        val copy = source.copy(Bitmap.Config.ARGB_8888, false)
        if (copy != null) return copy
        // Last resort: draw into a fresh bitmap rather than returning Glide's instance.
        val w = source.width.coerceAtLeast(1)
        val h = source.height.coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(source, 0f, 0f, null)
        return out
    }

    /** Fast unsharp via downscale/upscale blend — operates on an owned software bitmap. */
    private fun lightUnsharp(source: Bitmap, amount: Float = 0.35f): Bitmap {
        val w = source.width
        val h = source.height
        if (w * h > 1_500_000) return source
        val smallW = (w / 2).coerceAtLeast(1)
        val smallH = (h / 2).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(source, smallW, smallH, true)
        val blurred = Bitmap.createScaledBitmap(small, w, h, true)
        if (small !== source) small.recycle()
        val srcPx = IntArray(w * h)
        val blurPx = IntArray(w * h)
        source.getPixels(srcPx, 0, w, 0, 0, w, h)
        blurred.getPixels(blurPx, 0, w, 0, 0, w, h)
        blurred.recycle()
        for (i in srcPx.indices) {
            val s = srcPx[i]
            val b = blurPx[i]
            val sa = s ushr 24
            val sr = (s shr 16) and 0xFF
            val sg = (s shr 8) and 0xFF
            val sb = s and 0xFF
            val br = (b shr 16) and 0xFF
            val bg = (b shr 8) and 0xFF
            val bb = b and 0xFF
            val nr = (sr + amount * (sr - br)).roundToInt().coerceIn(0, 255)
            val ng = (sg + amount * (sg - bg)).roundToInt().coerceIn(0, 255)
            val nb = (sb + amount * (sb - bb)).roundToInt().coerceIn(0, 255)
            srcPx[i] = (sa shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(srcPx, 0, w, 0, 0, w, h)
        if (!source.isRecycled) source.recycle()
        return result
    }
}
