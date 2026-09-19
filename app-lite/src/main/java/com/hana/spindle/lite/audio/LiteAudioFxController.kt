package com.hana.spindle.lite.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer

/**
 * Lightweight Hardware Audio DSP Controller for Spindle Lite.
 * Utilizes native Android Equalizer and BassBoost APIs with zero heap allocations during playback.
 * Compatible with Android 4.4 KitKat (API 19) hardware Stagefright engine.
 */
class LiteAudioFxController(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("spindle_lite_eq_prefs", Context.MODE_PRIVATE)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    private var minLevelMilliBels: Short = -1200 // -12 dB
    private var maxLevelMilliBels: Short = 1200  // +12 dB
    var numberOfBands: Int = 5
        private set

    val bandFrequenciesHz = IntArray(5) { 0 }
    val bandGainsDb = FloatArray(5) { 0f }

    var bassBoostStrength: Int = 0 // 0 to 1000
        private set

    var currentPreset: String = "FLAT"
        private set

    var isEnabled: Boolean = true
        private set

    init {
        // Load saved state from SharedPreferences
        currentPreset = prefs.getString("eq_preset", "FLAT") ?: "FLAT"
        bassBoostStrength = prefs.getInt("bass_boost_strength", 0)
        isEnabled = prefs.getBoolean("eq_enabled", true)
        for (i in 0 until 5) {
            bandGainsDb[i] = prefs.getFloat("band_gain_$i", 0f)
        }
    }

    /**
     * Attaches audio effects to the active MediaPlayer audio session (or global session 0).
     */
    fun attachSession(audioSessionId: Int) {
        release()
        try {
            val eq = Equalizer(0, audioSessionId)
            eq.enabled = isEnabled
            this.numberOfBands = eq.numberOfBands.toInt().coerceAtMost(5)
            val range = eq.bandLevelRange
            if (range.size >= 2) {
                minLevelMilliBels = range[0]
                maxLevelMilliBels = range[1]
            }
            for (b in 0 until this.numberOfBands) {
                val centerFreq = eq.getCenterFreq(b.toShort()) / 1000 // Convert mHz to Hz
                bandFrequenciesHz[b] = centerFreq
            }
            equalizer = eq

            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = isEnabled
                if (enabled) {
                    try {
                        setStrength(bassBoostStrength.toShort())
                    } catch (e: Exception) {
                        // Safe fallback for uncalibrated hardware
                    }
                }
            }

            // Apply loaded gain values
            syncHardwareBands()
        } catch (e: Exception) {
            // Hardware DSP may be unavailable or restricted on certain devices
            equalizer = null
            bassBoost = null
        }
    }

    /**
     * Sets individual band gain in dB (-12 dB to +12 dB).
     */
    fun setBandGainDb(band: Int, gainDb: Float) {
        if (band !in bandGainsDb.indices) return
        bandGainsDb[band] = gainDb.coerceIn(-12f, 12f)
        currentPreset = "CUSTOM"
        saveBandGain(band, bandGainsDb[band])

        val milliBels = (bandGainsDb[band] * 100f).toInt().toShort()
            .coerceIn(minLevelMilliBels, maxLevelMilliBels)

        try {
            equalizer?.setBandLevel(band.toShort(), milliBels)
        } catch (e: Exception) {
            // ignore
        }
    }

    /**
     * Sets Bass Boost level (0 to 1000).
     */
    fun setBassBoost(strength: Int) {
        bassBoostStrength = strength.coerceIn(0, 1000)
        prefs.edit().putInt("bass_boost_strength", bassBoostStrength).apply()
        try {
            bassBoost?.setStrength(bassBoostStrength.toShort())
        } catch (e: Exception) {
            // ignore
        }
    }

    /**
     * Toggles Equalizer & BassBoost DSP on/off.
     */
    fun setEnabledState(enabled: Boolean) {
        isEnabled = enabled
        prefs.edit().putBoolean("eq_enabled", enabled).apply()
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
        } catch (e: Exception) {
            // ignore
        }
    }

    /**
     * Applies a built-in audiophile DSP preset curve.
     */
    fun applyPreset(presetName: String) {
        currentPreset = presetName
        prefs.edit().putString("eq_preset", presetName).apply()

        when (presetName.uppercase()) {
            "FLAT" -> {
                setAllGains(0f, 0f, 0f, 0f, 0f)
                setBassBoost(0)
            }
            "BASS+" -> {
                setAllGains(6f, 4f, 1f, 0f, 0f)
                setBassBoost(600)
            }
            "VOCAL" -> {
                setAllGains(-1f, 2f, 4f, 3f, 1f)
                setBassBoost(0)
            }
            "ROCK" -> {
                setAllGains(4f, 2f, -1f, 3f, 5f)
                setBassBoost(350)
            }
            "TAPE" -> {
                setAllGains(3f, 2f, 1f, -1f, -3f)
                setBassBoost(250)
            }
        }
    }

    private fun setAllGains(b0: Float, b1: Float, b2: Float, b3: Float, b4: Float) {
        bandGainsDb[0] = b0
        bandGainsDb[1] = b1
        bandGainsDb[2] = b2
        bandGainsDb[3] = b3
        bandGainsDb[4] = b4
        for (i in 0 until 5) {
            saveBandGain(i, bandGainsDb[i])
        }
        syncHardwareBands()
    }

    private fun syncHardwareBands() {
        val eq = equalizer ?: return
        for (b in 0 until numberOfBands) {
            val mB = (bandGainsDb[b] * 100f).toInt().toShort()
                .coerceIn(minLevelMilliBels, maxLevelMilliBels)
            try {
                eq.setBandLevel(b.toShort(), mB)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun saveBandGain(band: Int, gainDb: Float) {
        prefs.edit().putFloat("band_gain_$band", gainDb).apply()
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (e: Exception) {
            // ignore
        }
        try {
            bassBoost?.release()
        } catch (e: Exception) {
            // ignore
        }
        equalizer = null
        bassBoost = null
    }
}
