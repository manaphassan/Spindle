package com.hana.spindle.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import androidx.annotation.RequiresApi
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** SharedPreferences key (spindle_prefs) for the opt-in bit-perfect mixer mode. */
const val PREF_BITPERFECT_MIXER = "pref_bitperfect_mixer"

/** A PCM format the platform mixer can pass straight to a device. */
data class MixerFormat(val sampleRateHz: Int, val bitDepth: Int)

/** Pure selection rules, kept free of Android types so they can be unit tested. */
object BitPerfectMixerPolicy {
    /**
     * Picks the mixer format that carries the source untouched, or null when none can.
     * The sample rate must match exactly (a different rate means resampling). A 16-bit source
     * needs a 16-bit format; deeper sources prefer 24-bit, then 32-bit, never fewer bits.
     */
    fun select(supported: List<MixerFormat>, sourceRateHz: Int, sourceBitDepth: Int): MixerFormat? {
        val sameRate = supported.filter { it.sampleRateHz == sourceRateHz }
        if (sameRate.isEmpty()) return null
        if (sourceBitDepth <= 16) return sameRate.firstOrNull { it.bitDepth == 16 }
        return sameRate.filter { it.bitDepth >= 24 }.minByOrNull { it.bitDepth }
    }
}

enum class BitPerfectStatus { OFF, UNSUPPORTED_OS, NO_DEVICE, NO_MATCH, REJECTED, ACTIVE }

data class BitPerfectMixerState(
    val status: BitPerfectStatus = BitPerfectStatus.OFF,
    val format: MixerFormat? = null
) {
    /** True only when the platform confirmed a bit-perfect mixer path for the current output. */
    val isVerifiedActive: Boolean get() = status == BitPerfectStatus.ACTIVE
}

/**
 * Requests Android 14+ bit-perfect mixer behaviour (AudioMixerAttributes) for a wired/USB output.
 * The state is "active" only when the platform accepts the request AND reports the preferred
 * attributes back. It is never assumed.
 */
class BitPerfectMixerController(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _state = MutableStateFlow(BitPerfectMixerState())
    val state: StateFlow<BitPerfectMixerState> = _state.asStateFlow()

    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private var appliedDevice: AudioDeviceInfo? = null

    companion object {
        private const val TAG = "BitPerfectMixer"
        val isOsSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }

    /** Applies (or clears) the preferred mixer attributes for the current track. */
    fun apply(enabled: Boolean, sourceRateHz: Int, sourceBitDepth: Int) {
        if (!enabled) {
            clear()
            _state.value = BitPerfectMixerState()
            return
        }
        if (!isOsSupported) {
            _state.value = BitPerfectMixerState(BitPerfectStatus.UNSUPPORTED_OS)
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) applyApi34(sourceRateHz, sourceBitDepth)
        } catch (e: Exception) {
            Log.w(TAG, "Bit-perfect mixer request failed", e)
            _state.value = BitPerfectMixerState(BitPerfectStatus.REJECTED)
        }
    }

    fun clear() {
        if (!isOsSupported) return
        val device = appliedDevice ?: return
        try {
            audioManager.clearPreferredMixerAttributes(attrs, device)
        } catch (e: Exception) {
            Log.w(TAG, "clearPreferredMixerAttributes failed", e)
        }
        appliedDevice = null
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun applyApi34(sourceRateHz: Int, sourceBitDepth: Int) {
        val device = findOutputDevice()
        if (device == null) {
            _state.value = BitPerfectMixerState(BitPerfectStatus.NO_DEVICE)
            return
        }

        val candidates = audioManager.getSupportedMixerAttributes(device)
            .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
        val byFormat = candidates.mapNotNull { a -> toMixerFormat(a.format)?.let { it to a } }
        val chosen = BitPerfectMixerPolicy.select(byFormat.map { it.first }, sourceRateHz, sourceBitDepth)
        val attr = byFormat.firstOrNull { it.first == chosen }?.second
        if (chosen == null || attr == null) {
            clear()
            _state.value = BitPerfectMixerState(BitPerfectStatus.NO_MATCH)
            return
        }
        if (!audioManager.setPreferredMixerAttributes(attrs, device, attr)) {
            _state.value = BitPerfectMixerState(BitPerfectStatus.REJECTED)
            return
        }
        appliedDevice = device
        val reported = audioManager.getPreferredMixerAttributes(attrs, device)
        val confirmed = reported != null &&
            reported.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
            toMixerFormat(reported.format) == chosen
        _state.value = if (confirmed) {
            BitPerfectMixerState(BitPerfectStatus.ACTIVE, chosen)
        } else {
            BitPerfectMixerState(BitPerfectStatus.REJECTED)
        }
    }

    private fun findOutputDevice(): AudioDeviceInfo? {
        val outs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
            ?: outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
    }

    private fun toMixerFormat(f: AudioFormat): MixerFormat? {
        val bits = when (f.encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 16
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
            AudioFormat.ENCODING_PCM_32BIT -> 32
            else -> return null
        }
        return MixerFormat(f.sampleRate, bits)
    }
}
