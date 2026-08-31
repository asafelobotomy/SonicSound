package app.sonicsound

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import app.sonicsound.models.FullscreenVisualizer
import app.sonicsound.playback.AudioProfile
import app.sonicsound.playback.VinylCondition
import app.sonicsound.subsonic.SubsonicClient
import app.sonicsound.update.AppUpdateChecker
import app.sonicsound.update.AppUpdateUi

class SettingsFragment : Fragment {
    private val primaryColors = listOf(
        "#E53935", // red
        "#FB8C00", // orange
        "#FDD835", // yellow
        "#43A047", // green
        "#1E88E5", // blue
        "#8E24AA", // purple
        "#00ACC1", // cyan
        "#D81B60", // magenta
        "#FFFFFF", // white
    )

    private var selectedSolidColor: String = FullscreenVisualizer.DEFAULT_SOLID
    private var suppressAutoSave = true
    private var bindGeneration = 0
    private val saveHandler = Handler(Looper.getMainLooper())
    private val saveTextRunnable = Runnable { persistSettings() }
    private var enableSaveRunnable: Runnable? = null

    private lateinit var transcoding: EditText
    private lateinit var cacheSize: EditText
    private lateinit var audioProfileSpinner: Spinner
    private lateinit var vinylConditionSpinner: Spinner
    private lateinit var rgSwitch: SwitchCompat
    private lateinit var offlineSwitch: SwitchCompat
    private lateinit var vizSpinner: Spinner
    private lateinit var speedSpinner: Spinner
    private lateinit var clockSwitch: SwitchCompat
    private lateinit var dateSwitch: SwitchCompat
    private lateinit var vizModes: List<Pair<String, String>>
    private lateinit var audioProfiles: List<Pair<String, String>>
    private lateinit var vinylConditions: List<Pair<String, String>>
    private lateinit var speeds: List<Pair<String, String>>

    constructor() : super()
    constructor(@Suppress("UNUSED_PARAMETER") client: SubsonicClient) : super()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onDestroyView() {
        saveHandler.removeCallbacks(saveTextRunnable)
        enableSaveRunnable?.let { saveHandler.removeCallbacks(it) }
        enableSaveRunnable = null
        suppressAutoSave = true
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        transcoding = view.findViewById(R.id.et_transcoding)
        cacheSize = view.findViewById(R.id.et_cache_size)
        audioProfileSpinner = view.findViewById(R.id.spinner_audio_profile)
        vinylConditionSpinner = view.findViewById(R.id.spinner_vinyl_condition)
        rgSwitch = view.findViewById(R.id.switch_replaygain)
        offlineSwitch = view.findViewById(R.id.switch_offline)
        val cacheInfo = view.findViewById<TextView>(R.id.tv_cache_info)
        vizSpinner = view.findViewById(R.id.spinner_fullscreen_visualizer)
        speedSpinner = view.findViewById(R.id.spinner_dvd_speed)
        val solidRow = view.findViewById<View>(R.id.ll_solid_color_row)
        val dvdRow = view.findViewById<View>(R.id.ll_dvd_speed_row)
        val vinylRow = view.findViewById<View>(R.id.ll_vinyl_condition_row)
        val swatches = view.findViewById<LinearLayout>(R.id.ll_solid_color_swatches)
        clockSwitch = view.findViewById(R.id.switch_fs_clock)
        dateSwitch = view.findViewById(R.id.switch_fs_date)
        val settings = KeyValueStorage.getSettings()

        suppressAutoSave = true
        transcoding.setText(settings.transcoding)
        cacheSize.setText(settings.cacheSize.toString())
        rgSwitch.isChecked = settings.replayGainEnabled
        offlineSwitch.isChecked = KeyValueStorage.getOfflineMode()
        clockSwitch.isChecked = settings.fullscreenShowClock
        dateSwitch.isChecked = settings.fullscreenShowDate
        selectedSolidColor = settings.fullscreenSolidColor.ifBlank {
            FullscreenVisualizer.DEFAULT_SOLID
        }
        refreshCache(cacheInfo)

        audioProfiles = AudioProfile.ALL.map { id ->
            id to getString(audioProfileLabel(id))
        }
        audioProfileSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            audioProfiles.map { it.second }
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val resolvedProfile = AudioProfile.resolve(settings)
        audioProfileSpinner.setSelection(
            audioProfiles.indexOfFirst { it.first == resolvedProfile }.coerceAtLeast(0)
        )

        vinylConditions = VinylCondition.ALL.map { id ->
            id to getString(vinylConditionLabel(id))
        }
        vinylConditionSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            vinylConditions.map { it.second }
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val resolvedCondition = VinylCondition.resolve(settings)
        vinylConditionSpinner.setSelection(
            vinylConditions.indexOfFirst { it.first == resolvedCondition }.coerceAtLeast(0)
        )

        vizModes = FullscreenVisualizer.ALL_MODES.map { mode ->
            mode to getString(FullscreenVisualizer.labelRes(mode))
        }
        vizSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            vizModes.map { it.second }
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        vizSpinner.setSelection(
            vizModes.indexOfFirst { it.first == settings.fullscreenVisualizer }.coerceAtLeast(0)
        )

        speeds = listOf(
            FullscreenVisualizer.SPEED_SLOW to getString(R.string.dvd_speed_slow),
            FullscreenVisualizer.SPEED_DEFAULT to getString(R.string.dvd_speed_default),
            FullscreenVisualizer.SPEED_FAST to getString(R.string.dvd_speed_fast),
        )
        speedSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            speeds.map { it.second }
        )
        speedSpinner.setSelection(
            speeds.indexOfFirst { it.first == settings.dvdSpeed }.coerceAtLeast(1)
        )

        fun refreshConditionalRows() {
            val mode = vizModes.getOrNull(vizSpinner.selectedItemPosition)?.first
                ?: FullscreenVisualizer.ART_BACKGROUND
            solidRow.isVisible = mode == FullscreenVisualizer.ART_SOLID
            dvdRow.isVisible = mode == FullscreenVisualizer.DVD
            val profile = audioProfiles.getOrNull(audioProfileSpinner.selectedItemPosition)?.first
                ?: AudioProfile.OFF
            vinylRow.isVisible = profile == AudioProfile.VINYL
        }
        vizSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                refreshConditionalRows()
                persistSettings()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        speedSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) = persistSettings()

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        refreshConditionalRows()
        bindColorSwatches(swatches)
        audioProfileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                refreshConditionalRows()
                persistSettings()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        vinylConditionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) = persistSettings()

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        wireFocusRow(view.findViewById(R.id.row_replaygain), rgSwitch)
        wireFocusRow(view.findViewById(R.id.row_fs_clock), clockSwitch)
        wireFocusRow(view.findViewById(R.id.row_fs_date), dateSwitch)
        wireFocusRow(view.findViewById(R.id.row_offline), offlineSwitch)

        rgSwitch.setOnCheckedChangeListener { _, _ -> persistSettings() }
        clockSwitch.setOnCheckedChangeListener { _, _ -> persistSettings() }
        dateSwitch.setOnCheckedChangeListener { _, _ -> persistSettings() }
        offlineSwitch.setOnCheckedChangeListener { _, checked ->
            if (suppressAutoSave) return@setOnCheckedChangeListener
            KeyValueStorage.setOfflineMode(checked)
        }
        transcoding.doOnTextChanged { _, _, _, _ -> scheduleTextSave() }
        cacheSize.doOnTextChanged { _, _, _, _ -> scheduleTextSave() }

        view.findViewById<Button>(R.id.btn_clear_cache).setOnClickListener {
            val freed = SubsonicClient(KeyValueStorage.getActiveAccount()).clearCoverCache()
            Toast.makeText(
                requireContext(),
                getString(R.string.cache_cleared, freed / 1024),
                Toast.LENGTH_SHORT
            ).show()
            refreshCache(cacheInfo)
        }

        view.findViewById<TextView>(R.id.tv_app_version).text =
            getString(R.string.app_version_label, AppUpdateChecker.currentVersionName())
        view.findViewById<Button>(R.id.btn_check_updates).setOnClickListener {
            val act = activity ?: return@setOnClickListener
            AppUpdateUi.checkAndPrompt(act, force = true)
        }

        // Spinners fire onItemSelected during initial setSelection; ignore until settled.
        val generation = ++bindGeneration
        enableSaveRunnable?.let { saveHandler.removeCallbacks(it) }
        enableSaveRunnable = Runnable {
            if (generation == bindGeneration && isAdded && view != null) {
                suppressAutoSave = false
            }
        }
        // Delay past any deferred spinner callbacks so open-Settings cannot persist/recreate.
        saveHandler.postDelayed(enableSaveRunnable!!, 450)
    }

    private fun scheduleTextSave() {
        if (suppressAutoSave) return
        saveHandler.removeCallbacks(saveTextRunnable)
        saveHandler.postDelayed(saveTextRunnable, 400)
    }

    private fun persistSettings() {
        if (suppressAutoSave || !isAdded || view == null) return
        if (!::transcoding.isInitialized || !::audioProfiles.isInitialized ||
            !::vinylConditions.isInitialized || !::vizModes.isInitialized
        ) {
            return
        }
        val parsedCache =
            cacheSize.text?.toString()?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val mode = vizModes.getOrNull(vizSpinner.selectedItemPosition)?.first
            ?: FullscreenVisualizer.ART_BACKGROUND
        val speed = speeds.getOrNull(speedSpinner.selectedItemPosition)?.first
            ?: FullscreenVisualizer.SPEED_DEFAULT
        val profile = audioProfiles.getOrNull(audioProfileSpinner.selectedItemPosition)?.first
            ?: AudioProfile.OFF
        val vinylCondition = vinylConditions.getOrNull(vinylConditionSpinner.selectedItemPosition)?.first
            ?: VinylCondition.BRAND_NEW
        val current = KeyValueStorage.getSettings()
        KeyValueStorage.setSettings(
            current.copy(
                transcoding = transcoding.text?.toString().orEmpty(),
                cacheSize = parsedCache,
                audioProfile = profile,
                vinylCondition = vinylCondition,
                eqEnabled = profile != AudioProfile.OFF,
                replayGainEnabled = rgSwitch.isChecked,
                fullscreenVisualizer = mode,
                fullscreenSolidColor = selectedSolidColor,
                dvdSpeed = speed,
                fullscreenShowClock = clockSwitch.isChecked,
                fullscreenShowDate = dateSwitch.isChecked,
            )
        )
        KeyValueStorage.setOfflineMode(offlineSwitch.isChecked)
    }

    private fun wireFocusRow(row: View, switch: SwitchCompat) {
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

    private fun bindColorSwatches(container: LinearLayout) {
        container.removeAllViews()
        val density = resources.displayMetrics.density
        val size = (44 * density).toInt()
        val gap = (10 * density).toInt()
        primaryColors.forEach { hex ->
            val swatch = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).also {
                    it.marginEnd = gap
                }
                isFocusable = true
                contentDescription = hex
                background = swatchDrawable(hex, hex.equals(selectedSolidColor, ignoreCase = true))
                setOnClickListener {
                    selectedSolidColor = hex
                    bindColorSwatches(container)
                    persistSettings()
                }
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        background = swatchDrawable(hex, selected = true)
                    } else {
                        background = swatchDrawable(
                            hex,
                            hex.equals(selectedSolidColor, ignoreCase = true)
                        )
                    }
                }
            }
            container.addView(swatch)
        }
    }

    private fun swatchDrawable(hex: String, selected: Boolean): GradientDrawable {
        val fill = try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            Color.RED
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            if (selected) {
                setStroke((3 * resources.displayMetrics.density).toInt(), Color.WHITE)
            } else {
                setStroke((1 * resources.displayMetrics.density).toInt(), Color.parseColor("#66FFFFFF"))
            }
        }
    }

    private fun refreshCache(cacheInfo: TextView) {
        try {
            val bytes = SubsonicClient(KeyValueStorage.getActiveAccount()).getCoverCacheSizeBytes()
            cacheInfo.text = getString(R.string.art_cache_size, bytes / 1024)
        } catch (_: Exception) {
            cacheInfo.text = getString(R.string.art_cache_unavailable)
        }
    }

    private fun audioProfileLabel(id: String): Int = when (id) {
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

    private fun vinylConditionLabel(id: String): Int = when (id) {
        VinylCondition.BRAND_NEW -> R.string.vinyl_condition_brand_new
        VinylCondition.SLIGHTLY_USED -> R.string.vinyl_condition_slightly_used
        VinylCondition.HEAVILY_USED -> R.string.vinyl_condition_heavily_used
        else -> R.string.vinyl_condition_brand_new
    }
}
