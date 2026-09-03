package app.sonicsound

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
            id to getString(SettingsPreferenceUi.audioProfileLabel(id))
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
            id to getString(SettingsPreferenceUi.vinylConditionLabel(id))
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
        SettingsPreferenceUi.bindColorSwatches(this, swatches, selectedSolidColor) { hex ->
            selectedSolidColor = hex
            persistSettings()
        }
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
        SettingsPreferenceUi.wireFocusRow(view.findViewById(R.id.row_replaygain), rgSwitch)
        SettingsPreferenceUi.wireFocusRow(view.findViewById(R.id.row_fs_clock), clockSwitch)
        SettingsPreferenceUi.wireFocusRow(view.findViewById(R.id.row_fs_date), dateSwitch)
        SettingsPreferenceUi.wireFocusRow(view.findViewById(R.id.row_offline), offlineSwitch)

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

    private fun refreshCache(cacheInfo: TextView) {
        try {
            val bytes = SubsonicClient(KeyValueStorage.getActiveAccount()).getCoverCacheSizeBytes()
            cacheInfo.text = getString(R.string.art_cache_size, bytes / 1024)
        } catch (_: Exception) {
            cacheInfo.text = getString(R.string.art_cache_unavailable)
        }
    }


}
