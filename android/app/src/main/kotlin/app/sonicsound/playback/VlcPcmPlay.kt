package app.sonicsound.playback

import android.media.AudioTrack
import android.util.Log
import app.sonicsound.visualizer.PlaybackSpectrum
import java.nio.ByteOrder
import kotlin.math.min

/** PCM play callback body for [VlcPcmOutput]. */
internal object VlcPcmPlay {
    fun handle(
        tag: String,
        trackLock: Any,
        pcm: ByteArray,
        bytes: Int,
        ch: Int,
        frames: Int,
        sampleRate: Int,
        getTrack: () -> AudioTrack?,
        openTrack: (Int, Int) -> Boolean,
        getOutputChannels: () -> Int,
        downmix: (ByteArray, Int, Int, Int, Int) -> Pair<ByteArray, Int>,
        onWritten: () -> Int,
    ) {
        val len = min(bytes, pcm.size)
        if (len <= 0) return
        val playCh = ch.coerceAtLeast(1)
        val playFrames = frames.coerceAtLeast(1)
        val rate = sampleRate

        val processed = if (VinylProcessor.isEnabled()) {
            VinylProcessor.processInPlace(pcm, len, playCh, playFrames, rate)
            pcm
        } else {
            pcm
        }

        val callbackCount: Int
        synchronized(trackLock) {
            var t = getTrack()
            if (t == null) {
                Log.w(tag, "PCM play with null AudioTrack — healing ${sampleRate}Hz/${playCh}ch")
                if (!openTrack(sampleRate.coerceAtLeast(8000), playCh.coerceIn(1, 8))) {
                    t = null
                } else {
                    t = getTrack()
                }
            }
            if (t != null) {
                if (t.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    runCatching { t.play() }
                }
                val outCh = getOutputChannels()
                val writePcm: ByteArray
                val writeLen: Int
                if (playCh == outCh || outCh < 1) {
                    writePcm = processed
                    writeLen = len
                } else {
                    val mixed = downmix(processed, len, playCh, playFrames, outCh)
                    writePcm = mixed.first
                    writeLen = mixed.second
                }
                var offset = 0
                while (offset < writeLen) {
                    val written = t.write(writePcm, offset, writeLen - offset)
                    if (written <= 0) break
                    offset += written
                }
            }
            callbackCount = onWritten()
        }

        PlaybackSpectrum.onPcmS16(
            processed, len, playCh, playFrames, ByteOrder.nativeOrder(), rate,
        )

        if (callbackCount == 1 || callbackCount == 50 || callbackCount == 200) {
            Log.i(
                tag,
                "PCM play#$callbackCount bytes=$len ch=$playCh outCh=${getOutputChannels()} frames=$playFrames " +
                    "peak=${"%.4f".format(PlaybackSpectrum.lastPcmPeak)} " +
                    "energy=${"%.3f".format(PlaybackSpectrum.energy())} " +
                    "bass=${"%.3f".format(PlaybackSpectrum.bass())} " +
                    "L/R=${"%.2f".format(PlaybackSpectrum.left())}/${"%.2f".format(PlaybackSpectrum.right())}",
            )
        }
    }
}
