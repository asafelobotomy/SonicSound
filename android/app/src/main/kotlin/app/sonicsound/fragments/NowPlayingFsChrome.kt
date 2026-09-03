package app.sonicsound.fragments

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import app.sonicsound.KeyValueStorage
import app.sonicsound.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Clock / toast / typeface helpers for [NowPlayingFullscreen]. */
internal class NowPlayingFsChrome(
    private val root: View,
    private val fsClock: LinearLayout,
    private val fsClockTime: TextView,
    private val fsClockDate: TextView,
    private val fsToast: TextView,
    private val isActive: () -> Boolean,
) {
    private val hideHandler = Handler(Looper.getMainLooper())
    private val toastHideRunnable = Runnable { hideAppToast() }
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            refreshClock()
            clockHandler.postDelayed(this, 15_000)
        }
    }

    fun styleExtraBold(tv: TextView) {
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.paint.isFakeBoldText = true
        tv.paint.style = Paint.Style.FILL_AND_STROKE
        tv.paint.strokeWidth = 1.15f * root.resources.displayMetrics.density
        tv.invalidate()
    }

    fun styleBold(tv: TextView) {
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.paint.isFakeBoldText = true
        tv.paint.style = Paint.Style.FILL
        tv.paint.strokeWidth = 0f
        tv.invalidate()
    }

    fun showAppToast(message: String) {
        hideHandler.removeCallbacks(toastHideRunnable)
        fsToast.animate().cancel()
        fsToast.text = message
        fsToast.alpha = 0f
        fsToast.isVisible = true
        fsToast.bringToFront()
        (fsToast.parent as? ViewGroup)?.invalidate()
        fsToast.animate().alpha(1f).setDuration(160).start()
        hideHandler.postDelayed(toastHideRunnable, 2200)
    }

    fun hideAppToast() {
        if (!fsToast.isVisible) return
        fsToast.animate()
            .alpha(0f)
            .setDuration(220)
            .withEndAction {
                fsToast.isVisible = false
                fsToast.alpha = 1f
            }
            .start()
    }

    fun cancelToast() {
        hideHandler.removeCallbacks(toastHideRunnable)
        fsToast.animate().cancel()
        fsToast.isVisible = false
    }

    fun startClockUpdates() {
        clockHandler.removeCallbacks(clockRunnable)
        refreshClock()
        clockHandler.post(clockRunnable)
    }

    fun stopClockUpdates() {
        clockHandler.removeCallbacks(clockRunnable)
        fsClock.isVisible = false
    }

    fun refreshClock() {
        if (!isActive()) return
        val settings = KeyValueStorage.getSettings()
        val showTime = settings.fullscreenShowClock
        val showDate = settings.fullscreenShowDate
        fsClock.isVisible = showTime || showDate
        fsClockTime.isVisible = showTime
        fsClockDate.isVisible = showDate
        if (!showTime && !showDate) return
        val now = Date()
        val locale = Locale.getDefault()
        if (showTime) {
            fsClockTime.text = DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(now)
            styleExtraBold(fsClockTime)
        }
        if (showDate) {
            fsClockDate.text = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(now)
            styleBold(fsClockDate)
        }
    }

    fun formatSec(seconds: Int): String =
        "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
}
