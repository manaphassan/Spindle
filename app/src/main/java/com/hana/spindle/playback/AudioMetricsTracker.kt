package com.hana.spindle.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

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
    val bluetoothBatteryPct: Int? = null,
    val isBluetoothConnected: Boolean = false,
    val bluetoothConnectionStatus: String = "DISCONNECTED",
    val jackType: String? = null,
    val hasMic: Boolean = false,
    val jackCapabilities: String? = null
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

                val intentBattery = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                val isDisconnected = action == android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED

                val reflectedBattery = if (!isDisconnected && device != null) {
                    try {
                        val m = device.javaClass.getMethod("getBatteryLevel")
                        val res = m.invoke(device) as? Int
                        if (res != null && res >= 0) res else null
                    } catch (_: Exception) {
                        null
                    }
                } else null

                val btName = if (isDisconnected) {
                    null
                } else {
                    val rawName = try { device?.name } catch (_: SecurityException) { null }
                    rawName?.takeIf { it.isNotBlank() } ?: queryConnectedBluetoothDeviceName() ?: _metrics.value.bluetoothDeviceName
                }

                val btBattery = if (isDisconnected) {
                    null
                } else {
                    when {
                        intentBattery >= 0 -> intentBattery
                        reflectedBattery != null -> reflectedBattery
                        else -> _metrics.value.bluetoothBatteryPct
                    }
                }

                val isConnected = !isDisconnected && (btName != null || hasConnectedBluetoothAudioDevice())
                val connectionStatus = if (isConnected) "CONNECTED" else "DISCONNECTED"

                val jackInfo = inspectHeadphoneJack()

                _metrics.value = _metrics.value.copy(
                    bluetoothDeviceName = if (isConnected) btName else null,
                    bluetoothBatteryPct = if (isConnected) btBattery else null,
                    isBluetoothConnected = isConnected,
                    bluetoothConnectionStatus = connectionStatus,
                    outputRoute = detectActiveOutputRoute(),
                    jackType = jackInfo.jackType,
                    hasMic = jackInfo.hasMic,
                    jackCapabilities = jackInfo.capabilities
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
     * Inspects 3.5mm Headphone Jack connection topology (TRS vs TRRS vs Line-Out)
     * and hardware DAC capabilities.
     */
    fun inspectHeadphoneJack(): JackInfo {
        var detectedType: String? = null
        var hasMic = false
        var sampleRates: IntArray? = null

        // 1. Check kernel switch state if available (Android DAP hardware standard)
        val h2wState = readH2wSwitchState()
        when (h2wState) {
            1 -> {
                detectedType = "4-Pole TRRS (Headset + Mic)"
                hasMic = true
            }
            2 -> {
                detectedType = "3-Pole TRS (Stereo Output)"
                hasMic = false
            }
            4 -> {
                detectedType = "Line-Out / Pure Analog Out"
                hasMic = false
            }
        }

        // 2. Query AudioManager for audio devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                when (device.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADSET -> {
                        if (detectedType == null) detectedType = "4-Pole TRRS (Headset + Mic)"
                        hasMic = true
                        if (device.sampleRates.isNotEmpty()) sampleRates = device.sampleRates
                    }
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                        if (detectedType == null) detectedType = "3-Pole TRS (Stereo Output)"
                        if (device.sampleRates.isNotEmpty()) sampleRates = device.sampleRates
                    }
                    AudioDeviceInfo.TYPE_LINE_ANALOG,
                    AudioDeviceInfo.TYPE_LINE_DIGITAL -> {
                        if (detectedType == null) detectedType = "Line-Out / Pure Analog Out"
                        if (device.sampleRates.isNotEmpty()) sampleRates = device.sampleRates
                    }
                }
            }
        }

        if (detectedType == null) {
            return JackInfo(null, false, null)
        }

        val maxRate = sampleRates?.maxOrNull()
        val rateText = if (maxRate != null && maxRate > 0) "${maxRate / 1000} kHz" else "192 / 384 kHz"
        val capabilities = "Direct ALSA DAC • 16-32bit / up to $rateText • ${if (hasMic) "TRRS Mic Line" else "Pure Audio Ground"}"

        return JackInfo(detectedType, hasMic, capabilities)
    }

    private fun readH2wSwitchState(): Int? {
        return try {
            val file = File("/sys/class/switch/h2w/state")
            if (file.exists() && file.canRead()) {
                file.readText().trim().toIntOrNull()
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks if any Bluetooth audio output device is currently connected.
     */
    fun hasConnectedBluetoothAudioDevice(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            return devices.any { device ->
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    device.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
                ))
            }
        }
        return false
    }

    /**
     * Queries connected Bluetooth audio device productName directly from AudioManager.
     */
    fun queryConnectedBluetoothDeviceName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                val isBt = when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> true
                    else -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                            device.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
                        } else false
                    }
                }
                if (isBt) {
                    val name = device.productName?.toString()?.trim()
                    if (!name.isNullOrBlank() && name != "null") {
                        return name
                    }
                }
            }
        }
        return null
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

        val hasBt = hasConnectedBluetoothAudioDevice()
        val btName = if (hasBt) (_metrics.value.bluetoothDeviceName ?: queryConnectedBluetoothDeviceName()) else null
        val btConnected = hasBt || btName != null
        val btStatus = if (btConnected) "CONNECTED" else "DISCONNECTED"
        val btBattery = if (btConnected) _metrics.value.bluetoothBatteryPct else null
        val jackInfo = inspectHeadphoneJack()

        _metrics.value = _metrics.value.copy(
            outputRoute = route,
            isBitPerfect = isBitPerfect,
            bluetoothDeviceName = btName,
            bluetoothBatteryPct = btBattery,
            isBluetoothConnected = btConnected,
            bluetoothConnectionStatus = btStatus,
            jackType = jackInfo.jackType,
            hasMic = jackInfo.hasMic,
            jackCapabilities = jackInfo.capabilities
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

        val hasBt = hasConnectedBluetoothAudioDevice()
        val btName = if (hasBt) (_metrics.value.bluetoothDeviceName ?: queryConnectedBluetoothDeviceName()) else null
        val btConnected = hasBt || btName != null
        val btStatus = if (btConnected) "CONNECTED" else "DISCONNECTED"
        val btBattery = if (btConnected) _metrics.value.bluetoothBatteryPct else null
        val jackInfo = inspectHeadphoneJack()

        _metrics.value = _metrics.value.copy(
            format = format,
            bitDepth = bitDepth,
            sampleRate = sampleRate,
            dynamicBitrateKbps = bitrateKbps,
            replayGainOffsetDb = replayGainDb,
            outputRoute = outputRoute,
            isBitPerfect = isBitPerfect,
            bluetoothDeviceName = btName,
            bluetoothBatteryPct = btBattery,
            isBluetoothConnected = btConnected,
            bluetoothConnectionStatus = btStatus,
            jackType = jackInfo.jackType,
            hasMic = jackInfo.hasMic,
            jackCapabilities = jackInfo.capabilities
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
                val isBt = when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> true
                    else -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                            device.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
                        } else false
                    }
                }
                if (isBt) {
                    val devName = _metrics.value.bluetoothDeviceName ?: device.productName?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                    return if (!devName.isNullOrBlank()) "Bluetooth ($devName)" else "Bluetooth Audio (LDAC / aptX / AAC)"
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

    data class JackInfo(
        val jackType: String?,
        val hasMic: Boolean,
        val capabilities: String?
    )
}
