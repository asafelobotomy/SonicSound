package app.sonicsound.fragments

import android.content.res.ColorStateList
import android.view.KeyEvent
import android.view.View
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import app.sonicsound.R
import kotlin.math.floor

/** TV scrubber: focus shows frame; OK arms L/R seek; Back disarms. */
class NowPlayingScrubber(
    private val row: View,
    private val seekBar: SeekBar,
    private val durationProvider: () -> Int,
    private val onSeek: (Float) -> Unit,
    private val onTimePreview: (Int) -> Unit,
) {
    var armed = false
        private set

    fun wire() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val dur = durationProvider()
                onTimePreview(floor((progress / 100.0) * dur).toInt())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seekBar?.progress ?: return
                if (armed) onSeek(progress / 100f)
            }
        })
        seekBar.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && armed) {
                armed = false
                onSeek(seekBar.progress / 100f)
            }
            updateUi()
        }
        seekBar.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    armed = !armed
                    if (!armed) onSeek(seekBar.progress / 100f)
                    updateUi()
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> !armed
                else -> false
            }
        }
    }

    /** @return true if Back disarmed scrubbing (caller should consume Back). */
    fun disarmOnBack(): Boolean {
        if (!armed) return false
        armed = false
        updateUi()
        return true
    }

    fun updateUi() {
        val focused = seekBar.hasFocus()
        row.foreground = when {
            armed -> ResourcesCompat.getDrawable(row.resources, R.drawable.focus_frame_armed, null)
            focused -> ResourcesCompat.getDrawable(row.resources, R.drawable.focus_frame, null)
            else -> null
        }
        seekBar.alpha = if (armed) 1f else 0.9f
        val highlight = ContextCompat.getColor(row.context, R.color.sonicsound_item_highlight)
        val armedColor = ContextCompat.getColor(row.context, R.color.scrubber_thumb_armed)
        seekBar.thumbTintList = ColorStateList.valueOf(if (armed) armedColor else highlight)
        seekBar.progressTintList = ColorStateList.valueOf(if (armed) armedColor else highlight)
    }
}
