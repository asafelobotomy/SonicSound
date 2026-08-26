package app.sonicsound.extensions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.roundToInt

/**
 * Upscales only low-resolution album art for large TV displays.
 * High-resolution covers are returned unchanged.
 */
object AlbumArtUpscale {
    /** Shortest side below this is treated as low-res. */
    private const val LOW_RES_MAX = 512
    /** Target shortest side after upscale (2× low-res, capped). */
    private const val TARGET_MIN = 1024
    private const val HIGH_RES_MIN = 720

    fun maybeUpscale(source: Bitmap): Bitmap {
        val shortSide = minOf(source.width, source.height)
        if (shortSide >= HIGH_RES_MIN) return source
        if (shortSide >= LOW_RES_MAX) return source
        if (shortSide <= 0) return source
        val scale = TARGET_MIN.toFloat() / shortSide.toFloat()
        if (scale <= 1.05f) return source
        val w = (source.width * scale).roundToInt().coerceAtLeast(1)
        val h = (source.height * scale).roundToInt().coerceAtLeast(1)
        // Soft bilinear upsample (filter=true). Prefer ARGB_8888 for TV.
        val cfg = source.config ?: Bitmap.Config.ARGB_8888
        val out = Bitmap.createBitmap(w, h, cfg)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val matrix = Matrix().apply { setScale(scale, scale) }
        canvas.drawBitmap(source, matrix, paint)
        return out
    }

    fun needsUpscale(width: Int, height: Int): Boolean {
        val shortSide = minOf(width, height)
        return shortSide in 1 until LOW_RES_MAX
    }
}
