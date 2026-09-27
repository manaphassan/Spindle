package com.hana.spindle.remote

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Primary Remote Transport Manager for Spindle DAP Launcher.
 * Coordinates SSDP network discovery, active device binding, and bidirectional
 * transport telemetry synchronization with UPnP/DLNA renderers and Volumio streamers.
 */
class RemoteTransportManager(
    private val context: Context,
    private val ssdpDiscovery: SsdpDiscovery = SsdpDiscovery(context),
    private val upnpClient: UpnpSoapClient = UpnpSoapClient(),
    private val volumioClient: VolumioRestClient = VolumioRestClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {

    companion object {
        private const val TAG = "RemoteTransportManager"
        private const val PREFS_NAME = "spindle_remote_prefs"
        private const val KEY_LAST_DEVICE_ID = "last_remote_device_id"
        private const val KEY_LAST_DEVICE_IP = "last_remote_device_ip"
        private const val KEY_LAST_DEVICE_NAME = "last_remote_device_name"
        private const val KEY_LAST_DEVICE_TYPE = "last_remote_device_type"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _discoveredDevices = MutableStateFlow<List<RemoteDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<RemoteDevice>> = _discoveredDevices.asStateFlow()

    private val _activeDevice = MutableStateFlow<RemoteDevice?>(null)
    val activeDevice: StateFlow<RemoteDevice?> = _activeDevice.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _transportStatus = MutableStateFlow(RemoteTransportStatus())
    val transportStatus: StateFlow<RemoteTransportStatus> = _transportStatus.asStateFlow()

    private var pollingJob: Job? = null

    val isRemoteActive: Boolean
        get() = _activeDevice.value != null

    fun startDiscovery(onComplete: ((List<RemoteDevice>) -> Unit)? = null) {
        if (_isScanning.value) return

        scope.launch {
            _isScanning.value = true
            try {
                val devices = ssdpDiscovery.discoverStreamers()
                _discoveredDevices.value = devices
                onComplete?.invoke(devices)
            } catch (e: Exception) {
                Log.w(TAG, "Discovery failed: ${e.message}")
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun connectDevice(device: RemoteDevice) {
        if (_activeDevice.value?.id == device.id) {
            startPollingTelemetry()
            return
        }
        _activeDevice.value = device
        prefs.edit()
            .putString(KEY_LAST_DEVICE_ID, device.id)
            .putString(KEY_LAST_DEVICE_IP, device.ip)
            .putString(KEY_LAST_DEVICE_NAME, device.name)
            .putString(KEY_LAST_DEVICE_TYPE, device.type.name)
            .apply()

        startPollingTelemetry()
    }

    fun disconnect() {
        _activeDevice.value = null
        stopPollingTelemetry()
        _transportStatus.value = RemoteTransportStatus()
    }

    fun play() {
        val device = _activeDevice.value ?: return
        scope.launch(Dispatchers.IO) {
            when (device.type) {
                RemoteDeviceType.VOLUMIO -> volumioClient.play(device)
                RemoteDeviceType.UPNP_DLNA -> upnpClient.play(device)
            }
            refreshTelemetryNow()
        }
    }

    fun pause() {
        val device = _activeDevice.value ?: return
        scope.launch(Dispatchers.IO) {
            when (device.type) {
                RemoteDeviceType.VOLUMIO -> volumioClient.pause(device)
                RemoteDeviceType.UPNP_DLNA -> upnpClient.pause(device)
            }
            refreshTelemetryNow()
        }
    }

    fun togglePlayPause() {
        if (_transportStatus.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun stop() {
        val device = _activeDevice.value ?: return
        scope.launch(Dispatchers.IO) {
            when (device.type) {
                RemoteDeviceType.VOLUMIO -> volumioClient.stop(device)
                RemoteDeviceType.UPNP_DLNA -> upnpClient.stop(device)
            }
            refreshTelemetryNow()
        }
    }

    fun next() {
        val device = _activeDevice.value ?: return
        scope.launch(Dispatchers.IO) {
            when (device.type) {
                RemoteDeviceType.VOLUMIO -> volumioClient.next(device)
                RemoteDeviceType.UPNP_DLNA -> upnpClient.next(device)
            }
            delay(300)
            refreshTelemetryNow()
        }
    }

    fun previous() {
        val device = _activeDevice.value ?: return
        scope.launch(Dispatchers.IO) {
            when (device.type) {
                RemoteDeviceType.VOLUMIO -> volumioClient.previous(device)
                RemoteDeviceType.UPNP_DLNA -> upnpClient.previous(device)
            }
            delay(300)
            refreshTelemetryNow()
        }
    }

    fun seek(positionMs: Long) {
        val device = _activeDevice.value ?: return
        scope.launch(Dispatchers.IO) {
            when (device.type) {
                RemoteDeviceType.VOLUMIO -> volumioClient.seek(device, positionMs)
                RemoteDeviceType.UPNP_DLNA -> upnpClient.seek(device, positionMs)
            }
            refreshTelemetryNow()
        }
    }

    fun setVolume(volume: Int) {
        val device = _activeDevice.value ?: return
        scope.launch(Dispatchers.IO) {
            when (device.type) {
                RemoteDeviceType.VOLUMIO -> volumioClient.setVolume(device, volume)
                RemoteDeviceType.UPNP_DLNA -> upnpClient.setVolume(device, volume)
            }
            _transportStatus.value = _transportStatus.value.copy(volume = volume.coerceIn(0, 100))
        }
    }

    fun startPollingTelemetry() {
        pollingJob?.cancel()
        pollingJob = scope.launch(Dispatchers.IO) {
            while (isActive && _activeDevice.value != null) {
                refreshTelemetryNow()
                delay(1500)
            }
        }
    }

    fun stopPollingTelemetry() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun refreshTelemetryNow() {
        val device = _activeDevice.value ?: return
        try {
            when (device.type) {
                RemoteDeviceType.VOLUMIO -> {
                    val status = volumioClient.getState(device)
                    if (status != null) {
                        _transportStatus.value = status
                    }
                }
                RemoteDeviceType.UPNP_DLNA -> {
                    val state = upnpClient.getTransportInfo(device)
                    val posInfo = upnpClient.getPositionInfo(device)
                    val volume = upnpClient.getVolume(device)

                    _transportStatus.value = RemoteTransportStatus(
                        state = state,
                        title = posInfo.title,
                        artist = posInfo.artist,
                        durationMs = posInfo.durationMs,
                        positionMs = posInfo.positionMs,
                        volume = volume
                    )
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Telemetry poll notice: ${e.message}")
        }
    }
}
