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

/** Canvas drawing routines inspired by legacy Windows Media Player visualizations. */
object WmpRenderers {
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val gradientColors = IntArray(2)
    private val gradientStops = floatArrayOf(0f, 1f)

    fun draw(
        mode: String,
        canvas: Canvas,
        spectrum: AudioSpectrumSource,
        tick: Int,
        w: Int,
        h: Int,
    ) {
        canvas.drawColor(Color.BLACK)
        when (mode) {
            "wmp_bars" -> drawBars(canvas, spectrum, w, h, cool = false, warm = false)
            "wmp_ocean_mist" -> drawBars(canvas, spectrum, w, h, cool = true, warm = false)
            "wmp_fire_storm" -> drawBars(canvas, spectrum, w, h, cool = false, warm = true)
            "wmp_scope" -> drawScope(canvas, spectrum, w, h)
            "wmp_battery" -> drawBattery(canvas, spectrum, tick, w, h)
            "wmp_alchemy" -> drawAlchemy(canvas, spectrum, tick, w, h)
            "wmp_ambience" -> drawAmbience(canvas, spectrum, tick, w, h)
            "wmp_particle" -> drawParticle(canvas, spectrum, tick, w, h)
            "wmp_plenoptic" -> drawPlenoptic(canvas, spectrum, tick, w, h)
            "wmp_spikes" -> drawSpikes(canvas, spectrum, tick, w, h)
            "wmp_musical_colors" -> drawMusicalColors(canvas, spectrum, tick, w, h)
            "wmp_blazing_colors" -> drawBlazingColors(canvas, spectrum, tick, w, h)
            "wmp_color_cubes" -> drawColorCubes(canvas, spectrum, tick, w, h)
            "wmp_pulsing_colors" -> drawPulsingColors(canvas, spectrum, tick, w, h)
            "wmp_startime" -> drawStarTime(canvas, spectrum, tick, w, h)
            "wmp_snowtime" -> drawSnowTime(canvas, spectrum, tick, w, h)
            else -> drawBars(canvas, spectrum, w, h, cool = false, warm = false)
        }
    }

    private fun drawBars(
        canvas: Canvas,
        spectrum: AudioSpectrumSource,
        w: Int,
        h: Int,
        cool: Boolean,
        warm: Boolean,
    ) {
        val bars = 48
        val gap = 3f
        val barW = (w - gap * (bars - 1)) / bars
        val n = spectrum.bandCount.coerceAtLeast(1)
        for (i in 0 until bars) {
            // Log-ish map across available bands (skip DC).
            val band = 1 + (i * (n - 2) / bars).coerceIn(0, n - 1)
            val mag = spectrum.band(band)
            val barH = mag * h * 0.85f
            val hueBase = when {
                warm -> 20f + i * 1.4f
                cool -> 180f + i * 1.2f
                else -> 120f + i * 2.2f
            }
            barPaint.color = hsv(hueBase, 0.85f, 0.35f + mag * 0.65f)
            val left = i * (barW + gap)
            canvas.drawRect(left, h - barH, left + barW, h.toFloat(), barPaint)
        }
    }

    private fun drawScope(canvas: Canvas, spectrum: AudioSpectrumSource, w: Int, h: Int) {
        linePaint.color = Color.rgb(80, 220, 120)
        linePaint.strokeWidth = 2.5f
        path.reset()
        val mid = h / 2f
        val samples = spectrum.waveCount.coerceAtLeast(1)
        // Cap path complexity — enough for a smooth scope, cheap on large TVs.
        val steps = min(w, 256)
        for (i in 0 until steps) {
            val idx = (i.toFloat() / steps * samples).toInt()
            val x = i.toFloat() / steps * w
            val y = mid - spectrum.waveAt(idx) * h * 0.38f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
        linePaint.color = Color.argb(90, 80, 220, 120)
        canvas.drawLine(0f, mid, w.toFloat(), mid, linePaint)
    }

    private fun drawBattery(canvas: Canvas, spectrum: AudioSpectrumSource, tick: Int, w: Int, h: Int) {
        val cx = w / 2f
        val cy = h / 2f
        val bass = spectrum.bass()
        val energy = spectrum.energy()
        val rings = 6
        for (r in 0 until rings) {
            val band = spectrum.band(r + 1)
            val pulse = band * 0.7f + bass * 0.3f
            val radius = (80 + r * 55 + pulse * 110) * min(w, h) / 900f
            gradientColors[0] = hsv(280f + r * 18f + energy * 40f, 0.7f, 0.55f + pulse * 0.45f)
            gradientColors[1] = Color.TRANSPARENT
            fillPaint.shader = RadialGradient(
                cx, cy, radius.coerceAtLeast(1f),
                gradientColors,
                gradientStops,
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, radius, fillPaint)
        }
        fillPaint.shader = null
        // Subtle rotation from mid energy — still driven by audio, not a free-running oscillator.
        linePaint.color = hsv(300f, 0.4f, 0.4f + energy * 0.4f)
        linePaint.strokeWidth = 2f
        val spin = tick * 0.01f * (0.2f + energy)
        canvas.drawCircle(cx + cos(spin) * 20f * bass, cy + sin(spin) * 20f * bass, 8f + bass * 12f, linePaint)
    }

    private fun drawAlchemy(canvas: Canvas, spectrum: AudioSpectrumSource, tick: Int, w: Int, h: Int) {
        val energy = spectrum.energy()
        val mids = spectrum.mids()
        val bass = spectrum.bass()
        val cx = w / 2f
        val cy = h / 2f
        val sides = 5 + (mids * 5).toInt()
        val radius = min(w, h) * (0.16f + energy * 0.28f + bass * 0.08f)
        val n = spectrum.bandCount.coerceAtLeast(1)
        path.reset()
        for (i in 0..sides) {
            val a = tick * 0.02f * (0.3f + energy) + i * (PI * 2 / sides).toFloat()
            val rr = radius * (0.7f + spectrum.band(i % n) * 0.45f)
            val x = cx + cos(a) * rr
            val y = cy + sin(a) * rr
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        linePaint.color = hsv(40f + energy * 200f, 0.75f, 0.55f + energy * 0.45f)
        linePaint.strokeWidth = 2f + energy * 3f
        canvas.drawPath(path, linePaint)
        fillPaint.color = hsv(40f + energy * 200f + 60f, 0.55f, 0.25f + energy * 0.45f)
        fillPaint.alpha = (60 + energy * 100).toInt().coerceIn(0, 255)
        canvas.drawPath(path, fillPaint)
        fillPaint.alpha = 255
    }

    private fun drawAmbience(canvas: Canvas, spectrum: AudioSpectrumSource, tick: Int, w: Int, h: Int) {
        val ripples = 14
        val n = spectrum.bandCount.coerceAtLeast(1)
        for (i in 0 until ripples) {
            val mag = spectrum.band(1 + i % (n - 1).coerceAtLeast(1))
            // Drift speed scales with that band — quiet bands barely move.
            val drift = (tick * (0.4f + mag * 2.2f)).toInt()
            val cx = w * ((i * 73 + drift) % 1000) / 1000f
            val cy = h * ((i * 41 + drift * 2) % 1000) / 1000f
            fillPaint.color = hsv(195f + i * 8f + mag * 30f, 0.45f, 0.2f + mag * 0.65f)
            fillPaint.alpha = (40 + mag * 180).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, 16f + mag * 90f, fillPaint)
        }
        fillPaint.alpha = 255
        drawScope(canvas, spectrum, w, h)
    }

    private fun drawParticle(canvas: Canvas, spectrum: AudioSpectrumSource, tick: Int, w: Int, h: Int) {
        val grid = 12
        val cellW = w / grid.toFloat()
        val cellH = h / grid.toFloat()
        val n = spectrum.bandCount.coerceAtLeast(1)
        for (y in 0 until grid) {
            for (x in 0 until grid) {
                val idx = 1 + (x + y * 3) % (n - 1).coerceAtLeast(1)
                val mag = spectrum.band(idx)
                val px = x * cellW + cellW / 2
                val py = y * cellH + cellH / 2 + sin(tick * 0.04f * mag + x * 0.4f) * mag * 22f
                fillPaint.color = hsv(45f + mag * 120f, 0.8f, 0.25f + mag * 0.75f)
                canvas.drawCircle(px, py, 1.5f + mag * 9f, fillPaint)
            }
        }
    }

    private fun drawPlenoptic(canvas: Canvas, spectrum: AudioSpectrumSource, tick: Int, w: Int, h: Int) {
        val blobs = 10
        val n = spectrum.bandCount.coerceAtLeast(1)
        for (i in 0 until blobs) {
            val mag = spectrum.band(1 + (i * 2) % (n - 1).coerceAtLeast(1))
            val drive = 0.15f + mag
            val cx = w * (0.1f + (i * 0.09f + sin(tick * 0.015f * drive + i) * 0.06f * mag))
            val cy = h * (0.15f + (i * 0.07f + cos(tick * 0.014f * drive + i) * 0.09f * mag))
            val radius = (40f + mag * 160f).coerceAtLeast(1f)
            gradientColors[0] = hsv(i * 36f + mag * 80f, 0.6f, 0.45f + mag * 0.5f)
            gradientColors[1] = Color.TRANSPARENT
            fillPaint.shader = RadialGradient(
                cx, cy, radius,
                gradientColors,
                gradientStops,
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, radius, fillPaint)
        }
        fillPaint.shader = null
    }

    private fun drawSpikes(canvas: Canvas, spectrum: AudioSpectrumSource, tick: Int, w: Int, h: Int) {
        val cx = w / 2f
        val cy = h / 2f
        val spikes = 36
        val n = spectrum.bandCount.coerceAtLeast(1)
        val spin = tick * 0.008f * (0.2f + spectrum.energy())
        for (i in 0 until spikes) {
            val mag = spectrum.band(1 + i % (n - 1).coerceAtLeast(1))
            val a = i * (PI * 2 / spikes).toFloat() + spin
            val inner = 36f + spectrum.bass() * 16f
            val outer = inner + 24f + mag * 200f
            val x1 = cx + cos(a) * inner
            val y1 = cy + sin(a) * inner
            val x2 = cx + cos(a) * outer
            val y2 = cy + sin(a) * outer
            linePaint.color = hsv(55f + i * 4f + mag * 40f, 0.85f, 0.4f + mag * 0.6f)
            linePaint.strokeWidth = 3f + mag * 3f
            canvas.drawLine(x1, y1, x2, y2, linePaint)
            fillPaint.color = linePaint.color
            canvas.drawCircle(x2, y2, 2f + mag * 7f, fillPaint)
        }
    }

    private fun drawMusicalColors(canvas: Canvas, spectrum: AudioSpectrumSource, tick: Int, w: Int, h: Int) {
        val shapes = 8
        val n = spectrum.bandCount.coerceAtLeast(1)
        for (i in 0 until shapes) {
            val mag = spectrum.band(2 + i % (n - 2).coerceAtLeast(1))
            val cx = w * (0.12f + i * 0.11f)
            val cy = h * (0.3f + sin(tick * 0.02f * mag + i) * 0.18f * mag)
            fillPaint.color = hsv(i * 45f + mag * 90f, 0.85f, 0.3f + mag * 0.7f)
            canvas.drawRoundRect(
                cx - 30f - mag * 45f,
                cy - 18f - mag * 30f,
                cx + 30f + mag * 45f,
                cy + 18f + mag * 30f,
                16f,
                16f,
                fillPaint,
            )
        }
    }

    private fun drawBlazingColors(canvas: Canvas, spectrum: AudioSpectrumSource, @Suppress("UNUSED_PARAMETER") tick: Int, w: Int, h: Int) {
        val rays = 24
        val cx = w / 2f
        val cy = h / 2f
        val n = spectrum.bandCount.coerceAtLeast(1)
        val hueShift = spectrum.mids() * 90f
        for (i in 0 until rays) {
            val mag = spectrum.band(1 + i % (n - 1).coerceAtLeast(1))
            val a = i * (PI * 2 / rays).toFloat()
            fillPaint.color = hsv(i * 15f + hueShift, 1f, 0.35f + mag * 0.65f)
            fillPaint.alpha = (80 + mag * 175).toInt().coerceIn(0, 255)
            path.reset()
            path.moveTo(cx, cy)
            path.lineTo(
                cx + cos(a) * w * 0.55f * (0.25f + mag),
                cy + sin(a) * h * 0.55f * (0.25f + mag),
            )
            path.lineTo(cx + cos(a + 0.08f) * w * 0.15f, cy + sin(a + 0.08f) * h * 0.15f)
            path.close()
            canvas.drawPath(path, fillPaint)
        }
        fillPaint.alpha = 255
    }

    private fun drawColorCubes(canvas: Canvas, spectrum: AudioSpectrumSource, tick: Int, w: Int, h: Int) {
        val cubes = 12
        val n = spectrum.bandCount.coerceAtLeast(1)
        for (i in 0 until cubes) {
            val mag = spectrum.band(1 + i % (n - 1).coerceAtLeast(1))
            val cx = w * (0.1f + (i % 4) * 0.22f)
            val cy = h * (0.2f + (i / 4) * 0.25f)
            val size = 28f + mag * 70f
            val offset = sin(tick * 0.04f * (0.3f + mag) + i) * 18f * mag
            fillPaint.color = hsv(i * 30f + mag * 60f, 0.75f, 0.35f + mag * 0.6f)
            canvas.drawRect(
                cx - size / 2,
                cy - size / 2 + offset,
                cx + size / 2,
                cy + size / 2 + offset,
                fillPaint,
            )
            linePaint.color = Color.argb((80 + mag * 160).toInt(), 255, 255, 255)
            linePaint.strokeWidth = 2f
            canvas.drawRect(
                cx - size / 2,
                cy - size / 2 + offset,
                cx + size / 2,
                cy + size / 2 + offset,
                linePaint,
            )
        }
    }

    private fun drawPulsingColors(canvas: Canvas, spectrum: AudioSpectrumSource, @Suppress("UNUSED_PARAMETER") tick: Int, w: Int, h: Int) {
        val cx = w / 2f
        val cy = h / 2f
        val bass = spectrum.bass()
        val n = spectrum.bandCount.coerceAtLeast(1)
        for (i in 0 until 8) {
            val mag = spectrum.band(1 + i % (n - 1).coerceAtLeast(1))
            val pulse = mag * 0.65f + bass * 0.35f
            val radius = (50 + i * 42 + pulse * 90) * min(w, h) / 800f
            linePaint.color = hsv(i * 40f + bass * 80f, 0.9f, 0.4f + pulse * 0.6f)
            linePaint.strokeWidth = 2f + pulse * 4f
            canvas.drawCircle(cx, cy, radius, linePaint)
        }
    }

    private fun drawStarTime(canvas: Canvas, spectrum: AudioSpectrumSource, tick: Int, w: Int, h: Int) {
        val stars = 100
        val twinkle = spectrum.mids()
        val bass = spectrum.bass()
        val energy = spectrum.energy()
        val n = spectrum.bandCount.coerceAtLeast(1)
        for (i in 0 until stars) {
            val band = spectrum.band(1 + i % (n - 1).coerceAtLeast(1))
            val speed = 1 + (band * 5).toInt()
            val sx = (i * 97 + tick * speed) % w.coerceAtLeast(1)
            val sy = (i * 53 + tick * (1 + (bass * 3).toInt())) % h.coerceAtLeast(1)
            val r = 1f + (i % 3) + band * 3f + twinkle
            val bright = (80 + band * 175).toInt().coerceIn(0, 255)
            fillPaint.color = Color.argb(bright, 255, 255, 255)
            canvas.drawCircle(sx.toFloat(), sy.toFloat(), r, fillPaint)
            if (i % 17 == 0) {
                fillPaint.color = hsv(i * 19f + energy * 120f, 0.65f, 0.5f + band * 0.5f)
                canvas.drawCircle(sx.toFloat(), sy.toFloat(), r * (2f + band * 2f), fillPaint)
            }
        }
    }

    private fun drawSnowTime(canvas: Canvas, spectrum: AudioSpectrumSource, tick: Int, w: Int, h: Int) {
        val flakes = 70
        val energy = spectrum.energy()
        val bass = spectrum.bass()
        val n = spectrum.bandCount.coerceAtLeast(1)
        for (i in 0 until flakes) {
            val mag = spectrum.band(1 + i % (n - 1).coerceAtLeast(1))
            val fall = 2 + (energy * 6).toInt() + (i % 4)
            val x = (i * 131 + tick * (1 + (mag * 3).toInt())) % w.coerceAtLeast(1)
            val y = ((i * 197 + tick * fall) % (h + 40)) - 20
            val alpha = (100 + mag * 140).toInt().coerceIn(0, 255)
            fillPaint.color = Color.argb(alpha, 240, 248, 255)
            canvas.drawCircle(x.toFloat(), y.toFloat(), 1.5f + (i % 4) + bass * 2f, fillPaint)
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
