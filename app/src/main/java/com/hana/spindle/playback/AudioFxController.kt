package com.hana.spindle.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer

/**
 * Audiophile Hardware Audio DSP Controller.
 * Manages Android Equalizer and BassBoost attached to ExoPlayer audio session ID.
 */
class AudioFxController {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    private var minBandLevel: Short = -1500 // -15 dB in millibels
    private var maxBandLevel: Short = 1500  // +15 dB in millibels
    private var numBands: Short = 5

    // Cached gains (-12.0f to +12.0f dB)
    var lowGainDb: Float = 0.0f
        private set
    var midGainDb: Float = 0.0f
        private set
    var highGainDb: Float = 0.0f
        private set
    var bassBoostStrength: Int = 0 // 0 to 1000
        private set

    fun attachSession(audioSessionId: Int) {
        release()
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
                numBands = numberOfBands
                val range = bandLevelRange
                if (range.size >= 2) {
                    minBandLevel = range[0]
                    maxBandLevel = range[1]
                }
            }

            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
            }

            // Re-apply cached settings
            applyEq()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setLowGain(gainDb: Float) {
        lowGainDb = gainDb.coerceIn(-12.0f, 12.0f)
        applyEq()
    }

    fun setMidGain(gainDb: Float) {
        midGainDb = gainDb.coerceIn(-12.0f, 12.0f)
        applyEq()
    }

    fun setHighGain(gainDb: Float) {
        highGainDb = gainDb.coerceIn(-12.0f, 12.0f)
        applyEq()
    }

    fun setFilterStrength(strength: Int) { // 0 to 1000
        bassBoostStrength = strength.coerceIn(0, 1000)
        try {
            bassBoost?.setStrength(bassBoostStrength.toShort())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun applyPreset(preset: String) {
        when (preset.uppercase()) {
            "FLAT" -> {
                lowGainDb = 0f
                midGainDb = 0f
                highGainDb = 0f
                setFilterStrength(0)
            }
            "BASS_BOOST" -> {
                lowGainDb = 6.0f
                midGainDb = 0.5f
                highGainDb = -1.0f
                setFilterStrength(600)
            }
            "HARMAN" -> {
                lowGainDb = 4.5f
                midGainDb = -1.0f
                highGainDb = 2.5f
                setFilterStrength(300)
            }
            "VOCAL" -> {
                lowGainDb = -2.0f
                midGainDb = 5.0f
                highGainDb = 2.0f
                setFilterStrength(0)
            }
            "CLUB" -> {
                lowGainDb = 7.0f
                midGainDb = 2.0f
                highGainDb = 5.0f
                setFilterStrength(750)
            }
        }
        applyEq()
    }

    private fun applyEq() {
        val eq = equalizer ?: return
        try {
            val totalBands = numBands.toInt()
            if (totalBands <= 0) return

            for (i in 0 until totalBands) {
                // Approximate band frequency mapping
                val gainDb = when {
                    i == 0 -> lowGainDb
                    i < totalBands / 2 -> (lowGainDb + midGainDb) / 2f
                    i == totalBands / 2 -> midGainDb
                    i < totalBands - 1 -> (midGainDb + highGainDb) / 2f
                    else -> highGainDb
                }

                val millibels = (gainDb * 100).toInt().coerceIn(minBandLevel.toInt(), maxBandLevel.toInt()).toShort()
                eq.setBandLevel(i.toShort(), millibels)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        equalizer = null
        bassBoost = null
    }
}
