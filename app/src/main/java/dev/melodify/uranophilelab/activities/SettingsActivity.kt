package dev.melodify.uranophilelab.activities

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.melodify.uranophilelab.BaseApplicationClass
import dev.melodify.uranophilelab.R
import dev.melodify.uranophilelab.databinding.ActivitySettingsBinding
import dev.melodify.uranophilelab.utils.MiniPlayerHelper
import dev.melodify.uranophilelab.utils.MusicPlayerManager
import dev.melodify.uranophilelab.utils.SharedPreferenceManager
import dev.melodify.uranophilelab.utils.SleepTimerManager
import dev.melodify.uranophilelab.utils.TrackDownloader
import dev.melodify.uranophilelab.utils.customview.MaterialCustomSwitch.OnCheckChangeListener

class SettingsActivity : AppCompatActivity() {
    var binding: ActivitySettingsBinding? = null

    private val sleepTimerListener = object : SleepTimerManager.SleepTimerListener {
        override fun onTimerTick(remainingSeconds: Long) {
            updateSleepTimerUI()
        }

        override fun onTimerFinished() {
            updateSleepTimerUI()
        }

        override fun onTimerCancelled() {
            updateSleepTimerUI()
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding!!.getRoot())
        MiniPlayerHelper.initMiniPlayer(this)
        val settingsSharedPrefManager = SettingsSharedPrefManager(this)
        val sharedPreferenceManager: SharedPreferenceManager =
            SharedPreferenceManager.getInstance(this)

        binding!!.downloadOverCellular.setOnCheckChangeListener(object : OnCheckChangeListener {
            override fun onCheckChanged(isChecked: Boolean) {
                settingsSharedPrefManager.downloadOverCellular = isChecked
            }
        })
        binding!!.highQualityTrack.setOnCheckChangeListener(object : OnCheckChangeListener {
            override fun onCheckChanged(isChecked: Boolean) {
                settingsSharedPrefManager.highQualityTrack = isChecked
            }
        })
        binding!!.storeInCache.setOnCheckChangeListener(object : OnCheckChangeListener {
            override fun onCheckChanged(isChecked: Boolean) {
                settingsSharedPrefManager.storeInCache = isChecked
            }
        })
        binding!!.playInBackground?.setOnCheckChangeListener(object : OnCheckChangeListener {
            override fun onCheckChanged(isChecked: Boolean) {
                settingsSharedPrefManager.playInBackground = isChecked
            }
        })

        binding!!.downloadOverCellular.setChecked(settingsSharedPrefManager.downloadOverCellular)
        binding!!.highQualityTrack.setChecked(settingsSharedPrefManager.highQualityTrack)
        binding!!.storeInCache.setChecked(settingsSharedPrefManager.storeInCache)
        binding!!.playInBackground?.setChecked(settingsSharedPrefManager.playInBackground)

        // Download Format selector (MP3 / M4A / OGG)
        val currentFormat = settingsSharedPrefManager.downloadFormat
        val formatCheckedId = when (currentFormat) {
            TrackDownloader.DownloadFormat.MP3.extension -> R.id.format_mp3
            TrackDownloader.DownloadFormat.OGG.extension -> R.id.format_ogg
            else -> R.id.format_m4a
        }
        binding!!.downloadFormatChipGroup?.check(formatCheckedId)
        binding!!.downloadFormatChipGroup?.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val newExt = when (checkedIds[0]) {
                    R.id.format_mp3 -> TrackDownloader.DownloadFormat.MP3.extension
                    R.id.format_ogg -> TrackDownloader.DownloadFormat.OGG.extension
                    else -> TrackDownloader.DownloadFormat.M4A.extension
                }
                settingsSharedPrefManager.downloadFormat = newExt
            }
        }

        binding!!.themeChipGroup.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int ->
            settingsSharedPrefManager.theme =
                if (checkedId == R.id.dark) "dark" else if (checkedId == R.id.light) "light" else "system"
            BaseApplicationClass.updateTheme()
        }

        binding!!.clearCache.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Cache")
                .setMessage("Are you sure you want to clear the cache?")
                .setPositiveButton("Yes") { _: DialogInterface?, _: Int ->
                    sharedPreferenceManager.clearOldPrefsAsync(this@SettingsActivity, null)
                }
                .setNegativeButton("No", null)
                .show()
        }

        // --- Audio Streaming Quality ---
        val qualities = arrayOf("lossless", "320kbps", "160kbps", "96kbps", "48kbps", "12kbps")
        val qualityLabels = arrayOf("Lossless (24-bit / 96 kHz Hi-Res)", "320kbps (Very High)", "160kbps (High)", "96kbps (Medium)", "48kbps (Low)", "12kbps (Very Low)")

        fun updateQualityUI() {
            val currentQuality = sharedPreferenceManager.getStreamQuality()
            val index = qualities.indexOfFirst { it.equals(currentQuality, ignoreCase = true) }
            val label = if (index >= 0) qualityLabels[index] else currentQuality
            binding?.trackQualityValue?.text = label
        }

        updateQualityUI()

        binding?.trackQualitySetting?.setOnClickListener {
            val currentQuality = sharedPreferenceManager.getStreamQuality()
            val selectedIndex = qualities.indexOfFirst { it.equals(currentQuality, ignoreCase = true) }.let { if (it < 0) 1 else it }

            MaterialAlertDialogBuilder(this)
                .setTitle("Audio Streaming Quality")
                .setSingleChoiceItems(qualityLabels, selectedIndex) { dialog, which ->
                    val chosenQuality = qualities[which]
                    sharedPreferenceManager.setStreamQuality(chosenQuality)
                    MusicPlayerManager.setTrackQuality(chosenQuality, sharedPreferenceManager)
                    updateQualityUI()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // --- Preferred Music Provider ---
        val providers = arrayOf("saavn")
        val providerLabels = arrayOf("JioSaavn (Default)")

        fun updateProviderUI() {
            val currentProvider = sharedPreferenceManager.getPreferredProvider()
            val index = providers.indexOfFirst { it.equals(currentProvider, ignoreCase = true) }
            val label = if (index >= 0) providerLabels[index] else currentProvider
            binding?.musicProviderValue?.text = label
        }

        updateProviderUI()

        binding?.musicProviderSetting?.setOnClickListener {
            val currentProvider = sharedPreferenceManager.getPreferredProvider()
            val selectedIndex = providers.indexOfFirst { it.equals(currentProvider, ignoreCase = true) }.let { if (it < 0) 0 else it }

            MaterialAlertDialogBuilder(this)
                .setTitle("Preferred Music Provider")
                .setSingleChoiceItems(providerLabels, selectedIndex) { dialog, which ->
                    val chosenProvider = providers[which]
                    sharedPreferenceManager.setPreferredProvider(chosenProvider)
                    updateProviderUI()
                    Toast.makeText(this, "Search provider updated to ${providerLabels[which]}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // --- Variable Playback Speed ---
        val speeds = arrayOf("0.5x", "0.75x", "1.0x (Normal)", "1.25x", "1.5x", "1.75x", "2.0x")
        val speedValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

        fun updateSpeedUI() {
            val currentSpeed = MusicPlayerManager.PLAYBACK_SPEED
            val index = speedValues.indexOfFirst { kotlin.math.abs(it - currentSpeed) < 0.01f }
            val label = if (index >= 0) speeds[index] else "${currentSpeed}x"
            binding?.playbackSpeedValue?.text = label
        }

        updateSpeedUI()

        binding?.playbackSpeedSetting?.setOnClickListener {
            val currentSpeed = MusicPlayerManager.PLAYBACK_SPEED
            val selectedIndex = speedValues.indexOfFirst { kotlin.math.abs(it - currentSpeed) < 0.01f }.let { if (it < 0) 2 else it }

            MaterialAlertDialogBuilder(this)
                .setTitle("Variable Playback Speed")
                .setSingleChoiceItems(speeds, selectedIndex) { dialog, which ->
                    val chosenSpeed = speedValues[which]
                    MusicPlayerManager.setPlaybackSpeed(chosenSpeed)
                    updateSpeedUI()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // --- Sleep Timer ---
        updateSleepTimerUI()

        binding?.sleepTimerSetting?.setOnClickListener {
            val options = arrayOf("15 Minutes", "30 Minutes", "45 Minutes", "60 Minutes", "End of Track", "Custom Time...", "Turn Off Timer")
            MaterialAlertDialogBuilder(this)
                .setTitle("Sleep Timer")
                .setItems(options) { dialog, which ->
                    when (which) {
                        0 -> SleepTimerManager.startTimer(this, 15)
                        1 -> SleepTimerManager.startTimer(this, 30)
                        2 -> SleepTimerManager.startTimer(this, 45)
                        3 -> SleepTimerManager.startTimer(this, 60)
                        4 -> SleepTimerManager.startEndOfTrackTimer(this)
                        5 -> showCustomSleepTimerDialog()
                        6 -> {
                            SleepTimerManager.cancelTimer()
                            Toast.makeText(this, "Sleep timer turned off", Toast.LENGTH_SHORT).show()
                        }
                    }
                    updateSleepTimerUI()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // --- Equalizer ---
        binding?.equalizerSetting?.setOnClickListener {
            dev.melodify.uranophilelab.dialogs.EqualizerBottomSheetDialog(this).show()
        }

        binding!!.themeChipGroup.check(if (settingsSharedPrefManager.theme == "light") R.id.light else if (settingsSharedPrefManager.theme == "system") R.id.system else R.id.dark)
    }

    private fun showCustomSleepTimerDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Minutes (e.g. 20)"
            setPadding(48, 24, 48, 24)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Custom Sleep Timer")
            .setMessage("Enter timer duration in minutes:")
            .setView(input)
            .setPositiveButton("Set") { dialog, _ ->
                val text = input.text.toString().trim()
                val mins = text.toIntOrNull()
                if (mins != null && mins > 0) {
                    SleepTimerManager.startTimer(this, mins)
                    updateSleepTimerUI()
                } else {
                    Toast.makeText(this, "Please enter a valid number of minutes", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateSleepTimerUI() {
        runOnUiThread {
            binding?.sleepTimerValue?.text = SleepTimerManager.getFormattedRemainingTime()
        }
    }

    override fun onResume() {
        super.onResume()
        MiniPlayerHelper.onActivityResume(this)
        SleepTimerManager.addListener(sleepTimerListener)
        updateSleepTimerUI()
    }

    override fun onPause() {
        super.onPause()
        MiniPlayerHelper.onActivityPause(this)
        SleepTimerManager.removeListener(sleepTimerListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    fun backPress(view: View?) {
        finish()
    }

    class SettingsSharedPrefManager(context: Context) {
        var sharedPreferences: SharedPreferences = context.getSharedPreferences("settings", MODE_PRIVATE)

        var downloadOverCellular: Boolean
            get() = sharedPreferences.getBoolean("download_over_cellular", true)
            set(value) {
                sharedPreferences.edit { putBoolean("download_over_cellular", value) }
            }

        var highQualityTrack: Boolean
            get() = sharedPreferences.getBoolean("high_quality_track", true)
            set(value) {
                sharedPreferences.edit { putBoolean("high_quality_track", value) }
            }

        var storeInCache: Boolean
            get() = sharedPreferences.getBoolean("store_in_cache", true)
            set(value) {
                sharedPreferences.edit { putBoolean("store_in_cache", value) }
            }

        var playInBackground: Boolean
            get() = sharedPreferences.getBoolean("play_in_background", true)
            set(value) {
                sharedPreferences.edit { putBoolean("play_in_background", value) }
            }

        var downloadFormat: String
            get() = sharedPreferences.getString("download_format", TrackDownloader.DownloadFormat.M4A.extension) ?: TrackDownloader.DownloadFormat.M4A.extension
            set(value) {
                sharedPreferences.edit { putString("download_format", value) }
            }

        var theme: String?
            get() = sharedPreferences.getString("theme", "system")
            set(theme) {
                sharedPreferences.edit { putString("theme", theme) }
            }
    }
}
