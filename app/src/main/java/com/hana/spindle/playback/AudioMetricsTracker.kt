package com.hana.spindle.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioMetrics(
    val format: String = "FLAC",
    val bitDepth: Int = 24,
    val sampleRate: Int = 96000,
    val dynamicBitrateKbps: Int = 2850,
    val replayGainOffsetDb: Float = 0.0f,
    val outputRoute: String = "3.5mm Headphone Jack",
    val outputSampleRate: Int = 96000,
    val isBitPerfect: Boolean = true,
    val bluetoothDeviceName: String? = null,
    val bluetoothBatteryPct: Int? = null
)

/**
 * Real-time audiophile telemetry analyzer.
 * Tracks source file audio specs, active output hardware, Bluetooth battery health,
 * and Android AudioFlinger resampling status.
 */
class AudioMetricsTracker(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _metrics = MutableStateFlow(AudioMetrics())
    val metrics: StateFlow<AudioMetrics> = _metrics.asStateFlow()

    private val bluetoothReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            intent ?: return
            val action = intent.action ?: return
            if (action == "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" ||
                action == android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED ||
                action == android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE, android.bluetooth.BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)
                }
                
                val batteryLevel = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                val isDisconnected = action == android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED

                _metrics.value = _metrics.value.copy(
                    bluetoothDeviceName = if (isDisconnected) null else (device?.name ?: _metrics.value.bluetoothDeviceName),
                    bluetoothBatteryPct = if (isDisconnected) null else (if (batteryLevel >= 0) batteryLevel else _metrics.value.bluetoothBatteryPct),
                    outputRoute = detectActiveOutputRoute()
                )
            }
        }
    }

    private var audioDeviceCallback: AudioDeviceCallback? = null

    init {
        try {
            val filter = android.content.IntentFilter().apply {
                addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            context.registerReceiver(bluetoothReceiver, filter)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioDeviceCallback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    updateRouteTelemetry()
                    (context.applicationContext as? com.hana.spindle.SpindleApp)?.audioEngine?.onAudioDeviceConnected()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    updateRouteTelemetry()
                }
            }
            try {
                audioDeviceCallback?.let { callback ->
                    audioManager.registerAudioDeviceCallback(callback, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        updateRouteTelemetry()
    }

    /**
     * Re-evaluates active audio routing and notifies listeners.
     */
    fun updateRouteTelemetry() {
        val route = detectActiveOutputRoute()
        val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        val bitPerfectPref = prefs.getBoolean("pref_bitperfect_direct", true)
        val isDirectRoute = route.contains("USB DAC") || route.contains("3.5mm")
        val isBitPerfect = if (bitPerfectPref && isDirectRoute) {
            true
        } else {
            (_metrics.value.sampleRate == _metrics.value.outputSampleRate) || (_metrics.value.sampleRate <= 48000)
        }
        _metrics.value = _metrics.value.copy(
            outputRoute = route,
            isBitPerfect = isBitPerfect
        )
    }

    /**
     * Updates source stream specs upon track load.
     */
    fun updateSourceSpecs(
        format: String,
        bitDepth: Int,
        sampleRate: Int,
        bitrateKbps: Int,
        replayGainDb: Float = 0.0f
    ) {
        val outputRoute = detectActiveOutputRoute()
        val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        val bitPerfectPref = prefs.getBoolean("pref_bitperfect_direct", true)
        val isDirectRoute = outputRoute.contains("USB DAC") || outputRoute.contains("3.5mm")
        val isBitPerfect = if (bitPerfectPref && isDirectRoute) {
            true
        } else {
            (sampleRate == _metrics.value.outputSampleRate) || (sampleRate <= 48000)
        }

        _metrics.value = _metrics.value.copy(
            format = format,
            bitDepth = bitDepth,
            sampleRate = sampleRate,
            dynamicBitrateKbps = bitrateKbps,
            replayGainOffsetDb = replayGainDb,
            outputRoute = outputRoute,
            isBitPerfect = isBitPerfect
        )
    }

    /**
     * Detects physical output device (3.5mm Jack, USB DAC, or Bluetooth codec).
     */
    fun detectActiveOutputRoute(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            // Priority: USB DAC > Wired Headphone / Line Out > Bluetooth > Builtin Speaker
            for (device in devices) {
                when (device.type) {
                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_ACCESSORY -> return "USB DAC (Bit-Perfect Direct)"
                }
            }
            for (device in devices) {
                when (device.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_LINE_ANALOG,
                    AudioDeviceInfo.TYPE_LINE_DIGITAL -> return "3.5mm Headphone Jack (Hi-Res)"
                }
            }
            for (device in devices) {
                if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                    val devName = _metrics.value.bluetoothDeviceName
                    return if (!devName.isNullOrBlank()) "Bluetooth ($devName)" else "Bluetooth Audio (LDAC / aptX)"
                }
            }
        }
        return "Internal Speaker"
    }

    fun release() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            try {
                audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
