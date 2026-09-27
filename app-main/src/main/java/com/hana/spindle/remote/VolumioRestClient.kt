package com.hana.spindle.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * REST API client for Volumio Audiophile Streamers (default HTTP port 3000).
 */
class VolumioRestClient {

    companion object {
        private const val TAG = "VolumioRestClient"
        private const val TIMEOUT_MS = 3500
    }

    suspend fun getState(device: RemoteDevice): RemoteTransportStatus? = withContext(Dispatchers.IO) {
        val endpoint = "http://${device.ip}:${device.port}/api/v1/getState"
        val response = getHttp(endpoint) ?: return@withContext null

        try {
            val json = JSONObject(response)
            val statusStr = json.optString("status", "stop").lowercase(Locale.US)
            val playbackState = when (statusStr) {
                "play" -> RemotePlaybackState.PLAYING
                "pause" -> RemotePlaybackState.PAUSED
                "stop" -> RemotePlaybackState.STOPPED
                else -> RemotePlaybackState.UNKNOWN
            }

            val title = json.optString("title", "")
            val artist = json.optString("artist", "")
            val album = json.optString("album", "")
            val seekMs = json.optLong("seek", 0L)
            val durationSec = json.optLong("duration", 0L)
            val durationMs = durationSec * 1000L
            val volume = json.optInt("volume", 100).coerceIn(0, 100)
            val isMuted = json.optBoolean("mute", false)
            val sampleRate = json.optString("samplerate", "")
            val bitDepth = json.optString("bitdepth", "")
            val codec = json.optString("trackType", "")

            RemoteTransportStatus(
                state = playbackState,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                positionMs = seekMs,
                volume = volume,
                isMuted = isMuted,
                sampleRate = sampleRate,
                bitDepth = bitDepth,
                codec = codec
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Volumio state JSON: ${e.message}")
            null
        }
    }

    suspend fun play(device: RemoteDevice): Boolean = sendCommand(device, "play")

    suspend fun pause(device: RemoteDevice): Boolean = sendCommand(device, "pause")

    suspend fun stop(device: RemoteDevice): Boolean = sendCommand(device, "stop")

    suspend fun next(device: RemoteDevice): Boolean = sendCommand(device, "next")

    suspend fun previous(device: RemoteDevice): Boolean = sendCommand(device, "prev")

    suspend fun seek(device: RemoteDevice, positionMs: Long): Boolean {
        val positionSec = (positionMs / 1000).coerceAtLeast(0L)
        return sendCommand(device, "seek&position=$positionSec")
    }

    suspend fun setVolume(device: RemoteDevice, volume: Int): Boolean {
        val targetVol = volume.coerceIn(0, 100)
        return sendCommand(device, "volume&volume=$targetVol")
    }

    suspend fun sendCommand(device: RemoteDevice, cmd: String): Boolean = withContext(Dispatchers.IO) {
        val endpoint = "http://${device.ip}:${device.port}/api/v1/commands/?cmd=$cmd"
        val response = getHttp(endpoint)
        response != null
    }

    suspend fun probeEndpoint(ip: String, port: Int = 3000): RemoteDevice? = withContext(Dispatchers.IO) {
        val endpoint = "http://$ip:$port/api/v1/getState"
        val response = getHttp(endpoint) ?: return@withContext null

        try {
            val json = JSONObject(response)
            // If response has "status" or "volume", it is a confirmed Volumio endpoint
            if (json.has("status") || json.has("volume")) {
                val systemInfoResponse = getHttp("http://$ip:$port/api/v1/getSystemInfo")
                var deviceName = "Volumio Streamer"
                if (systemInfoResponse != null) {
                    try {
                        val sysJson = JSONObject(systemInfoResponse)
                        val name = sysJson.optString("name", "")
                        if (name.isNotBlank()) deviceName = name
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                return@withContext RemoteDevice(
                    id = "volumio-$ip:$port",
                    name = deviceName,
                    type = RemoteDeviceType.VOLUMIO,
                    ip = ip,
                    port = port,
                    locationUrl = "http://$ip:$port",
                    modelName = "Volumio Audiophile Linux OS",
                    manufacturer = "Volumio",
                    isOnline = true
                )
            }
        } catch (e: Exception) {
            // Not a Volumio device
        }
        null
    }

    private fun getHttp(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "Spindle-DAP-Transport/2.0")
            }

            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
