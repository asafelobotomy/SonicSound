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

/**
 * Canvas drawing for legacy Windows Media Player visualizations.
 *
 * Each mode is calibrated against the live pipeline:
 * - Full-band [bandAcross] mapping (skip DC)
 * - Stereo L/R + side width
 * - Surround / LFE when present
 * - Tempo ([AudioSpectrumSource.bpm]) for motion rates
 * - Smooth delayed spectrum from [AudioSpectrumSource]
 */
object WmpRenderers {
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = false }
    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = false }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isDither = false
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = false }
    private val path = Path()

    fun draw(
        mode: String,
        canvas: Canvas,
        spectrum: AudioSpectrumSource,
        state: WmpRenderState,
        w: Int,
        h: Int,
    ) {
        // Opaque clear — cheaper than layered alpha clears on GL.
        canvas.drawColor(Color.BLACK)
        when (mode) {
            "wmp_bars" -> drawBars(canvas, state, spectrum, w, h, cool = false, warm = false)
            "wmp_ocean_mist" -> drawBars(canvas, state, spectrum, w, h, cool = true, warm = false)
            "wmp_fire_storm" -> drawBars(canvas, state, spectrum, w, h, cool = false, warm = true)
            "wmp_scope" -> drawScope(canvas, spectrum, w, h)
            "wmp_battery" -> drawBattery(canvas, spectrum, state, w, h)
            "wmp_alchemy" -> drawAlchemy(canvas, spectrum, state, w, h)
            "wmp_ambience" -> drawAmbience(canvas, state, spectrum, w, h)
            "wmp_particle" -> drawParticle(canvas, spectrum, w, h)
            "wmp_plenoptic" -> drawPlenoptic(canvas, state, spectrum, w, h)
            "wmp_spikes" -> drawSpikes(canvas, spectrum, state, w, h)
            "wmp_musical_colors" -> drawMusicalColors(canvas, spectrum, state, w, h)
            "wmp_blazing_colors" -> drawBlazingColors(canvas, spectrum, state, w, h)
            "wmp_color_cubes" -> drawColorCubes(canvas, spectrum, state, w, h)
            "wmp_pulsing_colors" -> drawPulsingColors(canvas, spectrum, state, w, h)
            "wmp_startime" -> drawStarTime(canvas, state, spectrum, w, h)
            "wmp_snowtime" -> drawSnowTime(canvas, state, spectrum, w, h)
            else -> {
                // Unknown mode: stay black — do not fall back to bars (reads as a stub).
            }
        }
    }

    /** Map element [i] of [count] evenly across the analyzable spectrum (skip DC). */
    private fun bandAcross(spectrum: AudioSpectrumSource, i: Int, count: Int): Float {
        val n = spectrum.bandCount.coerceAtLeast(3)
        val t = if (count <= 1) 0f else i.toFloat() / (count - 1).toFloat()
        val idx = (1 + t * (n - 2)).toInt().coerceIn(1, n - 1)
        return spectrum.band(idx)
    }

    /** 0 = full left, 1 = full right → channel energy blend. */
    private fun channelAt(spectrum: AudioSpectrumSource, t: Float): Float {
        val l = spectrum.left()
        val r = spectrum.right()
        return (l * (1f - t) + r * t).coerceIn(0f, 1f)
    }

    private fun tempo(spectrum: AudioSpectrumSource): Float =
        (spectrum.bpm / 110f).coerceIn(0.55f, 1.75f)

    /**
     * Soft particle/orb wash for Ambience & Plenoptic — two very translucent fills,
     * never a hard opaque center disc (old SoftGlow cores read as placeholders).
     */
    private fun drawSoftOrb(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
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

    /** Classic WMP mirrored bars + palette variants; LFE lifts the floor on hits. */
    private fun drawBars(
        canvas: Canvas,
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
            barPaint.color = hsv(hueBase, 0.78f, 0.22f + mag * 0.78f)
            val left = i * (barW + gap)
            canvas.drawRect(left, h - barH, left + barW, h.toFloat(), barPaint)
            val peakY = h - (floor + peak * usable)
            peakPaint.color = hsv(hueBase, 0.45f, 1f)
            canvas.drawRect(left, peakY - 3f, left + barW, peakY, peakPaint)
        }
    }

    /** Oscilloscope — mid waveform; L/R ghosts for stereo width. */
    private fun drawScope(canvas: Canvas, spectrum: AudioSpectrumSource, w: Int, h: Int) {
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

    /** Battery — concentric rings; LFE/bass core; stereo center; surround radius. */
    private fun drawBattery(canvas: Canvas, spectrum: AudioSpectrumSource, state: WmpRenderState, w: Int, h: Int) {
        val cx = w / 2f + (spectrum.right() - spectrum.left()) * w * 0.05f
        val cy = h / 2f
        val scale = min(w, h) / 900f
        val rings = 20
        val bass = spectrum.bass()
        val lfe = spectrum.lfe()
        val energy = spectrum.energy()
        val sur = spectrum.surround()
        // Rings only — no center SoftGlow disc.

        val tScale = tempo(spectrum)
        for (r in 0 until rings) {
            val mag = bandAcross(spectrum, r, rings)
            val pulse = mag * 0.68f + bass * 0.12f + lfe * 0.12f + energy * 0.08f
            val radius = (50f + r * 26f + pulse * 100f + sur * 22f) * scale
            val hue = 250f + r * 5.5f + mag * 70f + state.simTime * (7f * tScale)
            linePaint.color = hsv(hue, 0.7f, 0.35f + pulse * 0.65f)
            linePaint.alpha = (50 + pulse * 185).toInt().coerceIn(0, 255)
            linePaint.strokeWidth = (1.3f + pulse * 4.8f) * scale
            canvas.drawCircle(cx, cy, radius, linePaint)
        }
        linePaint.alpha = 255
    }

    /** Alchemy — layered spectrum polygons; stereo offset; LFE size; tempo spin. */
    private fun drawAlchemy(canvas: Canvas, spectrum: AudioSpectrumSource, state: WmpRenderState, w: Int, h: Int) {
        val energy = spectrum.energy()
        val bass = spectrum.bass()
        val lfe = spectrum.lfe()
        val mids = spectrum.mids()
        val side = spectrum.side()
        val cx = w / 2f + (spectrum.right() - spectrum.left()) * w * 0.04f
        val cy = h / 2f
        val layers = (3 + (spectrum.surround() * 2).toInt()).coerceIn(3, 5)
        val tScale = tempo(spectrum)
        for (layer in 0 until layers) {
            val sides = (6 + (mids * 6).toInt() + layer).coerceIn(5, 14)
            val baseR = min(w, h) * (0.11f + layer * 0.075f + energy * 0.2f + bass * 0.07f + lfe * 0.06f)
            val spin = state.simTime * (0.5f + energy * 1.5f) * tScale * (if (layer % 2 == 0) 1f else -0.85f)
            path.reset()
            for (i in 0..sides) {
                val mag = bandAcross(spectrum, i % sides, sides)
                val a = spin + i * (PI * 2 / sides).toFloat()
                val skew = 1f + side * 0.35f * cos(a)
                val rr = baseR * (0.58f + mag * 0.58f) * skew
                val x = cx + cos(a) * rr
                val y = cy + sin(a) * rr
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            val hue = 25f + energy * 200f + layer * 35f + mids * 30f
            fillPaint.color = hsv(hue, 0.6f, 0.18f + energy * 0.45f)
            fillPaint.alpha = (40 + energy * 90 - layer * 8).toInt().coerceIn(0, 255)
            canvas.drawPath(path, fillPaint)
            linePaint.color = hsv(hue, 0.85f, 0.5f + energy * 0.5f)
            linePaint.strokeWidth = 2f + energy * 3f + lfe * 2f
            linePaint.alpha = 220
            canvas.drawPath(path, linePaint)
        }
        fillPaint.alpha = 255
        linePaint.alpha = 255
    }

    /** Ambience — soft haze; surround widens; stereo shifts field. */
    private fun drawAmbience(canvas: Canvas, state: WmpRenderState, spectrum: AudioSpectrumSource, w: Int, h: Int) {
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
            val tint = hsv(185f + p.hue * 0.2f + mag * 50f + sur * 40f, 0.45f, 0.2f + mag * 0.7f)
            val a = (28 + mag * 165).toInt().coerceIn(0, 255)
            drawSoftOrb(canvas, cx, cy, r, Color.argb(a, Color.red(tint), Color.green(tint), Color.blue(tint)))
        }
    }

    /** Particle / Dotplane — perspective grid; columns follow L→R; LFE lifts near rows. */
    private fun drawParticle(canvas: Canvas, spectrum: AudioSpectrumSource, w: Int, h: Int) {
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
                val mag = (bandAcross(spectrum, col, cols) * (0.7f + channelAt(spectrum, t) * 0.55f))
                    .coerceIn(0f, 1f)
                val bounce = (mag + nearBoost) * h * 0.1f * (0.3f + depth)
                val x = left + col * spacing
                val y = yBase - bounce
                val hue = palette[(col + row) % palette.size]
                fillPaint.color = hsv(hue, 0.85f, 0.32f + mag * 0.68f)
                fillPaint.alpha = (85 + mag * 170).toInt().coerceIn(0, 255)
                canvas.drawCircle(x, y, r * (0.65f + mag * 0.95f) * persp, fillPaint)
            }
        }
        fillPaint.alpha = 255
    }

    /** Plenoptic — smoky paint orbs; stereo drift; surround/LFE size. */
    private fun drawPlenoptic(canvas: Canvas, state: WmpRenderState, spectrum: AudioSpectrumSource, w: Int, h: Int) {
        val balance = spectrum.right() - spectrum.left()
        val sur = spectrum.surround()
        val lfe = spectrum.lfe()
        val energy = spectrum.energy()
        for (b in state.blobList) {
            val mag = b.level
            val cx = (b.x + balance * 0.04f) * w
            val cy = b.y * h
            val radius = (50f + mag * 175f + sur * 55f + lfe * 40f + energy * 25f).coerceAtLeast(1f)
            // Cap alpha so hsv()'s opaque colors can't become solid placeholder discs.
            val tint = hsv(b.band * 5.5f + mag * 80f + spectrum.mids() * 40f, 0.55f, 0.32f + mag * 0.62f)
            val a = (40 + mag * 90).toInt().coerceIn(0, 130)
            drawSoftOrb(
                canvas, cx, cy, radius,
                Color.argb(a, Color.red(tint), Color.green(tint), Color.blue(tint)),
            )
        }
    }

    /** Spikes — nested stretching ellipses; stereo width + LFE elongation. */
    private fun drawSpikes(canvas: Canvas, spectrum: AudioSpectrumSource, state: WmpRenderState, w: Int, h: Int) {
        val cx = w / 2f + (spectrum.right() - spectrum.left()) * w * 0.035f
        val cy = h / 2f
        val rings = 16
        val energy = spectrum.energy()
        val bass = spectrum.bass()
        val lfe = spectrum.lfe()
        val side = spectrum.side()
        val scale = min(w, h).toFloat()
        // Nested ovals only — no SoftGlow center disc.

        for (i in 0 until rings) {
            val mag = bandAcross(spectrum, i, rings)
            val pulse = mag * 0.65f + bass * 0.18f + lfe * 0.12f + energy * 0.08f
            val widthBoost = 1f + side * 0.6f + spectrum.surround() * 0.28f
            val rx = scale * (0.055f + i * 0.03f + pulse * 0.08f) * widthBoost
            val ry = scale * (0.048f + i * 0.027f + pulse * 0.24f + lfe * 0.04f)
            val hue = 165f + i * 2.8f + mag * 28f + state.simTime * (3.5f * tempo(spectrum))
            linePaint.color = hsv(hue, 0.75f, 0.4f + pulse * 0.6f)
            linePaint.strokeWidth = 2f + pulse * 3.8f
            linePaint.alpha = (95 + pulse * 160).toInt().coerceIn(0, 255)
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, linePaint)
            linePaint.color = hsv(hue, 0.4f, 1f)
            linePaint.alpha = (38 + pulse * 95).toInt().coerceIn(0, 255)
            linePaint.strokeWidth = 5f + pulse * 4f
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, linePaint)
        }
        linePaint.alpha = 255
    }

    /** Musical Colors — dense animated tile field; L/R columns; tempo bob; LFE bottom weight. */
    private fun drawMusicalColors(canvas: Canvas, spectrum: AudioSpectrumSource, state: WmpRenderState, w: Int, h: Int) {
        val cols = 14
        val rows = 8
        val shapes = cols * rows
        val tScale = tempo(spectrum)
        val lfe = spectrum.lfe()
        val cellW = w / (cols + 0.5f)
        val cellH = h / (rows + 1.2f)
        for (i in 0 until shapes) {
            val mag = bandAcross(spectrum, i, shapes)
            val col = i % cols
            val row = i / cols
            val t = col.toFloat() / (cols - 1).coerceAtLeast(1)
            val ch = channelAt(spectrum, t)
            val level = (mag * (0.65f + ch * 0.5f) + if (row >= rows - 2) lfe * 0.3f else 0f)
                .coerceIn(0f, 1f)
            if (level < 0.04f) continue
            val cx = cellW * (0.75f + col)
            val bob = sin(state.simTime * (1.0f + level * 2.4f) * tScale + i) * cellH * 0.12f * (0.2f + level)
            val cy = cellH * (0.85f + row) + bob
            val hw = cellW * (0.22f + level * 0.28f)
            val hh = cellH * (0.18f + level * 0.32f)
            fillPaint.color = hsv(i * 11f + level * 90f + spectrum.mids() * 40f, 0.88f, 0.26f + level * 0.74f)
            fillPaint.alpha = (90 + level * 165).toInt().coerceIn(0, 255)
            // Rounded tiles only — filled circles read as placeholder dots.
            canvas.drawRoundRect(
                cx - hw, cy - hh, cx + hw, cy + hh,
                10f, 10f, fillPaint,
            )
        }
        fillPaint.alpha = 255
    }

    /** Blazing Colors — radial streaks from a point; no hub disc / SoftGlow. */
    private fun drawBlazingColors(canvas: Canvas, spectrum: AudioSpectrumSource, state: WmpRenderState, w: Int, h: Int) {
        val rays = 40
        val cx = w / 2f + (spectrum.right() - spectrum.left()) * w * 0.03f
        val cy = h / 2f
        val hueShift = spectrum.mids() * 100f + state.simTime * (10f * tempo(spectrum))
        val spin = state.simTime * 0.12f * tempo(spectrum)
        val bass = spectrum.bass()
        val lfe = spectrum.lfe()
        val sur = spectrum.surround()
        val step = (PI * 2 / rays).toFloat()
        // Narrow wedges + true center apex — a clear hub radius left a circular placeholder edge.
        val wedge = step * 0.42f
        val maxR = min(w, h) * 0.62f

        val prevCap = linePaint.strokeCap
        linePaint.strokeCap = Paint.Cap.BUTT
        for (i in 0 until rays) {
            val mag = bandAcross(spectrum, i, rays)
            val a = i * step + spin
            val facing = ((cos(a) + 1f) * 0.5f) // 0 leftish, 1 rightish
            val ch = channelAt(spectrum, facing)
            val level = (mag * (0.7f + ch * 0.45f)).coerceIn(0f, 1f)
            if (level < 0.03f) continue
            val len = (0.18f + level * 0.55f + sur * 0.12f + bass * 0.08f + lfe * 0.06f).coerceIn(0.12f, 1f)
            val tipR = maxR * len
            val midA = a + wedge * 0.5f
            fillPaint.color = hsv(i * 9f + hueShift, 1f, 0.28f + level * 0.72f)
            fillPaint.alpha = (55 + level * 200).toInt().coerceIn(0, 255)
            path.reset()
            path.moveTo(cx, cy)
            path.lineTo(cx + cos(a) * tipR, cy + sin(a) * tipR)
            path.lineTo(cx + cos(a + wedge) * tipR * 0.94f, cy + sin(a + wedge) * tipR * 0.94f)
            path.close()
            canvas.drawPath(path, fillPaint)
            // Bright spine so quiet bands read as streaks, not stubby center blobs.
            linePaint.color = hsv(i * 9f + hueShift, 0.7f, 0.55f + level * 0.45f)
            linePaint.alpha = (90 + level * 165).toInt().coerceIn(0, 255)
            linePaint.strokeWidth = (1.2f + level * 3.5f)
            canvas.drawLine(cx, cy, cx + cos(midA) * tipR, cy + sin(midA) * tipR, linePaint)
        }
        linePaint.strokeCap = prevCap
        linePaint.alpha = 255
        fillPaint.alpha = 255
    }

    /** Color Cubes — isometric-ish blocks; column stereo; tempo bounce; full bands. */
    private fun drawColorCubes(canvas: Canvas, spectrum: AudioSpectrumSource, state: WmpRenderState, w: Int, h: Int) {
        val cols = 8
        val rows = 5
        val cubes = cols * rows
        val tScale = tempo(spectrum)
        val lfe = spectrum.lfe()
        for (i in 0 until cubes) {
            val mag = bandAcross(spectrum, i, cubes)
            val col = i % cols
            val row = i / cols
            val t = col.toFloat() / (cols - 1).coerceAtLeast(1)
            val level = (mag * (0.65f + channelAt(spectrum, t) * 0.5f) + if (row >= rows - 2) lfe * 0.25f else 0f)
                .coerceIn(0f, 1f)
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
            // Top face
            path.reset()
            path.moveTo(left, top)
            path.lineTo(left + depth, top - depth)
            path.lineTo(right + depth, top - depth)
            path.lineTo(right, top)
            path.close()
            fillPaint.color = hsv(hue, 0.7f, 0.45f + level * 0.55f)
            fillPaint.alpha = 230
            canvas.drawPath(path, fillPaint)
            // Side face
            path.reset()
            path.moveTo(right, top)
            path.lineTo(right + depth, top - depth)
            path.lineTo(right + depth, bottom - depth)
            path.lineTo(right, bottom)
            path.close()
            fillPaint.color = hsv(hue, 0.85f, 0.18f + level * 0.45f)
            canvas.drawPath(path, fillPaint)
            // Front face
            fillPaint.color = hsv(hue, 0.8f, 0.28f + level * 0.72f)
            canvas.drawRect(left, top, right, bottom, fillPaint)
        }
        fillPaint.alpha = 255
    }

    /** Pulsing Colors — rings; LFE/bass pulse; stereo oval; surround outer glow. */
    private fun drawPulsingColors(canvas: Canvas, spectrum: AudioSpectrumSource, state: WmpRenderState, w: Int, h: Int) {
        val cx = w / 2f + (spectrum.right() - spectrum.left()) * w * 0.03f
        val cy = h / 2f
        val bass = spectrum.bass()
        val lfe = spectrum.lfe()
        val side = spectrum.side()
        val sur = spectrum.surround()
        val rings = 18
        val scale = min(w, h) / 900f
        for (i in 0 until rings) {
            val mag = bandAcross(spectrum, i, rings)
            val pulse = mag * 0.55f + bass * 0.25f + lfe * 0.2f
            val rx = (32f + i * 30f + pulse * 105f + sur * 20f) * scale * (1f + side * 0.45f)
            val ry = (32f + i * 30f + pulse * 105f) * scale
            linePaint.color = hsv(i * 20f + bass * 80f + state.simTime * (9f * tempo(spectrum)), 0.9f, 0.35f + pulse * 0.65f)
            linePaint.strokeWidth = 1.6f + pulse * 5.2f
            linePaint.alpha = 255
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, linePaint)
        }
    }

    /** StarTime — band twinkle; stereo lateral drift; surround colored blooms. */
    private fun drawStarTime(canvas: Canvas, state: WmpRenderState, spectrum: AudioSpectrumSource, w: Int, h: Int) {
        val balance = spectrum.right() - spectrum.left()
        val sur = spectrum.surround()
        val energy = spectrum.energy()
        for ((i, s) in state.starList.withIndex()) {
            val drift = balance * 0.04f * s.z
            val r = (1.1f + s.z * 2.4f + s.twinkle * 4.2f + energy * 1.2f)
            val bright = (70 + s.twinkle * 185).toInt().coerceIn(0, 255)
            fillPaint.color = Color.argb(bright, 255, 255, 255)
            canvas.drawCircle((s.x + drift) * w, s.y * h, r, fillPaint)
            // Sparse blooms only — full-star glow was fill-bound on Tegra.
            if (i % 14 == 0 && (s.twinkle > 0.35f || sur > 0.2f)) {
                fillPaint.color = hsv(200f + s.hue * 0.2f + s.twinkle * 60f + sur * 40f, 0.55f, 0.4f + s.twinkle * 0.55f)
                fillPaint.alpha = (45 + s.twinkle * 155 + sur * 40).toInt().coerceIn(0, 255)
                canvas.drawCircle((s.x + drift) * w, s.y * h, r * (1.8f + s.twinkle * 2.2f + sur), fillPaint)
            }
        }
        fillPaint.alpha = 255
    }

    /** SnowTime — flakes; stereo wind; LFE denser flakes; band size. */
    private fun drawSnowTime(canvas: Canvas, state: WmpRenderState, spectrum: AudioSpectrumSource, w: Int, h: Int) {
        val wind = (spectrum.right() - spectrum.left()) * 0.06f + spectrum.side() * 0.04f
        val lfe = spectrum.lfe()
        for (f in state.flakeList) {
            val alpha = (105 + f.level * 150).toInt().coerceIn(0, 255)
            fillPaint.color = Color.argb(alpha, 230, 240, 255)
            val size = 1.4f + f.size + f.level * 3.2f + lfe * 2f
            canvas.drawCircle((f.x + wind) * w, f.y * h, size, fillPaint)
        }
    }

    private fun hsv(h: Float, s: Float, v: Float): Int {
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
