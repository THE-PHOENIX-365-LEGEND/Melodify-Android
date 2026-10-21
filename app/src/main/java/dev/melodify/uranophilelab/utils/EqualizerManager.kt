package dev.melodify.uranophilelab.utils

import android.content.Context
import android.media.audiofx.Equalizer
import android.util.Log
import dev.melodify.uranophilelab.model.EqPreset
import dev.melodify.uranophilelab.model.EqPresets
import kotlin.math.ln

object EqualizerManager {
    private const val TAG = "EqualizerManager"

    private var equalizer: Equalizer? = null
    private var prefs: SharedPreferenceManager? = null
    private var currentAudioSessionId: Int = -1

    var isEnabled: Boolean = false
        private set

    var currentPresetId: String? = "flat"
        private set

    val gains: FloatArray = FloatArray(10)

    interface EqualizerChangeListener {
        fun onEqualizerStateChanged(enabled: Boolean, presetId: String?, gains: FloatArray)
    }

    private val listeners = mutableListOf<EqualizerChangeListener>()

    fun addListener(listener: EqualizerChangeListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: EqualizerChangeListener) {
        listeners.remove(listener)
    }

    fun init(context: Context) {
        val sp = SharedPreferenceManager.getInstance(context)
        prefs = sp
        isEnabled = sp.eqEnabled
        currentPresetId = sp.eqPresetId
        val savedGains = sp.eqGains
        if (savedGains.size == 10) {
            System.arraycopy(savedGains, 0, gains, 0, 10)
        }
    }

    fun attachAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (currentAudioSessionId == audioSessionId && equalizer != null) {
            applyGainsToHardware()
            return
        }

        releaseAudioSession()
        currentAudioSessionId = audioSessionId

        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = isEnabled
            }
            applyGainsToHardware()
            Log.i(TAG, "Successfully attached Equalizer to audioSessionId: $audioSessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Equalizer audiofx for session $audioSessionId", e)
            equalizer = null
        }
    }

    fun releaseAudioSession() {
        try {
            equalizer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Equalizer", e)
        }
        equalizer = null
        currentAudioSessionId = -1
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        prefs?.eqEnabled = enabled
        applyGainsToHardware()
        notifyListeners()
    }

    fun applyPreset(preset: EqPreset) {
        currentPresetId = preset.id
        System.arraycopy(preset.gains, 0, gains, 0, 10)
        saveState()
        applyGainsToHardware()
        notifyListeners()
    }

    fun setBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex !in 0..9) return
        gains[bandIndex] = gainDb.coerceIn(-12f, 12f)

        // Check if matching preset
        val matchingPreset = EqPresets.kEqPresets.firstOrNull { preset ->
            preset.gains.foldIndexed(true) { idx, acc, g -> acc && kotlin.math.abs(g - gains[idx]) < 0.01f }
        }
        currentPresetId = matchingPreset?.id ?: "custom"

        saveState()
        applyGainsToHardware()
        notifyListeners()
    }

    fun reset() {
        val flatPreset = EqPresets.getPresetById("flat")
        if (flatPreset != null) {
            applyPreset(flatPreset)
        } else {
            for (i in 0..9) gains[i] = 0f
            currentPresetId = "flat"
            saveState()
            applyGainsToHardware()
            notifyListeners()
        }
    }

    private fun saveState() {
        prefs?.let {
            it.eqPresetId = currentPresetId
            it.eqGains = gains
        }
    }

    private fun applyGainsToHardware() {
        val eq = equalizer ?: return
        try {
            eq.enabled = isEnabled
            if (!isEnabled) return

            val numBands = eq.numberOfBands.toInt()
            val bandRange = eq.bandLevelRange ?: return
            val minLevel = bandRange[0].toInt()
            val maxLevel = bandRange[1].toInt()

            val frequencies = EqPresets.kEqFrequencies

            for (b in 0 until numBands) {
                val centerFreqHz = eq.getCenterFreq(b.toShort()) / 1000 // Convert mHz to Hz
                val interpolatedGainDb = interpolateGain(centerFreqHz, frequencies, gains)
                val levelMb = (interpolatedGainDb * 100f).toInt().coerceIn(minLevel, maxLevel)
                eq.setBandLevel(b.toShort(), levelMb.toShort())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying gains to hardware equalizer", e)
        }
    }

    private fun interpolateGain(freqHz: Int, targetFreqs: IntArray, targetGains: FloatArray): Float {
        if (freqHz <= targetFreqs.first()) return targetGains.first()
        if (freqHz >= targetFreqs.last()) return targetGains.last()

        for (i in 0 until targetFreqs.size - 1) {
            val f1 = targetFreqs[i]
            val f2 = targetFreqs[i + 1]
            if (freqHz in f1..f2) {
                val g1 = targetGains[i]
                val g2 = targetGains[i + 1]
                val logF = ln(freqHz.toDouble())
                val logF1 = ln(f1.toDouble())
                val logF2 = ln(f2.toDouble())
                val t = ((logF - logF1) / (logF2 - logF1)).toFloat()
                return g1 + t * (g2 - g1)
            }
        }
        return 0f
    }

    private fun notifyListeners() {
        for (listener in listeners) {
            listener.onEqualizerStateChanged(isEnabled, currentPresetId, gains)
        }
    }
}
