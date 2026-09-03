package app.sonicsound.visualizer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.abs

/** Shared paint helpers for WMP canvas modes. */
internal object WmpRenderUtil {
    fun bandAcross(spectrum: AudioSpectrumSource, i: Int, count: Int): Float {
        val n = spectrum.bandCount.coerceAtLeast(3)
        val t = if (count <= 1) 0f else i.toFloat() / (count - 1).toFloat()
        val idx = (1 + t * (n - 2)).toInt().coerceIn(1, n - 1)
        return spectrum.band(idx)
    }

    fun channelAt(spectrum: AudioSpectrumSource, t: Float): Float {
        val l = spectrum.left()
        val r = spectrum.right()
        return (l * (1f - t) + r * t).coerceIn(0f, 1f)
    }

    fun tempo(spectrum: AudioSpectrumSource): Float =
        (spectrum.bpm / 110f).coerceIn(0.55f, 1.75f)

    fun drawSoftOrb(
        canvas: Canvas,
        fillPaint: Paint,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
    ) {
        val r = radius.coerceAtLeast(1f)
        fillPaint.shader = null
        fillPaint.colorFilter = null
        val a = Color.alpha(color).coerceIn(0, 255)
        val cr = Color.red(color)
        val cg = Color.green(color)
        val cb = Color.blue(color)
        fillPaint.color = Color.argb((a * 0.20f).toInt().coerceIn(0, 55), cr, cg, cb)
        canvas.drawCircle(cx, cy, r, fillPaint)
        fillPaint.color = Color.argb((a * 0.12f).toInt().coerceIn(0, 35), cr, cg, cb)
        canvas.drawCircle(cx, cy, r * 0.55f, fillPaint)
        fillPaint.alpha = 255
    }

    fun hsv(h: Float, s: Float, v: Float): Int {
        val hh = ((h % 360f) + 360f) % 360f
        val c = v * s
        val x = c * (1 - abs((hh / 60f) % 2f - 1))
        val m = v - c
        val (r, g, b) = when {
            hh < 60f -> Triple(c, x, 0f)
            hh < 120f -> Triple(x, c, 0f)
            hh < 180f -> Triple(0f, c, x)
            hh < 240f -> Triple(0f, x, c)
            hh < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color.rgb(
            ((r + m) * 255).toInt().coerceIn(0, 255),
            ((g + m) * 255).toInt().coerceIn(0, 255),
            ((b + m) * 255).toInt().coerceIn(0, 255),
        )
    }
}
