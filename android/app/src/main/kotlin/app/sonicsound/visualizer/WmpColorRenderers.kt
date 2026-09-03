package app.sonicsound.visualizer

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Color-field WMP modes (tiles, rays, cubes, rings). */
internal object WmpColorRenderers {
    fun drawMusicalColors(
        canvas: Canvas,
        fillPaint: Paint,
        spectrum: AudioSpectrumSource,
        state: WmpRenderState,
        w: Int,
        h: Int,
    ) {
        val cols = 14
        val rows = 8
        val shapes = cols * rows
        val tScale = WmpRenderUtil.tempo(spectrum)
        val lfe = spectrum.lfe()
        val cellW = w / (cols + 0.5f)
        val cellH = h / (rows + 1.2f)
        for (i in 0 until shapes) {
            val mag = WmpRenderUtil.bandAcross(spectrum, i, shapes)
            val col = i % cols
            val row = i / cols
            val t = col.toFloat() / (cols - 1).coerceAtLeast(1)
            val ch = WmpRenderUtil.channelAt(spectrum, t)
            val level = (mag * (0.65f + ch * 0.5f) + if (row >= rows - 2) lfe * 0.3f else 0f)
                .coerceIn(0f, 1f)
            if (level < 0.04f) continue
            val cx = cellW * (0.75f + col)
            val bob = sin(state.simTime * (1.0f + level * 2.4f) * tScale + i) * cellH * 0.12f * (0.2f + level)
            val cy = cellH * (0.85f + row) + bob
            val hw = cellW * (0.22f + level * 0.28f)
            val hh = cellH * (0.18f + level * 0.32f)
            fillPaint.color = WmpRenderUtil.hsv(
                i * 11f + level * 90f + spectrum.mids() * 40f, 0.88f, 0.26f + level * 0.74f,
            )
            fillPaint.alpha = (90 + level * 165).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(cx - hw, cy - hh, cx + hw, cy + hh, 10f, 10f, fillPaint)
        }
        fillPaint.alpha = 255
    }

    fun drawBlazingColors(
        canvas: Canvas,
        fillPaint: Paint,
        linePaint: Paint,
        path: Path,
        spectrum: AudioSpectrumSource,
        state: WmpRenderState,
        w: Int,
        h: Int,
    ) {
        val rays = 40
        val cx = w / 2f + (spectrum.right() - spectrum.left()) * w * 0.03f
        val cy = h / 2f
        val hueShift = spectrum.mids() * 100f + state.simTime * (10f * WmpRenderUtil.tempo(spectrum))
        val spin = state.simTime * 0.12f * WmpRenderUtil.tempo(spectrum)
        val bass = spectrum.bass()
        val lfe = spectrum.lfe()
        val sur = spectrum.surround()
        val step = (PI * 2 / rays).toFloat()
        val wedge = step * 0.42f
        val maxR = min(w, h) * 0.62f
        val prevCap = linePaint.strokeCap
        linePaint.strokeCap = Paint.Cap.BUTT
        for (i in 0 until rays) {
            val mag = WmpRenderUtil.bandAcross(spectrum, i, rays)
            val a = i * step + spin
            val facing = ((cos(a) + 1f) * 0.5f)
            val ch = WmpRenderUtil.channelAt(spectrum, facing)
            val level = (mag * (0.7f + ch * 0.45f)).coerceIn(0f, 1f)
            if (level < 0.03f) continue
            val len = (0.18f + level * 0.55f + sur * 0.12f + bass * 0.08f + lfe * 0.06f).coerceIn(0.12f, 1f)
            val tipR = maxR * len
            val midA = a + wedge * 0.5f
            fillPaint.color = WmpRenderUtil.hsv(i * 9f + hueShift, 1f, 0.28f + level * 0.72f)
            fillPaint.alpha = (55 + level * 200).toInt().coerceIn(0, 255)
            path.reset()
            path.moveTo(cx, cy)
            path.lineTo(cx + cos(a) * tipR, cy + sin(a) * tipR)
            path.lineTo(cx + cos(a + wedge) * tipR * 0.94f, cy + sin(a + wedge) * tipR * 0.94f)
            path.close()
            canvas.drawPath(path, fillPaint)
            linePaint.color = WmpRenderUtil.hsv(i * 9f + hueShift, 0.7f, 0.55f + level * 0.45f)
            linePaint.alpha = (90 + level * 165).toInt().coerceIn(0, 255)
            linePaint.strokeWidth = (1.2f + level * 3.5f)
            canvas.drawLine(cx, cy, cx + cos(midA) * tipR, cy + sin(midA) * tipR, linePaint)
        }
        linePaint.strokeCap = prevCap
        linePaint.alpha = 255
        fillPaint.alpha = 255
    }

    fun drawColorCubes(
        canvas: Canvas,
        fillPaint: Paint,
        path: Path,
        spectrum: AudioSpectrumSource,
        state: WmpRenderState,
        w: Int,
        h: Int,
    ) {
        val cols = 8
        val rows = 5
        val cubes = cols * rows
        val tScale = WmpRenderUtil.tempo(spectrum)
        val lfe = spectrum.lfe()
        for (i in 0 until cubes) {
            val mag = WmpRenderUtil.bandAcross(spectrum, i, cubes)
            val col = i % cols
            val row = i / cols
            val t = col.toFloat() / (cols - 1).coerceAtLeast(1)
            val level = (mag * (0.65f + WmpRenderUtil.channelAt(spectrum, t) * 0.5f) +
                if (row >= rows - 2) lfe * 0.25f else 0f).coerceIn(0f, 1f)
            if (level < 0.035f) continue
            val cx = w * (0.08f + col * 0.115f)
            val cy = h * (0.14f + row * 0.16f)
            val size = 14f + level * 58f
            val offset = sin(state.simTime * (1.2f + level * 2.4f) * tScale + i) * 14f * (0.15f + level)
            val top = cy - size / 2 + offset
            val bottom = cy + size / 2 + offset
            val left = cx - size / 2
            val right = cx + size / 2
            val depth = size * 0.28f
            val hue = i * 12f + level * 70f
            path.reset()
            path.moveTo(left, top)
            path.lineTo(left + depth, top - depth)
            path.lineTo(right + depth, top - depth)
            path.lineTo(right, top)
            path.close()
            fillPaint.color = WmpRenderUtil.hsv(hue, 0.7f, 0.45f + level * 0.55f)
            fillPaint.alpha = 230
            canvas.drawPath(path, fillPaint)
            path.reset()
            path.moveTo(right, top)
            path.lineTo(right + depth, top - depth)
            path.lineTo(right + depth, bottom - depth)
            path.lineTo(right, bottom)
            path.close()
            fillPaint.color = WmpRenderUtil.hsv(hue, 0.85f, 0.18f + level * 0.45f)
            canvas.drawPath(path, fillPaint)
            fillPaint.color = WmpRenderUtil.hsv(hue, 0.8f, 0.28f + level * 0.72f)
            canvas.drawRect(left, top, right, bottom, fillPaint)
        }
        fillPaint.alpha = 255
    }

    fun drawPulsingColors(
        canvas: Canvas,
        linePaint: Paint,
        spectrum: AudioSpectrumSource,
        state: WmpRenderState,
        w: Int,
        h: Int,
    ) {
        val cx = w / 2f + (spectrum.right() - spectrum.left()) * w * 0.03f
        val cy = h / 2f
        val bass = spectrum.bass()
        val lfe = spectrum.lfe()
        val side = spectrum.side()
        val sur = spectrum.surround()
        val rings = 18
        val scale = min(w, h) / 900f
        for (i in 0 until rings) {
            val mag = WmpRenderUtil.bandAcross(spectrum, i, rings)
            val pulse = mag * 0.55f + bass * 0.25f + lfe * 0.2f
            val rx = (32f + i * 30f + pulse * 105f + sur * 20f) * scale * (1f + side * 0.45f)
            val ry = (32f + i * 30f + pulse * 105f) * scale
            linePaint.color = WmpRenderUtil.hsv(
                i * 20f + bass * 80f + state.simTime * (9f * WmpRenderUtil.tempo(spectrum)),
                0.9f, 0.35f + pulse * 0.65f,
            )
            linePaint.strokeWidth = 1.6f + pulse * 5.2f
            linePaint.alpha = 255
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, linePaint)
        }
    }
}
