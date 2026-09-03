package app.sonicsound.visualizer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.min

/** Soft particle / ambient WMP modes. */
internal object WmpParticleRenderers {
    fun drawAmbience(
        canvas: Canvas,
        fillPaint: Paint,
        state: WmpRenderState,
        spectrum: AudioSpectrumSource,
        w: Int,
        h: Int,
    ) {
        val sur = spectrum.surround()
        val side = spectrum.side()
        val lfe = spectrum.lfe()
        val balance = spectrum.right() - spectrum.left()
        for (p in state.particleList) {
            val mag = p.level
            if (mag < 0.03f && p.size < 0.65f) continue
            val r = (20f + mag * 105f + p.size * 18f + sur * 48f + lfe * 30f)
            val ox = balance * 0.05f + side * (p.x - 0.5f) * 0.1f
            val cx = (p.x + ox) * w
            val cy = p.y * h
            val tint = WmpRenderUtil.hsv(185f + p.hue * 0.2f + mag * 50f + sur * 40f, 0.45f, 0.2f + mag * 0.7f)
            val a = (28 + mag * 165).toInt().coerceIn(0, 255)
            WmpRenderUtil.drawSoftOrb(
                canvas, fillPaint, cx, cy, r,
                Color.argb(a, Color.red(tint), Color.green(tint), Color.blue(tint)),
            )
        }
    }

    fun drawParticle(canvas: Canvas, fillPaint: Paint, spectrum: AudioSpectrumSource, w: Int, h: Int) {
        val cols = 24
        val rows = 14
        val cx = w / 2f + (spectrum.right() - spectrum.left()) * w * 0.03f
        val horizon = h * 0.16f
        val floorY = h * 0.96f
        val palette = floatArrayOf(185f, 280f, 220f, 310f)
        val lfe = spectrum.lfe()
        val bass = spectrum.bass()
        val sur = spectrum.surround()
        for (row in 0 until rows) {
            val depth = (row + 1f) / rows
            val persp = 0.16f + depth * 0.84f
            val yBase = horizon + (floorY - horizon) * depth
            val rowW = w * (0.20f + depth * 0.80f) * (1f + sur * 0.08f)
            val left = cx - rowW / 2f
            val spacing = rowW / (cols - 1).coerceAtLeast(1)
            val r = (1.5f + depth * 4.8f)
            val nearBoost = if (depth > 0.7f) lfe * 0.35f + bass * 0.15f else 0f
            for (col in 0 until cols) {
                val t = col.toFloat() / (cols - 1).coerceAtLeast(1)
                val mag = (WmpRenderUtil.bandAcross(spectrum, col, cols) *
                    (0.7f + WmpRenderUtil.channelAt(spectrum, t) * 0.55f)).coerceIn(0f, 1f)
                val bounce = (mag + nearBoost) * h * 0.1f * (0.3f + depth)
                val x = left + col * spacing
                val y = yBase - bounce
                val hue = palette[(col + row) % palette.size]
                fillPaint.color = WmpRenderUtil.hsv(hue, 0.85f, 0.32f + mag * 0.68f)
                fillPaint.alpha = (85 + mag * 170).toInt().coerceIn(0, 255)
                canvas.drawCircle(x, y, r * (0.65f + mag * 0.95f) * persp, fillPaint)
            }
        }
        fillPaint.alpha = 255
    }

    fun drawPlenoptic(
        canvas: Canvas,
        fillPaint: Paint,
        state: WmpRenderState,
        spectrum: AudioSpectrumSource,
        w: Int,
        h: Int,
    ) {
        val balance = spectrum.right() - spectrum.left()
        val sur = spectrum.surround()
        val lfe = spectrum.lfe()
        val energy = spectrum.energy()
        for (b in state.blobList) {
            val mag = b.level
            val cx = (b.x + balance * 0.04f) * w
            val cy = b.y * h
            val radius = (50f + mag * 175f + sur * 55f + lfe * 40f + energy * 25f).coerceAtLeast(1f)
            val tint = WmpRenderUtil.hsv(
                b.band * 5.5f + mag * 80f + spectrum.mids() * 40f, 0.55f, 0.32f + mag * 0.62f,
            )
            val a = (40 + mag * 90).toInt().coerceIn(0, 130)
            WmpRenderUtil.drawSoftOrb(
                canvas, fillPaint, cx, cy, radius,
                Color.argb(a, Color.red(tint), Color.green(tint), Color.blue(tint)),
            )
        }
    }

    fun drawSpikes(
        canvas: Canvas,
        linePaint: Paint,
        spectrum: AudioSpectrumSource,
        state: WmpRenderState,
        w: Int,
        h: Int,
    ) {
        val cx = w / 2f + (spectrum.right() - spectrum.left()) * w * 0.035f
        val cy = h / 2f
        val rings = 16
        val energy = spectrum.energy()
        val bass = spectrum.bass()
        val lfe = spectrum.lfe()
        val side = spectrum.side()
        val scale = min(w, h).toFloat()
        for (i in 0 until rings) {
            val mag = WmpRenderUtil.bandAcross(spectrum, i, rings)
            val pulse = mag * 0.65f + bass * 0.18f + lfe * 0.12f + energy * 0.08f
            val widthBoost = 1f + side * 0.6f + spectrum.surround() * 0.28f
            val rx = scale * (0.055f + i * 0.03f + pulse * 0.08f) * widthBoost
            val ry = scale * (0.048f + i * 0.027f + pulse * 0.24f + lfe * 0.04f)
            val hue = 165f + i * 2.8f + mag * 28f + state.simTime * (3.5f * WmpRenderUtil.tempo(spectrum))
            linePaint.color = WmpRenderUtil.hsv(hue, 0.75f, 0.4f + pulse * 0.6f)
            linePaint.strokeWidth = 2f + pulse * 3.8f
            linePaint.alpha = (95 + pulse * 160).toInt().coerceIn(0, 255)
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, linePaint)
            linePaint.color = WmpRenderUtil.hsv(hue, 0.4f, 1f)
            linePaint.alpha = (38 + pulse * 95).toInt().coerceIn(0, 255)
            linePaint.strokeWidth = 5f + pulse * 4f
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, linePaint)
        }
        linePaint.alpha = 255
    }

    fun drawStarTime(
        canvas: Canvas,
        fillPaint: Paint,
        state: WmpRenderState,
        spectrum: AudioSpectrumSource,
        w: Int,
        h: Int,
    ) {
        val balance = spectrum.right() - spectrum.left()
        val sur = spectrum.surround()
        val energy = spectrum.energy()
        for ((i, s) in state.starList.withIndex()) {
            val drift = balance * 0.04f * s.z
            val r = (1.1f + s.z * 2.4f + s.twinkle * 4.2f + energy * 1.2f)
            val bright = (70 + s.twinkle * 185).toInt().coerceIn(0, 255)
            fillPaint.color = Color.argb(bright, 255, 255, 255)
            canvas.drawCircle((s.x + drift) * w, s.y * h, r, fillPaint)
            if (i % 14 == 0 && (s.twinkle > 0.35f || sur > 0.2f)) {
                fillPaint.color = WmpRenderUtil.hsv(
                    200f + s.hue * 0.2f + s.twinkle * 60f + sur * 40f, 0.55f, 0.4f + s.twinkle * 0.55f,
                )
                fillPaint.alpha = (45 + s.twinkle * 155 + sur * 40).toInt().coerceIn(0, 255)
                canvas.drawCircle((s.x + drift) * w, s.y * h, r * (1.8f + s.twinkle * 2.2f + sur), fillPaint)
            }
        }
        fillPaint.alpha = 255
    }

    fun drawSnowTime(
        canvas: Canvas,
        fillPaint: Paint,
        state: WmpRenderState,
        spectrum: AudioSpectrumSource,
        w: Int,
        h: Int,
    ) {
        val wind = (spectrum.right() - spectrum.left()) * 0.06f + spectrum.side() * 0.04f
        val lfe = spectrum.lfe()
        for (f in state.flakeList) {
            val alpha = (105 + f.level * 150).toInt().coerceIn(0, 255)
            fillPaint.color = Color.argb(alpha, 230, 240, 255)
            val size = 1.4f + f.size + f.level * 3.2f + lfe * 2f
            canvas.drawCircle((f.x + wind) * w, f.y * h, size, fillPaint)
        }
    }
}
