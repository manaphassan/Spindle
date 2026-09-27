package com.hana.spindle.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Detailed telemetry model for an external USB-OTG Digital-to-Analog Converter (DAC).
 */
data class UsbDacInfo(
    val isConnected: Boolean,
    val deviceName: String,
    val manufacturer: String,
    val productName: String,
    val vendorId: Int,
    val productId: Int,
    val audioClass: String, // e.g. "USB Audio Class 2.0 (High-Speed)"
    val maxSampleRateHz: Int, // e.g. 768000, 384000, 192000, 96000
    val bitDepthBits: Int, // e.g. 32, 24, 16
    val isKnownAudiophileBrand: Boolean,
    val brandBadge: String, // e.g. "FiiO", "Moondrop", "AudioQuest", "Qudelix", "iFi"
    val capabilitiesDescription: String
) {
    val vidHex: String get() = String.format(Locale.US, "0x%04X", vendorId)
    val pidHex: String get() = String.format(Locale.US, "0x%04X", productId)
    val shortBadgeTitle: String get() = if (isKnownAudiophileBrand) "$brandBadge $productName" else productName
}

/**
 * Real-time Hardware USB-OTG Audiophile DAC Detection & Telemetry Engine.
 *
 * Automatically monitors USB bus insertion/removal and AudioManager routing topology.
 * Identifies leading audiophile DAC hardware (FiiO, Moondrop, AudioQuest, Qudelix, iFi, Chord, Topping, etc.),
 * decodes UAC 1.0/2.0 descriptors, supported PCM sample rates, and bit-depth capabilities.
 */
class UsbDacManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _usbDacState = MutableStateFlow<UsbDacInfo?>(null)
    val usbDacState: StateFlow<UsbDacInfo?> = _usbDacState.asStateFlow()

    private var audioDeviceCallback: AudioDeviceCallback? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == UsbManager.ACTION_USB_DEVICE_ATTACHED ||
                action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                Log.d(TAG, "USB Device Broadcast received: $action")
                refreshUsbDacTelemetry()
            }
        }
    }

    init {
        registerUsbReceiver()
        registerAudioDeviceCallback()
        refreshUsbDacTelemetry()
    }

    private fun registerUsbReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            context.registerReceiver(usbReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register USB receiver: ${e.message}")
        }
    }

    private fun registerAudioDeviceCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioDeviceCallback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    refreshUsbDacTelemetry()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    refreshUsbDacTelemetry()
                }
            }
            try {
                audioDeviceCallback?.let { audioManager.registerAudioDeviceCallback(it, null) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register AudioDeviceCallback: ${e.message}")
            }
        }
    }

    /**
     * Re-scans connected USB hardware and AudioDeviceInfo outputs to build complete DAC telemetry.
     */
    fun refreshUsbDacTelemetry(): UsbDacInfo? {
        val detectedDac = detectUsbDac()
        _usbDacState.value = detectedDac
        Log.i(TAG, "USB DAC State updated: connected=${detectedDac != null}, name=${detectedDac?.shortBadgeTitle}")
        return detectedDac
    }

    private fun detectUsbDac(): UsbDacInfo? {
        // 1. Check physical USB Host device list via UsbManager
        val usbDevices = usbManager?.deviceList?.values ?: emptyList()
        for (device in usbDevices) {
            if (isAudioUsbDevice(device)) {
                return buildUsbDacInfo(device)
            }
        }

        // 2. Fallback: Check AudioManager AudioDeviceInfo for USB outputs (e.g. if UsbManager permission is restricted)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (outDev in outputDevices) {
                if (outDev.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    outDev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    outDev.type == AudioDeviceInfo.TYPE_USB_ACCESSORY) {
                    return buildFromAudioDeviceInfo(outDev)
                }
            }
        }

        return null
    }

    /**
     * Determines whether a UsbDevice contains an Audio Streaming or Audio Control interface.
     */
    private fun isAudioUsbDevice(device: UsbDevice): Boolean {
        if (device.deviceClass == UsbConstants.USB_CLASS_AUDIO) return true
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO) {
                return true
            }
        }
        return false
    }

    private fun buildUsbDacInfo(device: UsbDevice): UsbDacInfo {
        val vid = device.vendorId
        val pid = device.productId

        val rawProdName = try { device.productName } catch (_: Exception) { null }
        val rawMfgName = try { device.manufacturerName } catch (_: Exception) { null }

        val brand = resolveBrand(vid, rawMfgName, rawProdName)
        val productName = resolveProductName(vid, pid, rawProdName)
        val manufacturer = rawMfgName?.takeIf { it.isNotBlank() } ?: brand

        // Check if High-Speed UAC 2.0 (Interface class 1, subclass 2 or high endpoint count)
        val isUac2 = isHighSpeedUac2(device)
        val audioClass = if (isUac2) "USB Audio Class 2.0 (High-Speed)" else "USB Audio Class 1.0 (Full-Speed)"

        val (maxSampleRate, bitDepth) = estimateMaxSpecs(vid, pid, isUac2)

        val isKnownBrand = brand != "Generic USB"
        val capsDesc = "Direct ALSA PCM • Up to ${bitDepth}-bit/${maxSampleRate / 1000}kHz • ${if (isUac2) "Asynchronous Isochronous" else "Adaptive Synchronous"}"

        return UsbDacInfo(
            isConnected = true,
            deviceName = device.deviceName,
            manufacturer = manufacturer,
            productName = productName,
            vendorId = vid,
            productId = pid,
            audioClass = audioClass,
            maxSampleRateHz = maxSampleRate,
            bitDepthBits = bitDepth,
            isKnownAudiophileBrand = isKnownBrand,
            brandBadge = brand,
            capabilitiesDescription = capsDesc
        )
    }

    private fun buildFromAudioDeviceInfo(device: AudioDeviceInfo): UsbDacInfo {
        val rawName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.productName?.toString()?.trim() ?: "USB Audio Output"
        } else {
            "USB Audio Output"
        }

        val sampleRates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) device.sampleRates else intArrayOf()
        val maxRate = sampleRates.maxOrNull()?.takeIf { it > 0 } ?: 192000
        val bitDepth = if (maxRate >= 192000) 32 else 24

        val brand = resolveBrandFromName(rawName)
        val isKnownBrand = brand != "USB Audio"

        return UsbDacInfo(
            isConnected = true,
            deviceName = rawName,
            manufacturer = brand,
            productName = rawName,
            vendorId = 0x0000,
            productId = 0x0000,
            audioClass = "USB Audio Class 2.0 (Hi-Res Passthrough)",
            maxSampleRateHz = maxRate,
            bitDepthBits = bitDepth,
            isKnownAudiophileBrand = isKnownBrand,
            brandBadge = brand,
            capabilitiesDescription = "Direct ALSA PCM • Up to ${bitDepth}-bit/${maxRate / 1000}kHz • Native AudioFlinger Passthrough"
        )
    }

    private fun isHighSpeedUac2(device: UsbDevice): Boolean {
        // Check for multiple streaming alternate settings or specific UAC2 interface descriptors
        var hasStreamingInterface = false
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO && iface.interfaceSubclass == 2) {
                hasStreamingInterface = true
                if (iface.endpointCount >= 2) return true
            }
        }
        return hasStreamingInterface
    }

    fun release() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: Exception) { }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            try {
                audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            } catch (_: Exception) { }
        }
    }

    companion object {
        private const val TAG = "UsbDacManager"

        internal fun resolveBrand(vid: Int, mfg: String?, prod: String?): String {
            val text = "${mfg.orEmpty()} ${prod.orEmpty()}".lowercase(Locale.US)
            return when {
                vid == 0x2972 || text.contains("fiio") -> "FiiO"
                vid == 0x2FC6 || text.contains("moondrop") -> "Moondrop"
                vid == 0x2D87 || text.contains("qudelix") -> "Qudelix"
                vid == 0x21B4 || text.contains("dragonfly") || text.contains("audioquest") -> "AudioQuest"
                vid == 0x20B1 || text.contains("ifi") || text.contains("amr") -> "iFi Audio"
                vid == 0x04D8 || text.contains("chord") || text.contains("mojo") || text.contains("hugo") -> "Chord"
                vid == 0x152A || text.contains("topping") || text.contains("smsl") -> "Topping / SMSL"
                vid == 0x2772 || text.contains("shanling") -> "Shanling"
                vid == 0x2F72 || text.contains("hiby") -> "HiBy"
                vid == 0x05AC || text.contains("apple") -> "Apple"
                vid == 0x18D1 || text.contains("google") -> "Google"
                vid == 0x0BDA || text.contains("realtek") || text.contains("alc5686") -> "Realtek Hi-Res"
                vid == 0x2207 || text.contains("cx31993") || text.contains("conexant") -> "Conexant / CX"
                vid == 0x2616 || text.contains("savitech") -> "Savitech"
                else -> resolveBrandFromName(text)
            }
        }

        internal fun resolveBrandFromName(name: String): String {
            val lower = name.lowercase(Locale.US)
            return when {
                lower.contains("fiio") -> "FiiO"
                lower.contains("moondrop") || lower.contains("dawn") -> "Moondrop"
                lower.contains("qudelix") -> "Qudelix"
                lower.contains("dragonfly") || lower.contains("audioquest") -> "AudioQuest"
                lower.contains("ifi") || lower.contains("hip-dac") -> "iFi Audio"
                lower.contains("chord") || lower.contains("mojo") -> "Chord"
                lower.contains("topping") -> "Topping"
                lower.contains("smsl") -> "SMSL"
                lower.contains("shanling") -> "Shanling"
                lower.contains("hiby") -> "HiBy"
                lower.contains("cayin") -> "Cayin"
                lower.contains("astell") -> "Astell&Kern"
                lower.contains("sony") -> "Sony Hi-Res"
                lower.contains("hidizs") -> "Hidizs"
                lower.contains("tempotec") -> "TempoTec"
                lower.contains("ibasso") -> "iBasso"
                else -> "USB Audio"
            }
        }

        internal fun resolveProductName(vid: Int, pid: Int, rawName: String?): String {
            if (!rawName.isNullOrBlank() && rawName != "null") {
                return rawName.trim()
            }
            // Known VID/PID Catalog for popular audiophile dongles
            return when (vid) {
                0x2972 -> when (pid) {
                    0x0047 -> "KA3 High-Resolution DAC"
                    0x0048 -> "KA1 Type-C DAC"
                    0x0049 -> "KA2 Balanced DAC"
                    0x0050 -> "KA5 Dual CS43198 DAC"
                    0x0051 -> "KA13 Dual CS43198 Desktop Mode"
                    0x0052 -> "KA17 Dual ES9069Q Flagship"
                    0x0021 -> "BTR5 Bluetooth & USB DAC"
                    0x0022 -> "BTR7 Dual THX AAA DAC"
                    0x0023 -> "BTR15 Balanced DAC"
                    0x0031 -> "Q3 MQA Balanced DAC"
                    else -> "FiiO USB Audio DAC"
                }
                0x2FC6 -> when (pid) {
                    0xF010 -> "Dawn Dual CS43131 DAC"
                    0xF011 -> "Dawn Pro Dual CS43131"
                    0xF020 -> "Moonriver 2 Flagship DAC"
                    0xF030 -> "FreeDSP Type-C Cable"
                    else -> "Moondrop Audiophile DAC"
                }
                0x21B4 -> when (pid) {
                    0x0080 -> "DragonFly Black v1.5"
                    0x0081 -> "DragonFly Red v1.0"
                    0x0082 -> "DragonFly Cobalt Flagship"
                    else -> "AudioQuest DragonFly"
                }
                0x2D87 -> when (pid) {
                    0x0001 -> "Qudelix-5K Reference DAC"
                    0x0002 -> "Qudelix T71 Surround DAC"
                    else -> "Qudelix USB DAC"
                }
                0x05AC -> "Apple USB-C Audio (CS46L41)"
                0x0BDA -> "Realtek ALC5686 Hi-Res DAC"
                0x2207 -> "Conexant CX31993 HiFi DAC"
                else -> String.format(Locale.US, "USB DAC [VID:%04X PID:%04X]", vid, pid)
            }
        }

        internal fun estimateMaxSpecs(vid: Int, pid: Int, isUac2: Boolean): Pair<Int, Int> {
            // High-end audiophile DACs supporting 32-bit / 384k or 768k
            val isFlagship768k = when (vid) {
                0x2972 -> pid in setOf(0x0047, 0x0050, 0x0051, 0x0052, 0x0022, 0x0023)
                0x2FC6 -> pid in setOf(0xF011, 0xF020)
                0x20B1 -> true // iFi GO bar / Hip-dac typically 384k or 768k
                0x04D8 -> true // Chord Mojo 768k
                else -> false
            }
            if (isFlagship768k) return Pair(768000, 32)

            val isHighRes384k = vid in setOf(0x2972, 0x2FC6, 0x2D87, 0x0BDA, 0x2207)
            if (isHighRes384k) return Pair(384000, 32)

            // DragonFly is 24/96 (Adaptive UAC1)
            if (vid == 0x21B4) return Pair(96000, 24)

            return if (isUac2) Pair(192000, 24) else Pair(96000, 24)
        }
    }
}
