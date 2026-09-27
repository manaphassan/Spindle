package com.hana.spindle.remote

/**
 * Categorization of remote audio streamer endpoints.
 */
enum class RemoteDeviceType {
    UPNP_DLNA,
    VOLUMIO
}

/**
 * Transport playback state reported by remote renderer.
 */
enum class RemotePlaybackState {
    STOPPED,
    PLAYING,
    PAUSED,
    TRANSITIONING,
    UNKNOWN
}

/**
 * Model representing a discovered UPnP/DLNA MediaRenderer or Volumio endpoint.
 */
data class RemoteDevice(
    val id: String,
    val name: String,
    val type: RemoteDeviceType,
    val ip: String,
    val port: Int,
    val locationUrl: String = "",
    val avTransportControlUrl: String = "",
    val renderingControlUrl: String = "",
    val modelName: String = "",
    val manufacturer: String = "",
    val isOnline: Boolean = true
) {
    val displaySubtitle: String
        get() = when (type) {
            RemoteDeviceType.VOLUMIO -> "Volumio Audiophile Streamer • $ip:$port"
            RemoteDeviceType.UPNP_DLNA -> {
                val mf = if (manufacturer.isNotBlank()) "$manufacturer " else ""
                val md = if (modelName.isNotBlank()) "$modelName • " else ""
                "$mf$md$ip"
            }
        }

    val typeBadge: String
        get() = when (type) {
            RemoteDeviceType.VOLUMIO -> "VOLUMIO"
            RemoteDeviceType.UPNP_DLNA -> "DLNA / UPnP"
        }
}

/**
 * Real-time telemetry snapshot of remote transport playback.
 */
data class RemoteTransportStatus(
    val state: RemotePlaybackState = RemotePlaybackState.STOPPED,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val volume: Int = 100,
    val isMuted: Boolean = false,
    val sampleRate: String = "",
    val bitDepth: String = "",
    val codec: String = ""
) {
    val isPlaying: Boolean get() = state == RemotePlaybackState.PLAYING
    val isPaused: Boolean get() = state == RemotePlaybackState.PAUSED
    val isStopped: Boolean get() = state == RemotePlaybackState.STOPPED
}
