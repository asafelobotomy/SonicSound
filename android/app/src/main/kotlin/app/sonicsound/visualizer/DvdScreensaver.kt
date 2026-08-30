package app.sonicsound.visualizer

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * DVD screensaver physics with randomized velocity and occasional exact corner hits.
 *
 * Classic fixed-ratio bounce never reaches corners; jitter + rare corner snaps fix that.
 */
class DvdScreensaver(
    private val speedPxPerSec: () -> Float,
    private val random: Random = Random.Default,
) {
    var x = 0f
        private set
    var y = 0f
        private set
    var vx = 0f
        private set
    var vy = 0f
        private set
    var cornerHits = 0
        private set

    fun reset(startX: Float, startY: Float) {
        x = startX
        y = startY
        assignRandomVelocity()
    }

    /** @return true when an exact corner hit occurred this frame. */
    fun step(dt: Float, parentW: Int, parentH: Int, artW: Int, artH: Int): Boolean {
        if (parentW <= 0 || parentH <= 0) return false
        val maxX = (parentW - artW).toFloat().coerceAtLeast(0f)
        val maxY = (parentH - artH).toFloat().coerceAtLeast(0f)
        x += vx * dt
        y += vy * dt

        var hitX = false
        var hitY = false
        if (x <= 0f) {
            x = 0f
            vx = kotlin.math.abs(vx)
            hitX = true
        } else if (x >= maxX) {
            x = maxX
            vx = -kotlin.math.abs(vx)
            hitX = true
        }
        if (y <= 0f) {
            y = 0f
            vy = kotlin.math.abs(vy)
            hitY = true
        } else if (y >= maxY) {
            y = maxY
            vy = -kotlin.math.abs(vy)
            hitY = true
        }

        if (hitX || hitY) {
            jitterVelocity()
        }

        var cornerHit = hitX && hitY
        if (cornerHit) {
            x = if (x <= 0f) 0f else maxX
            y = if (y <= 0f) 0f else maxY
            cornerHits++
        } else if (hitX || hitY) {
            // Small chance to nudge onto an exact corner after a wall bounce.
            if (random.nextFloat() < 0.012f) {
                val nearLeft = x <= 2f
                val nearRight = x >= maxX - 2f
                val nearTop = y <= 2f
                val nearBottom = y >= maxY - 2f
                if ((nearLeft || nearRight) && (nearTop || nearBottom)) {
                    x = if (nearLeft) 0f else maxX
                    y = if (nearTop) 0f else maxY
                    cornerHit = true
                    cornerHits++
                }
            }
        }
        return cornerHit
    }

    private fun assignRandomVelocity() {
        val speed = speedPxPerSec().coerceAtLeast(1f)
        val angleDeg = random.nextDouble(18.0, 72.0)
        val angle = Math.toRadians(angleDeg).toFloat()
        val signX = if (random.nextBoolean()) 1f else -1f
        val signY = if (random.nextBoolean()) 1f else -1f
        vx = speed * cos(angle) * signX
        vy = speed * sin(angle) * signY
    }

    private fun jitterVelocity() {
        val speed = hypot(vx.toDouble(), vy.toDouble()).toFloat().coerceAtLeast(1f)
        val angle = atan2(vy, vx) + Math.toRadians(random.nextDouble(-5.0, 5.0)).toFloat()
        vx = speed * cos(angle)
        vy = speed * sin(angle)
        // Occasional stronger redirect keeps corner trajectories unpredictable.
        if (random.nextFloat() < 0.04f) {
            assignRandomVelocity()
        }
    }
}
