package app.sonicsound.visualizer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Classic WMP bars / scope / battery modes. */
internal object WmpClassicRenderers {
    fun drawBars(
        canvas: Canvas,
        barPaint: Paint,
        peakPaint: Paint,
        state: WmpRenderState,
        spectrum: AudioSpectrumSource,
        w: Int,
        h: Int,
        cool: Boolean,
        warm: Boolean,
    ) {
        val bars = state.barHeights.size
        val gap = 2.5f
        val barW = (w - gap * (bars - 1)) / bars
        val lfe = spectrum.lfe()
        val sur = spectrum.surround()
        val floor = h * (0.025f + lfe * 0.02f)
        val usable = (h - floor) * (0.90f + sur * 0.04f)
        for (i in 0 until bars) {
            val mag = state.barHeights[i].coerceIn(0f, 1f)
            val peak = state.barPeaks[i].coerceIn(0f, 1f)
            val barH = floor + mag * usable
            val hueBase = when {
                warm -> 8f + abs(i - bars / 2) * 1.8f + spectrum.mids() * 12f
                cool -> 170f + abs(i - bars / 2) * 1.4f + sur * 18f
                else -> 100f + abs(i - bars / 2) * 2.2f
            }
            barPaint.color = WmpRenderUtil.hsv(hueBase, 0.78f, 0.22f + mag * 0.78f)
            val left = i * (barW + gap)
            canvas.drawRect(left, h - barH, left + barW, h.toFloat(), barPaint)
            val peakY = h - (floor + peak * usable)
            peakPaint.color = WmpRenderUtil.hsv(hueBase, 0.45f, 1f)
            canvas.drawRect(left, peakY - 3f, left + barW, peakY, peakPaint)
        }
    }

    fun drawScope(
        canvas: Canvas,
        linePaint: Paint,
        path: Path,
        spectrum: AudioSpectrumSource,
        w: Int,
        h: Int,
    ) {
        val midY = h / 2f
        val samples = spectrum.waveCount.coerceAtLeast(2)
        val steps = min(w, 512)
        val side = spectrum.side()
        val amp = 0.40f + spectrum.energy() * 0.08f
        if (side > 0.015f || abs(spectrum.left() - spectrum.right()) > 0.02f) {
            path.reset()
            for (i in 0 until steps) {
                val idx = (i.toFloat() / (steps - 1) * (samples - 1)).toInt()
                val x = i.toFloat() / (steps - 1) * w
                val m = spectrum.waveAt(idx)
                val y = midY - (m * (1f + spectrum.left() * 0.4f) - side * 0.4f) * h * amp
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            linePaint.color = Color.argb(55, 80, 200, 255)
            linePaint.strokeWidth = 2f
            canvas.drawPath(path, linePaint)
            path.reset()
            for (i in 0 until steps) {
                val idx = (i.toFloat() / (steps - 1) * (samples - 1)).toInt()
                val x = i.toFloat() / (steps - 1) * w
                val m = spectrum.waveAt(idx)
                val y = midY - (m * (1f + spectrum.right() * 0.4f) + side * 0.4f) * h * amp
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            linePaint.color = Color.argb(55, 255, 160, 80)
            canvas.drawPath(path, linePaint)
        }
        linePaint.color = Color.rgb(60, 235, 120)
        linePaint.strokeWidth = 2.5f
        path.reset()
        for (i in 0 until steps) {
            val idx = (i.toFloat() / (steps - 1) * (samples - 1)).toInt()
            val x = i.toFloat() / (steps - 1) * w
            val y = midY - spectrum.waveAt(idx) * h * (amp + 0.04f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
        linePaint.color = Color.argb(55, 60, 235, 120)
        linePaint.strokeWidth = 6f
        canvas.drawPath(path, linePaint)
        linePaint.strokeWidth = 2.5f
    }

    fun drawBattery(
        canvas: Canvas,
        linePaint: Paint,
        spectrum: AudioSpectrumSource,
        state: WmpRenderState,
        w: Int,
        h: Int,
    ) {
        val cx = w / 2f + (spectrum.right() - spectrum.left()) * w * 0.05f
        val cy = h / 2f
        val scale = min(w, h) / 900f
        val rings = 20
        val bass = spectrum.bass()
        val lfe = spectrum.lfe()
        val energy = spectrum.energy()
        val sur = spectrum.surround()
        val tScale = WmpRenderUtil.tempo(spectrum)
        for (r in 0 until rings) {
            val mag = WmpRenderUtil.bandAcross(spectrum, r, rings)
            val pulse = mag * 0.68f + bass * 0.12f + lfe * 0.12f + energy * 0.08f
            val radius = (50f + r * 26f + pulse * 100f + sur * 22f) * scale
            val hue = 250f + r * 5.5f + mag * 70f + state.simTime * (7f * tScale)
            linePaint.color = WmpRenderUtil.hsv(hue, 0.7f, 0.35f + pulse * 0.65f)
            linePaint.alpha = (50 + pulse * 185).toInt().coerceIn(0, 255)
            linePaint.strokeWidth = (1.3f + pulse * 4.8f) * scale
            canvas.drawCircle(cx, cy, radius, linePaint)
        }
        linePaint.alpha = 255
    }

    fun drawAlchemy(
        canvas: Canvas,
        fillPaint: Paint,
        linePaint: Paint,
        path: Path,
        spectrum: AudioSpectrumSource,
        state: WmpRenderState,
        w: Int,
        h: Int,
    ) {
        val energy = spectrum.energy()
        val bass = spectrum.bass()
        val lfe = spectrum.lfe()
        val mids = spectrum.mids()
        val side = spectrum.side()
        val cx = w / 2f + (spectrum.right() - spectrum.left()) * w * 0.04f
        val cy = h / 2f
        val layers = (3 + (spectrum.surround() * 2).toInt()).coerceIn(3, 5)
        val tScale = WmpRenderUtil.tempo(spectrum)
        for (layer in 0 until layers) {
            val sides = (6 + (mids * 6).toInt() + layer).coerceIn(5, 14)
            val baseR = min(w, h) * (0.11f + layer * 0.075f + energy * 0.2f + bass * 0.07f + lfe * 0.06f)
            val spin = state.simTime * (0.5f + energy * 1.5f) * tScale * (if (layer % 2 == 0) 1f else -0.85f)
            path.reset()
            for (i in 0..sides) {
                val mag = WmpRenderUtil.bandAcross(spectrum, i % sides, sides)
                val a = spin + i * (PI * 2 / sides).toFloat()
                val skew = 1f + side * 0.35f * cos(a)
                val rr = baseR * (0.58f + mag * 0.58f) * skew
                val x = cx + cos(a) * rr
                val y = cy + sin(a) * rr
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            val hue = 25f + energy * 200f + layer * 35f + mids * 30f
            fillPaint.color = WmpRenderUtil.hsv(hue, 0.6f, 0.18f + energy * 0.45f)
            fillPaint.alpha = (40 + energy * 90 - layer * 8).toInt().coerceIn(0, 255)
            canvas.drawPath(path, fillPaint)
            linePaint.color = WmpRenderUtil.hsv(hue, 0.85f, 0.5f + energy * 0.5f)
            linePaint.strokeWidth = 2f + energy * 3f + lfe * 2f
            linePaint.alpha = 220
            canvas.drawPath(path, linePaint)
        }
        fillPaint.alpha = 255
        linePaint.alpha = 255
    }
}
