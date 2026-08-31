package app.sonicsound.visualizer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Canvas drawing for legacy Windows Media Player visualizations.
 *
 * Design notes (WMP originals):
 * - Bars / Ocean Mist / Fire Storm → mirrored spectrum analyzer + falling peaks
 * - Scope → green oscilloscope waveform (no debug crosshair)
 * - Battery → Ambience-family energy fields: concentric pulsing rings / soft core glow
 * - Alchemy → morphing geometric polygons with spectrum-warped vertices
 * - Ambience → soft color fields / haze (no oscilloscope overlay)
 * - Particle → drifting sparks across the spectrum
 * - Plenoptic → soft light orbs (light-field feel)
 * - Spikes → radial spectrum rays
 * - Musical / Blazing / Cubes / Pulsing Colors → color shapes driven by full band range
 * - StarTime / SnowTime → starfield / snowfall reacting to audio
 */
object WmpRenderers {
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val gradientColors = IntArray(2)
    private val gradientStops = floatArrayOf(0f, 1f)

    fun draw(
        mode: String,
        canvas: Canvas,
        spectrum: AudioSpectrumSource,
        state: WmpRenderState,
        w: Int,
        h: Int,
    ) {
        canvas.drawColor(Color.BLACK)
        when (mode) {
            "wmp_bars" -> drawBars(canvas, state, w, h, cool = false, warm = false)
            "wmp_ocean_mist" -> drawBars(canvas, state, w, h, cool = true, warm = false)
            "wmp_fire_storm" -> drawBars(canvas, state, w, h, cool = false, warm = true)
            "wmp_scope" -> drawScope(canvas, spectrum, w, h)
            "wmp_battery" -> drawBattery(canvas, spectrum, state, w, h)
            "wmp_alchemy" -> drawAlchemy(canvas, spectrum, state, w, h)
            "wmp_ambience" -> drawAmbience(canvas, state, spectrum, w, h)
            "wmp_particle" -> drawParticle(canvas, spectrum, w, h)
            "wmp_plenoptic" -> drawPlenoptic(canvas, state, w, h)
            "wmp_spikes" -> drawSpikes(canvas, spectrum, state, w, h)
            "wmp_musical_colors" -> drawMusicalColors(canvas, spectrum, state, w, h)
            "wmp_blazing_colors" -> drawBlazingColors(canvas, spectrum, state, w, h)
            "wmp_color_cubes" -> drawColorCubes(canvas, spectrum, state, w, h)
            "wmp_pulsing_colors" -> drawPulsingColors(canvas, spectrum, state, w, h)
            "wmp_startime" -> drawStarTime(canvas, state, w, h)
            "wmp_snowtime" -> drawSnowTime(canvas, state, w, h)
            else -> drawBars(canvas, state, w, h, cool = false, warm = false)
        }
    }

    /** Map element [i] of [count] evenly across the analyzable spectrum (skip DC). */
    private fun bandAcross(spectrum: AudioSpectrumSource, i: Int, count: Int): Float {
        val n = spectrum.bandCount.coerceAtLeast(3)
        val t = if (count <= 1) 0f else i.toFloat() / (count - 1).toFloat()
        val idx = (1 + t * (n - 2)).toInt().coerceIn(1, n - 1)
        return spectrum.band(idx)
    }

    /** Classic WMP mirrored bars: bass center, highs at edges, falling peak caps. */
    private fun drawBars(
        canvas: Canvas,
        state: WmpRenderState,
        w: Int,
        h: Int,
        cool: Boolean,
        warm: Boolean,
    ) {
        val bars = state.barHeights.size
        val gap = 2.5f
        val barW = (w - gap * (bars - 1)) / bars
        val floor = h * 0.03f
        val usable = (h - floor) * 0.92f
        for (i in 0 until bars) {
            val mag = state.barHeights[i].coerceIn(0f, 1f)
            val peak = state.barPeaks[i].coerceIn(0f, 1f)
            val barH = floor + mag * usable
            val hueBase = when {
                warm -> 8f + abs(i - bars / 2) * 1.8f
                cool -> 170f + abs(i - bars / 2) * 1.4f
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

    /** Oscilloscope — mid waveform; faint L/R ghost traces for stereo width. */
    private fun drawScope(canvas: Canvas, spectrum: AudioSpectrumSource, w: Int, h: Int) {
        val midY = h / 2f
        val samples = spectrum.waveCount.coerceAtLeast(2)
        val steps = min(w, 480)
        val side = spectrum.side()
        val leftBoost = (1f + spectrum.left() * 0.35f)
        val rightBoost = (1f + spectrum.right() * 0.35f)
        // Soft L (upper tint) / R (lower tint) ghosts from mid±side approximation.
        if (side > 0.02f) {
            path.reset()
            for (i in 0 until steps) {
                val idx = (i.toFloat() / (steps - 1) * (samples - 1)).toInt()
                val x = i.toFloat() / (steps - 1) * w
                val m = spectrum.waveAt(idx)
                val y = midY - (m * leftBoost - side * 0.35f) * h * 0.38f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            linePaint.color = Color.argb(50, 80, 200, 255)
            linePaint.strokeWidth = 2f
            canvas.drawPath(path, linePaint)
            path.reset()
            for (i in 0 until steps) {
                val idx = (i.toFloat() / (steps - 1) * (samples - 1)).toInt()
                val x = i.toFloat() / (steps - 1) * w
                val m = spectrum.waveAt(idx)
                val y = midY - (m * rightBoost + side * 0.35f) * h * 0.38f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            linePaint.color = Color.argb(50, 255, 160, 80)
            canvas.drawPath(path, linePaint)
        }
        linePaint.color = Color.rgb(60, 235, 120)
        linePaint.strokeWidth = 2.5f
        path.reset()
        for (i in 0 until steps) {
            val idx = (i.toFloat() / (steps - 1) * (samples - 1)).toInt()
            val x = i.toFloat() / (steps - 1) * w
            val y = midY - spectrum.waveAt(idx) * h * 0.44f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
        linePaint.color = Color.argb(55, 60, 235, 120)
        linePaint.strokeWidth = 6f
        canvas.drawPath(path, linePaint)
        linePaint.strokeWidth = 2.5f
    }

    /**
     * Battery (Ambience family): concentric energy rings across the spectrum.
     * Soft bass/LFE-reactive core bloom — no orbiting placeholder dot.
     */
    private fun drawBattery(canvas: Canvas, spectrum: AudioSpectrumSource, state: WmpRenderState, w: Int, h: Int) {
        val cx = w / 2f + (spectrum.right() - spectrum.left()) * w * 0.04f
        val cy = h / 2f
        val scale = min(w, h) / 900f
        val rings = 18
        val bass = spectrum.bass()
        val lfe = spectrum.lfe()
        val energy = spectrum.energy()
        val sur = spectrum.surround()
        val coreR = (90f + bass * 120f + lfe * 90f + energy * 50f) * scale
        gradientColors[0] = Color.argb(
            (35 + (bass + lfe) * 55).toInt().coerceIn(0, 255),
            90, 70, 160,
        )
        gradientColors[1] = Color.TRANSPARENT
        fillPaint.shader = RadialGradient(
            cx, cy, coreR.coerceAtLeast(1f),
            gradientColors, gradientStops, Shader.TileMode.CLAMP,
        )
        fillPaint.alpha = 255
        canvas.drawCircle(cx, cy, coreR, fillPaint)
        fillPaint.shader = null
        fillPaint.alpha = 255

        for (r in 0 until rings) {
            val mag = bandAcross(spectrum, r, rings)
            val pulse = mag * 0.7f + bass * 0.12f + lfe * 0.1f + energy * 0.08f
            val radius = (55f + r * 28f + pulse * 95f + sur * 18f) * scale
            val hue = 250f + r * 6f + mag * 70f + state.simTime * 8f
            linePaint.color = hsv(hue, 0.7f, 0.35f + pulse * 0.65f)
            linePaint.alpha = (55 + pulse * 180).toInt().coerceIn(0, 255)
            linePaint.strokeWidth = (1.4f + pulse * 4.5f) * scale
            canvas.drawCircle(cx, cy, radius, linePaint)
        }
        linePaint.alpha = 255
    }

    /** Alchemy: morphing spectrum-warped polygon (classic geometric WMP look). */
    private fun drawAlchemy(canvas: Canvas, spectrum: AudioSpectrumSource, state: WmpRenderState, w: Int, h: Int) {
        val energy = spectrum.energy()
        val bass = spectrum.bass()
        val mids = spectrum.mids()
        val cx = w / 2f
        val cy = h / 2f
        val layers = 3
        for (layer in 0 until layers) {
            val sides = (6 + (mids * 6).toInt() + layer).coerceIn(5, 14)
            val baseR = min(w, h) * (0.12f + layer * 0.08f + energy * 0.22f + bass * 0.08f)
            val spin = state.simTime * (0.55f + energy * 1.6f) * (if (layer % 2 == 0) 1f else -0.85f)
            path.reset()
            for (i in 0..sides) {
                val mag = bandAcross(spectrum, i % sides, sides)
                val a = spin + i * (PI * 2 / sides).toFloat()
                val rr = baseR * (0.62f + mag * 0.55f)
                val x = cx + cos(a) * rr
                val y = cy + sin(a) * rr
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            val hue = 25f + energy * 200f + layer * 35f
            fillPaint.color = hsv(hue, 0.6f, 0.18f + energy * 0.45f)
            fillPaint.alpha = (40 + energy * 90 - layer * 10).toInt().coerceIn(0, 255)
            canvas.drawPath(path, fillPaint)
            linePaint.color = hsv(hue, 0.85f, 0.5f + energy * 0.5f)
            linePaint.strokeWidth = 2f + energy * 3f
            linePaint.alpha = 220
            canvas.drawPath(path, linePaint)
        }
        fillPaint.alpha = 255
        linePaint.alpha = 255
    }

    /** Ambience: soft color haze — surround widens the field. */
    private fun drawAmbience(canvas: Canvas, state: WmpRenderState, spectrum: AudioSpectrumSource, w: Int, h: Int) {
        val sur = spectrum.surround()
        val side = spectrum.side()
        for (p in state.particleList) {
            val mag = p.level
            val r = (22f + mag * 110f + p.size * 18f + sur * 40f)
            val ox = (spectrum.right() - spectrum.left()) * 0.04f + side * (p.x - 0.5f) * 0.08f
            gradientColors[0] = hsv(185f + p.hue * 0.2f + mag * 50f + sur * 40f, 0.45f, 0.2f + mag * 0.7f)
            gradientColors[1] = Color.TRANSPARENT
            fillPaint.shader = RadialGradient(
                (p.x + ox) * w, p.y * h, r.coerceAtLeast(1f),
                gradientColors, gradientStops, Shader.TileMode.CLAMP,
            )
            fillPaint.alpha = (30 + mag * 160).toInt().coerceIn(0, 255)
            canvas.drawCircle((p.x + ox) * w, p.y * h, r, fillPaint)
        }
        fillPaint.shader = null
        fillPaint.alpha = 255
    }

    /**
     * Particle (classic "Dotplane"): perspective grid of cyan/magenta/blue/purple dots
     * that bounce with their assigned spectrum band.
     */
    private fun drawParticle(canvas: Canvas, spectrum: AudioSpectrumSource, w: Int, h: Int) {
        val cols = 28
        val rows = 16
        val cx = w / 2f
        val horizon = h * 0.18f
        val floorY = h * 0.95f
        val palette = floatArrayOf(185f, 280f, 220f, 310f) // cyan, magenta, blue, purple
        for (row in 0 until rows) {
            val depth = (row + 1f) / rows
            val persp = 0.18f + depth * 0.82f
            val yBase = horizon + (floorY - horizon) * depth
            val rowW = w * (0.22f + depth * 0.78f)
            val left = cx - rowW / 2f
            val spacing = rowW / (cols - 1).coerceAtLeast(1)
            val r = (1.4f + depth * 4.2f)
            for (col in 0 until cols) {
                val mag = bandAcross(spectrum, col, cols)
                val bounce = mag * h * 0.09f * (0.35f + depth)
                val x = left + col * spacing
                val y = yBase - bounce
                val hue = palette[(col + row) % palette.size]
                fillPaint.color = hsv(hue, 0.85f, 0.35f + mag * 0.65f)
                fillPaint.alpha = (90 + mag * 165).toInt().coerceIn(0, 255)
                canvas.drawCircle(x, y, r * (0.7f + mag * 0.9f) * persp, fillPaint)
            }
        }
        fillPaint.alpha = 255
    }

    /** Plenoptic: soft paint-like smoky circles across the full spectrum. */
    private fun drawPlenoptic(canvas: Canvas, state: WmpRenderState, w: Int, h: Int) {
        for (b in state.blobList) {
            val mag = b.level
            val cx = b.x * w
            val cy = b.y * h
            val radius = (55f + mag * 190f).coerceAtLeast(1f)
            gradientColors[0] = hsv(b.band * 5.5f + mag * 80f, 0.55f, 0.35f + mag * 0.6f)
            gradientColors[1] = Color.TRANSPARENT
            fillPaint.shader = RadialGradient(
                cx, cy, radius, gradientColors, gradientStops, Shader.TileMode.CLAMP,
            )
            fillPaint.alpha = 255
            canvas.drawCircle(cx, cy, radius, fillPaint)
        }
        fillPaint.shader = null
    }

    /**
     * Spikes (Bars & Waves family): nested circles that stretch into ellipses with the music.
     * Matches classic WMP cyan/teal concentric stretching rings — not radial spokes.
     */
    private fun drawSpikes(canvas: Canvas, spectrum: AudioSpectrumSource, state: WmpRenderState, w: Int, h: Int) {
        val cx = w / 2f + (spectrum.right() - spectrum.left()) * w * 0.03f
        val cy = h / 2f
        val rings = 14
        val energy = spectrum.energy()
        val bass = spectrum.bass()
        val side = spectrum.side()
        val scale = min(w, h).toFloat()
        gradientColors[0] = Color.argb((30 + energy * 50).toInt(), 20, 80, 90)
        gradientColors[1] = Color.TRANSPARENT
        fillPaint.shader = RadialGradient(
            cx, cy, scale * 0.55f, gradientColors, gradientStops, Shader.TileMode.CLAMP,
        )
        fillPaint.alpha = 255
        canvas.drawCircle(cx, cy, scale * 0.55f, fillPaint)
        fillPaint.shader = null

        for (i in 0 until rings) {
            val mag = bandAcross(spectrum, i, rings)
            val pulse = mag * 0.7f + bass * 0.2f + energy * 0.1f
            // Stereo width stretches X; mono-ish content stays rounder.
            val widthBoost = 1f + side * 0.55f + spectrum.surround() * 0.25f
            val rx = scale * (0.06f + i * 0.032f + pulse * 0.08f) * widthBoost
            val ry = scale * (0.05f + i * 0.028f + pulse * 0.22f)
            val hue = 165f + i * 3f + mag * 25f + state.simTime * 4f
            linePaint.color = hsv(hue, 0.75f, 0.4f + pulse * 0.6f)
            linePaint.strokeWidth = 2f + pulse * 3.5f
            linePaint.alpha = (100 + pulse * 155).toInt().coerceIn(0, 255)
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, linePaint)
            linePaint.color = hsv(hue, 0.4f, 1f)
            linePaint.alpha = (40 + pulse * 90).toInt().coerceIn(0, 255)
            linePaint.strokeWidth = 5f + pulse * 4f
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, linePaint)
        }
        linePaint.alpha = 255
    }

    private fun drawMusicalColors(canvas: Canvas, spectrum: AudioSpectrumSource, state: WmpRenderState, w: Int, h: Int) {
        val shapes = 16
        for (i in 0 until shapes) {
            val mag = bandAcross(spectrum, i, shapes)
            val cx = w * (0.08f + (i % 8) * 0.12f)
            val cy = h * (0.28f + (i / 8) * 0.38f + sin(state.simTime * (1.1f + mag * 2.2f) + i) * 0.12f * (0.2f + mag))
            fillPaint.color = hsv(i * 22f + mag * 80f, 0.88f, 0.28f + mag * 0.72f)
            fillPaint.alpha = 255
            canvas.drawRoundRect(
                cx - 22f - mag * 42f,
                cy - 14f - mag * 28f,
                cx + 22f + mag * 42f,
                cy + 14f + mag * 28f,
                12f, 12f, fillPaint,
            )
        }
    }

    private fun drawBlazingColors(canvas: Canvas, spectrum: AudioSpectrumSource, state: WmpRenderState, w: Int, h: Int) {
        val rays = 48
        val cx = w / 2f
        val cy = h / 2f
        val hueShift = spectrum.mids() * 100f + state.simTime * 12f
        val spin = state.simTime * 0.15f
        for (i in 0 until rays) {
            val mag = bandAcross(spectrum, i, rays)
            val a = i * (PI * 2 / rays).toFloat() + spin
            fillPaint.color = hsv(i * 7.5f + hueShift, 1f, 0.3f + mag * 0.7f)
            fillPaint.alpha = (60 + mag * 190).toInt().coerceIn(0, 255)
            path.reset()
            path.moveTo(cx, cy)
            path.lineTo(
                cx + cos(a) * w * 0.62f * (0.18f + mag),
                cy + sin(a) * h * 0.62f * (0.18f + mag),
            )
            path.lineTo(cx + cos(a + 0.055f) * w * 0.1f, cy + sin(a + 0.055f) * h * 0.1f)
            path.close()
            canvas.drawPath(path, fillPaint)
        }
        fillPaint.alpha = 255
    }

    private fun drawColorCubes(canvas: Canvas, spectrum: AudioSpectrumSource, state: WmpRenderState, w: Int, h: Int) {
        val cubes = 24
        val cols = 6
        for (i in 0 until cubes) {
            val mag = bandAcross(spectrum, i, cubes)
            val col = i % cols
            val row = i / cols
            val cx = w * (0.1f + col * 0.16f)
            val cy = h * (0.14f + row * 0.2f)
            val size = 18f + mag * 70f
            val offset = sin(state.simTime * (1.3f + mag * 2.2f) + i) * 18f * (0.15f + mag)
            fillPaint.color = hsv(i * 15f + mag * 60f, 0.8f, 0.3f + mag * 0.7f)
            fillPaint.alpha = 255
            canvas.drawRect(
                cx - size / 2, cy - size / 2 + offset,
                cx + size / 2, cy + size / 2 + offset,
                fillPaint,
            )
            linePaint.color = Color.argb((70 + mag * 140).toInt(), 255, 255, 255)
            linePaint.strokeWidth = 1.5f
            canvas.drawRect(
                cx - size / 2, cy - size / 2 + offset,
                cx + size / 2, cy + size / 2 + offset,
                linePaint,
            )
        }
    }

    private fun drawPulsingColors(canvas: Canvas, spectrum: AudioSpectrumSource, state: WmpRenderState, w: Int, h: Int) {
        val cx = w / 2f
        val cy = h / 2f
        val bass = spectrum.bass()
        val rings = 16
        for (i in 0 until rings) {
            val mag = bandAcross(spectrum, i, rings)
            val pulse = mag * 0.65f + bass * 0.35f
            val radius = (35f + i * 32f + pulse * 110f) * min(w, h) / 900f
            linePaint.color = hsv(i * 22f + bass * 80f + state.simTime * 10f, 0.9f, 0.35f + pulse * 0.65f)
            linePaint.strokeWidth = 1.8f + pulse * 5f
            linePaint.alpha = 255
            canvas.drawCircle(cx, cy, radius, linePaint)
        }
    }

    private fun drawStarTime(canvas: Canvas, state: WmpRenderState, w: Int, h: Int) {
        for ((i, s) in state.starList.withIndex()) {
            val r = (1.1f + s.z * 2.4f + s.twinkle * 4f)
            val bright = (75 + s.twinkle * 180).toInt().coerceIn(0, 255)
            fillPaint.color = Color.argb(bright, 255, 255, 255)
            canvas.drawCircle(s.x * w, s.y * h, r, fillPaint)
            if (i % 12 == 0) {
                fillPaint.color = hsv(200f + s.hue * 0.2f + s.twinkle * 60f, 0.55f, 0.4f + s.twinkle * 0.55f)
                fillPaint.alpha = (50 + s.twinkle * 150).toInt().coerceIn(0, 255)
                canvas.drawCircle(s.x * w, s.y * h, r * (1.8f + s.twinkle * 2.2f), fillPaint)
            }
        }
        fillPaint.alpha = 255
    }

    private fun drawSnowTime(canvas: Canvas, state: WmpRenderState, w: Int, h: Int) {
        for (f in state.flakeList) {
            val alpha = (110 + f.level * 145).toInt().coerceIn(0, 255)
            fillPaint.color = Color.argb(alpha, 230, 240, 255)
            canvas.drawCircle(f.x * w, f.y * h, 1.5f + f.size + f.level * 3f, fillPaint)
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
