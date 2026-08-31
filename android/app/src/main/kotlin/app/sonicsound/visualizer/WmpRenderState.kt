package app.sonicsound.visualizer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Persistent simulation state for WMP visualizations.
 * Updated with real dt so motion stays smooth at display refresh (≥60fps).
 *
 * Every particle / star / flake / blob is assigned an evenly spaced spectrum band
 * so the full track range drives the scene — not just the first few bins.
 */
class WmpRenderState {
    val barHeights = FloatArray(64)
    val barPeaks = FloatArray(64)
    val barPeakHold = FloatArray(64)

    private val particles = Array(PARTICLE_COUNT) { Particle() }
    private val stars = Array(STAR_COUNT) { Star() }
    private val flakes = Array(FLAKE_COUNT) { Flake() }
    private val blobs = Array(BLOB_COUNT) { Blob() }
    private var seeded = false
    private var time = 0f

    val particleList: Array<Particle> get() = particles
    val starList: Array<Star> get() = stars
    val flakeList: Array<Flake> get() = flakes
    val blobList: Array<Blob> get() = blobs
    val simTime: Float get() = time

    fun onModeChanged(mode: String) {
        if (mode == "wmp_particle" || mode == "wmp_startime" || mode == "wmp_snowtime" ||
            mode == "wmp_plenoptic" || mode == "wmp_ambience" || mode == "wmp_battery" ||
            mode == "wmp_alchemy"
        ) {
            seeded = false
        }
    }

    fun step(dt: Float, spectrum: AudioSpectrumSource, mode: String) {
        time += dt
        ensureSeeded(spectrum.bandCount)
        // Bars always track the delayed spectrum (used by Bars / Ocean / Fire).
        updateBars(dt, spectrum)
        when (mode) {
            "wmp_particle", "wmp_ambience" -> updateParticles(dt, spectrum)
            "wmp_startime" -> updateStars(dt, spectrum)
            "wmp_snowtime" -> updateFlakes(dt, spectrum)
            "wmp_plenoptic" -> updateBlobs(dt, spectrum)
            "wmp_battery", "wmp_alchemy", "wmp_spikes",
            "wmp_musical_colors", "wmp_blazing_colors",
            "wmp_color_cubes", "wmp_pulsing_colors",
            "wmp_scope", "wmp_bars", "wmp_ocean_mist", "wmp_fire_storm",
            -> {
                // Keep sims warm so mode switches stay seamless without hitching.
                updateParticles(dt * 0.2f, spectrum)
                updateStars(dt * 0.2f, spectrum)
                updateFlakes(dt * 0.2f, spectrum)
                updateBlobs(dt * 0.2f, spectrum)
            }
            else -> {
                updateParticles(dt * 0.2f, spectrum)
                updateStars(dt * 0.2f, spectrum)
                updateFlakes(dt * 0.2f, spectrum)
                updateBlobs(dt * 0.2f, spectrum)
            }
        }
    }

    private fun ensureSeeded(bandCount: Int) {
        if (seeded) return
        seeded = true
        val bands = bandCount.coerceAtLeast(3)
        val rng = Random(0x51F0A11)
        fun bandFor(i: Int, count: Int): Int {
            val t = if (count <= 1) 0f else i.toFloat() / (count - 1)
            return (1 + t * (bands - 2)).toInt().coerceIn(1, bands - 1)
        }
        for ((i, p) in particles.withIndex()) {
            p.x = rng.nextFloat()
            p.y = rng.nextFloat()
            p.vx = (rng.nextFloat() - 0.5f) * 0.06f
            p.vy = (rng.nextFloat() - 0.5f) * 0.06f
            p.size = 0.4f + rng.nextFloat()
            p.hue = rng.nextFloat() * 360f
            p.band = bandFor(i, particles.size)
            p.level = 0f
        }
        for ((i, s) in stars.withIndex()) {
            s.x = rng.nextFloat()
            s.y = rng.nextFloat()
            s.z = 0.2f + rng.nextFloat() * 0.8f
            s.hue = rng.nextFloat() * 360f
            s.band = bandFor(i, stars.size)
            s.twinkle = 0f
        }
        for ((i, f) in flakes.withIndex()) {
            f.x = rng.nextFloat()
            f.y = rng.nextFloat()
            f.vy = 0.05f + rng.nextFloat() * 0.12f
            f.vx = (rng.nextFloat() - 0.5f) * 0.04f
            f.size = 0.5f + rng.nextFloat()
            f.band = bandFor(i, flakes.size)
            f.level = 0f
        }
        for (i in blobs.indices) {
            val b = blobs[i]
            b.x = 0.12f + (i % 5) * 0.19f
            b.y = 0.18f + (i / 5) * 0.28f
            b.phase = rng.nextFloat() * (PI * 2).toFloat()
            b.band = bandFor(i, blobs.size)
            b.level = 0f
        }
    }

    private fun updateBars(dt: Float, spectrum: AudioSpectrumSource) {
        val n = barHeights.size
        val bands = spectrum.bandCount.coerceAtLeast(2)
        val center = (n - 1) / 2f
        val aUp = 1f - kotlin.math.exp(-dt * spectrum.attackHz)
        val aDown = 1f - kotlin.math.exp(-dt * spectrum.releaseHz)
        val left = spectrum.left()
        val right = spectrum.right()
        val midBalance = ((left + right) * 0.5f).coerceAtLeast(0.02f)
        for (i in 0 until n) {
            val dist = (kotlin.math.abs(i - center) / center).coerceIn(0f, 1f)
            val band = (1 + dist * (bands - 2)).toInt().coerceIn(1, bands - 1)
            // Left half follows L, right half follows R (classic stereo analyzer feel).
            val ch = if (i <= center) left else right
            val balance = (0.72f + 0.5f * (ch / midBalance)).coerceIn(0.55f, 1.4f)
            val target = (spectrum.band(band) * balance).coerceIn(0f, 1f)
            val cur = barHeights[i]
            barHeights[i] = if (target > cur) {
                cur + (target - cur) * aUp
            } else {
                cur + (target - cur) * aDown
            }
            val holdSec = (60f / spectrum.bpm.coerceIn(50f, 200f) * 0.22f).coerceIn(0.08f, 0.28f)
            if (barHeights[i] >= barPeaks[i]) {
                barPeaks[i] = barHeights[i]
                barPeakHold[i] = holdSec
            } else {
                barPeakHold[i] = (barPeakHold[i] - dt).coerceAtLeast(0f)
                if (barPeakHold[i] <= 0f) {
                    val fall = (0.55f + spectrum.releaseHz * 0.02f).coerceIn(0.4f, 1.1f)
                    barPeaks[i] = (barPeaks[i] - dt * fall).coerceAtLeast(barHeights[i])
                }
            }
        }
    }

    private fun updateParticles(dt: Float, spectrum: AudioSpectrumSource) {
        val energy = spectrum.energy()
        val bass = spectrum.bass()
        val t = time
        val levelA = 1f - kotlin.math.exp(-dt * spectrum.attackHz * 0.7f)
        val levelR = 1f - kotlin.math.exp(-dt * spectrum.releaseHz)
        for (p in particles) {
            val mag = spectrum.band(p.band)
            val angle = t * (0.35f + p.size * 0.2f) + p.hue * 0.017f + mag * 1.2f
            val flowX = cos(angle) * (0.015f + mag * 0.07f + energy * 0.025f)
            val flowY = sin(angle * 0.9f) * (0.012f + bass * 0.055f + mag * 0.045f)
            p.vx += (flowX - p.vx) * (1f - kotlin.math.exp(-dt * 5f))
            p.vy += (flowY - p.vy) * (1f - kotlin.math.exp(-dt * 5f))
            val speed = (0.35f + mag * 1.0f + energy * 0.3f).coerceIn(0.2f, 1.8f)
            p.x += p.vx * speed * dt
            p.y += p.vy * speed * dt
            if (p.x < 0f) p.x += 1f
            if (p.x > 1f) p.x -= 1f
            if (p.y < 0f) p.y += 1f
            if (p.y > 1f) p.y -= 1f
            val a = if (mag > p.level) levelA else levelR
            p.level += (mag - p.level) * a
        }
    }

    private fun updateStars(dt: Float, spectrum: AudioSpectrumSource) {
        val energy = spectrum.energy()
        val aUp = 1f - kotlin.math.exp(-dt * spectrum.attackHz * 0.7f)
        val aDown = 1f - kotlin.math.exp(-dt * spectrum.releaseHz)
        for (s in stars) {
            val mag = spectrum.band(s.band)
            val speed = (0.045f + mag * 0.6f + energy * 0.2f) * s.z
            s.x += speed * dt
            if (s.x > 1f) {
                s.x -= 1f
                s.y = Random.nextFloat()
            }
            val a = if (mag > s.twinkle) aUp else aDown
            s.twinkle += (mag - s.twinkle) * a
        }
    }

    private fun updateFlakes(dt: Float, spectrum: AudioSpectrumSource) {
        val energy = spectrum.energy()
        val bass = spectrum.bass()
        val aUp = 1f - kotlin.math.exp(-dt * spectrum.attackHz * 0.7f)
        val aDown = 1f - kotlin.math.exp(-dt * spectrum.releaseHz)
        for (f in flakes) {
            val mag = spectrum.band(f.band)
            f.y += (f.vy + energy * 0.4f + mag * 0.25f) * dt
            f.x += (f.vx + sin(time * 1.4f + f.band) * 0.035f * mag) * dt
            if (f.y > 1.05f) {
                f.y = -0.05f
                f.x = Random.nextFloat()
            }
            if (f.x < 0f) f.x += 1f
            if (f.x > 1f) f.x -= 1f
            val target = mag * 0.7f + bass * 0.3f
            val a = if (target > f.level) aUp else aDown
            f.level += (target - f.level) * a
        }
    }

    private fun updateBlobs(dt: Float, spectrum: AudioSpectrumSource) {
        val aUp = 1f - kotlin.math.exp(-dt * spectrum.attackHz * 0.7f)
        val aDown = 1f - kotlin.math.exp(-dt * spectrum.releaseHz)
        for (b in blobs) {
            val mag = spectrum.band(b.band)
            b.phase += dt * (0.7f + mag * 3.4f)
            val a = if (mag > b.level) aUp else aDown
            b.level += (mag - b.level) * a
            b.x = (b.x + sin(b.phase) * dt * 0.055f * (0.3f + mag)).let {
                when {
                    it < 0.05f -> 0.05f
                    it > 0.95f -> 0.95f
                    else -> it
                }
            }
            b.y = (b.y + cos(b.phase * 0.85f) * dt * 0.065f * (0.3f + mag)).let {
                when {
                    it < 0.08f -> 0.08f
                    it > 0.92f -> 0.92f
                    else -> it
                }
            }
        }
    }

    class Particle {
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var size = 1f
        var hue = 0f
        var band = 1
        var level = 0f
    }

    class Star {
        var x = 0f
        var y = 0f
        var z = 1f
        var hue = 0f
        var band = 1
        var twinkle = 0f
    }

    class Flake {
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0.1f
        var size = 1f
        var band = 1
        var level = 0f
    }

    class Blob {
        var x = 0.5f
        var y = 0.5f
        var phase = 0f
        var band = 1
        var level = 0f
    }

    companion object {
        const val PARTICLE_COUNT = 110
        const val STAR_COUNT = 140
        const val FLAKE_COUNT = 100
        const val BLOB_COUNT = 14
    }
}
