package app.sonicsound.visualizer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/**
 * Canvas drawing for legacy Windows Media Player visualizations.
 *
 * Each mode is calibrated against the live pipeline:
 * - Full-band band mapping (skip DC)
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
        canvas.drawColor(Color.BLACK)
        when (mode) {
            "wmp_bars" -> WmpClassicRenderers.drawBars(
                canvas, barPaint, peakPaint, state, spectrum, w, h, cool = false, warm = false,
            )
            "wmp_ocean_mist" -> WmpClassicRenderers.drawBars(
                canvas, barPaint, peakPaint, state, spectrum, w, h, cool = true, warm = false,
            )
            "wmp_fire_storm" -> WmpClassicRenderers.drawBars(
                canvas, barPaint, peakPaint, state, spectrum, w, h, cool = false, warm = true,
            )
            "wmp_scope" -> WmpClassicRenderers.drawScope(canvas, linePaint, path, spectrum, w, h)
            "wmp_battery" -> WmpClassicRenderers.drawBattery(canvas, linePaint, spectrum, state, w, h)
            "wmp_alchemy" -> WmpClassicRenderers.drawAlchemy(
                canvas, fillPaint, linePaint, path, spectrum, state, w, h,
            )
            "wmp_ambience" -> WmpParticleRenderers.drawAmbience(canvas, fillPaint, state, spectrum, w, h)
            "wmp_particle" -> WmpParticleRenderers.drawParticle(canvas, fillPaint, spectrum, w, h)
            "wmp_plenoptic" -> WmpParticleRenderers.drawPlenoptic(canvas, fillPaint, state, spectrum, w, h)
            "wmp_spikes" -> WmpParticleRenderers.drawSpikes(canvas, linePaint, spectrum, state, w, h)
            "wmp_musical_colors" -> WmpColorRenderers.drawMusicalColors(
                canvas, fillPaint, spectrum, state, w, h,
            )
            "wmp_blazing_colors" -> WmpColorRenderers.drawBlazingColors(
                canvas, fillPaint, linePaint, path, spectrum, state, w, h,
            )
            "wmp_color_cubes" -> WmpColorRenderers.drawColorCubes(
                canvas, fillPaint, path, spectrum, state, w, h,
            )
            "wmp_pulsing_colors" -> WmpColorRenderers.drawPulsingColors(
                canvas, linePaint, spectrum, state, w, h,
            )
            "wmp_startime" -> WmpParticleRenderers.drawStarTime(canvas, fillPaint, state, spectrum, w, h)
            "wmp_snowtime" -> WmpParticleRenderers.drawSnowTime(canvas, fillPaint, state, spectrum, w, h)
            else -> {
                // Unknown mode: stay black — do not fall back to bars (reads as a stub).
            }
        }
    }
}
