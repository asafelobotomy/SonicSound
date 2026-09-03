package app.sonicsound

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import app.sonicsound.models.FullscreenVisualizer
import app.sonicsound.playback.AudioProfile
import app.sonicsound.playback.VinylCondition

/** Preference row / swatch helpers for [SettingsFragment]. */
internal object SettingsPreferenceUi {
    val primaryColors = listOf(
        "#E53935", "#FB8C00", "#FDD835", "#43A047", "#1E88E5",
        "#8E24AA", "#00ACC1", "#D81B60", "#FFFFFF",
    )

    fun wireFocusRow(row: View, switch: SwitchCompat) {
        row.setOnClickListener { switch.toggle() }
        row.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                switch.toggle()
                true
            } else {
                false
            }
        }
    }

    fun bindColorSwatches(
        fragment: Fragment,
        container: LinearLayout,
        selectedSolidColor: String,
        onSelect: (String) -> Unit,
    ) {
        container.removeAllViews()
        val density = fragment.resources.displayMetrics.density
        val size = (44 * density).toInt()
        val gap = (10 * density).toInt()
        primaryColors.forEach { hex ->
            val swatch = View(fragment.requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).also {
                    it.marginEnd = gap
                }
                isFocusable = true
                contentDescription = hex
                background = swatchDrawable(
                    fragment, hex, hex.equals(selectedSolidColor, ignoreCase = true),
                )
                setOnClickListener {
                    onSelect(hex)
                    bindColorSwatches(fragment, container, hex, onSelect)
                }
                setOnFocusChangeListener { _, hasFocus ->
                    background = swatchDrawable(
                        fragment, hex,
                        hasFocus || hex.equals(selectedSolidColor, ignoreCase = true),
                    )
                }
            }
            container.addView(swatch)
        }
    }

    fun swatchDrawable(fragment: Fragment, hex: String, selected: Boolean): GradientDrawable {
        val fill = try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            Color.RED
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            if (selected) {
                setStroke((3 * fragment.resources.displayMetrics.density).toInt(), Color.WHITE)
            } else {
                setStroke(
                    (1 * fragment.resources.displayMetrics.density).toInt(),
                    Color.parseColor("#66FFFFFF"),
                )
            }
        }
    }

    fun audioProfileLabel(id: String): Int = when (id) {
        AudioProfile.OFF -> R.string.audio_profile_off
        AudioProfile.FLAT -> R.string.audio_profile_flat
        AudioProfile.BASS -> R.string.audio_profile_bass
        AudioProfile.TREBLE -> R.string.audio_profile_treble
        AudioProfile.VOCAL -> R.string.audio_profile_vocal
        AudioProfile.ROCK -> R.string.audio_profile_rock
        AudioProfile.ELECTRONIC -> R.string.audio_profile_electronic
        AudioProfile.CLASSICAL -> R.string.audio_profile_classical
        AudioProfile.POP -> R.string.audio_profile_pop
        AudioProfile.TV -> R.string.audio_profile_tv
        AudioProfile.HEADPHONES -> R.string.audio_profile_headphones
        AudioProfile.VINYL -> R.string.audio_profile_vinyl
        else -> R.string.audio_profile_off
    }

    fun vinylConditionLabel(id: String): Int = when (id) {
        VinylCondition.BRAND_NEW -> R.string.vinyl_condition_brand_new
        VinylCondition.SLIGHTLY_USED -> R.string.vinyl_condition_slightly_used
        VinylCondition.HEAVILY_USED -> R.string.vinyl_condition_heavily_used
        else -> R.string.vinyl_condition_brand_new
    }
}
