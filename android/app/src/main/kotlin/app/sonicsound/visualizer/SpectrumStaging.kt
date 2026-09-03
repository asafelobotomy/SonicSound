package app.sonicsound.visualizer

/** FFT staging ring for [PlaybackSpectrum]. */
internal class SpectrumStaging(private val slots: Int, private val fftSize: Int) {
    private val windows = Array(slots) { FloatArray(fftSize) }
    private var read = 0
    private var pending = 0

    fun clear() {
        read = 0
        pending = 0
    }

    fun offer(window: FloatArray, writePos: Int) {
        if (pending == slots) {
            read = (read + 1) % slots
            pending--
        }
        val slot = (read + pending) % slots
        val dest = windows[slot]
        for (i in 0 until fftSize) {
            dest[i] = window[(writePos + i) % fftSize]
        }
        pending++
    }

    fun take(dest: FloatArray): Boolean {
        if (pending <= 0) return false
        System.arraycopy(windows[read], 0, dest, 0, fftSize)
        read = (read + 1) % slots
        pending--
        return true
    }

    val hasPending: Boolean get() = pending > 0
}
