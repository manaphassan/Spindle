package com.hana.spindle.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Lightweight SOAP 1.1 HTTP client for UPnP AVTransport:1 and RenderingControl:1.
 */
class UpnpSoapClient {

    companion object {
        private const val TAG = "UpnpSoapClient"
        private const val TIMEOUT_MS = 3500

        private const val AV_TRANSPORT_NS = "urn:schemas-upnp-org:service:AVTransport:1"
        private const val RENDERING_CONTROL_NS = "urn:schemas-upnp-org:service:RenderingControl:1"

        fun formatMsToTimeString(ms: Long): String {
            val totalSec = (ms / 1000).coerceAtLeast(0L)
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        }

        fun parseTimeStringToMs(timeStr: String?): Long {
            if (timeStr.isNullOrBlank() || timeStr == "NOT_IMPLEMENTED") return 0L
            val parts = timeStr.trim().split(":")
            return try {
                when (parts.size) {
                    3 -> {
                        val h = parts[0].toLong()
                        val m = parts[1].toLong()
                        val s = parts[2].toDouble().toLong()
                        (h * 3600 + m * 60 + s) * 1000L
                    }
                    2 -> {
                        val m = parts[0].toLong()
                        val s = parts[1].toDouble().toLong()
                        (m * 60 + s) * 1000L
                    }
                    else -> 0L
                }
            } catch (e: Exception) {
                0L
            }
        }

        fun resolveControlUrl(baseUrl: String, controlUrl: String): String {
            if (controlUrl.startsWith("http://", ignoreCase = true) ||
                controlUrl.startsWith("https://", ignoreCase = true)
            ) {
                return controlUrl
            }
            return try {
                val base = URL(baseUrl)
                URL(base, controlUrl).toString()
            } catch (e: Exception) {
                baseUrl
            }
        }
    }

    suspend fun play(device: RemoteDevice): Boolean = withContext(Dispatchers.IO) {
        val body = """
            <u:Play xmlns:u="$AV_TRANSPORT_NS">
                <InstanceID>0</InstanceID>
                <Speed>1</Speed>
            </u:Play>
        """.trimIndent()
        postSoap(device.locationUrl, device.avTransportControlUrl, AV_TRANSPORT_NS, "Play", body) != null
    }

    suspend fun pause(device: RemoteDevice): Boolean = withContext(Dispatchers.IO) {
        val body = """
            <u:Pause xmlns:u="$AV_TRANSPORT_NS">
                <InstanceID>0</InstanceID>
            </u:Pause>
        """.trimIndent()
        postSoap(device.locationUrl, device.avTransportControlUrl, AV_TRANSPORT_NS, "Pause", body) != null
    }

    suspend fun stop(device: RemoteDevice): Boolean = withContext(Dispatchers.IO) {
        val body = """
            <u:Stop xmlns:u="$AV_TRANSPORT_NS">
                <InstanceID>0</InstanceID>
            </u:Stop>
        """.trimIndent()
        postSoap(device.locationUrl, device.avTransportControlUrl, AV_TRANSPORT_NS, "Stop", body) != null
    }

    suspend fun next(device: RemoteDevice): Boolean = withContext(Dispatchers.IO) {
        val body = """
            <u:Next xmlns:u="$AV_TRANSPORT_NS">
                <InstanceID>0</InstanceID>
            </u:Next>
        """.trimIndent()
        postSoap(device.locationUrl, device.avTransportControlUrl, AV_TRANSPORT_NS, "Next", body) != null
    }

    suspend fun previous(device: RemoteDevice): Boolean = withContext(Dispatchers.IO) {
        val body = """
            <u:Previous xmlns:u="$AV_TRANSPORT_NS">
                <InstanceID>0</InstanceID>
            </u:Previous>
        """.trimIndent()
        postSoap(device.locationUrl, device.avTransportControlUrl, AV_TRANSPORT_NS, "Previous", body) != null
    }

    suspend fun seek(device: RemoteDevice, positionMs: Long): Boolean = withContext(Dispatchers.IO) {
        val target = formatMsToTimeString(positionMs)
        val body = """
            <u:Seek xmlns:u="$AV_TRANSPORT_NS">
                <InstanceID>0</InstanceID>
                <Unit>REL_TIME</Unit>
                <Target>$target</Target>
            </u:Seek>
        """.trimIndent()
        postSoap(device.locationUrl, device.avTransportControlUrl, AV_TRANSPORT_NS, "Seek", body) != null
    }

    suspend fun setVolume(device: RemoteDevice, volume: Int): Boolean = withContext(Dispatchers.IO) {
        val targetVol = volume.coerceIn(0, 100)
        val body = """
            <u:SetVolume xmlns:u="$RENDERING_CONTROL_NS">
                <InstanceID>0</InstanceID>
                <Channel>Master</Channel>
                <DesiredVolume>$targetVol</DesiredVolume>
            </u:SetVolume>
        """.trimIndent()
        postSoap(device.locationUrl, device.renderingControlUrl, RENDERING_CONTROL_NS, "SetVolume", body) != null
    }

    suspend fun setAvTransportUri(
        device: RemoteDevice,
        uri: String,
        title: String,
        artist: String
    ): Boolean = withContext(Dispatchers.IO) {
        val escapedUri = escapeXml(uri)
        val didl = """&lt;DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"&gt;&lt;item id="0" parentID="0" restricted="1"&gt;&lt;dc:title&gt;${escapeXml(title)}&lt;/dc:title&gt;&lt;dc:creator&gt;${escapeXml(artist)}&lt;/dc:creator&gt;&lt;upnp:class&gt;object.item.audioItem.musicTrack&lt;/upnp:class&gt;&lt;res&gt;$escapedUri&lt;/res&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;"""

        val body = """
            <u:SetAVTransportURI xmlns:u="$AV_TRANSPORT_NS">
                <InstanceID>0</InstanceID>
                <CurrentURI>$escapedUri</CurrentURI>
                <CurrentURIMetaData>$didl</CurrentURIMetaData>
            </u:SetAVTransportURI>
        """.trimIndent()
        postSoap(device.locationUrl, device.avTransportControlUrl, AV_TRANSPORT_NS, "SetAVTransportURI", body) != null
    }

    suspend fun getTransportInfo(device: RemoteDevice): RemotePlaybackState = withContext(Dispatchers.IO) {
        val body = """
            <u:GetTransportInfo xmlns:u="$AV_TRANSPORT_NS">
                <InstanceID>0</InstanceID>
            </u:GetTransportInfo>
        """.trimIndent()
        val response = postSoap(device.locationUrl, device.avTransportControlUrl, AV_TRANSPORT_NS, "GetTransportInfo", body)
            ?: return@withContext RemotePlaybackState.UNKNOWN

        val stateStr = extractXmlValue(response, "CurrentTransportState")
        when (stateStr?.uppercase(Locale.US)) {
            "PLAYING" -> RemotePlaybackState.PLAYING
            "PAUSED_PLAYBACK", "PAUSED" -> RemotePlaybackState.PAUSED
            "STOPPED" -> RemotePlaybackState.STOPPED
            "TRANSITIONING" -> RemotePlaybackState.TRANSITIONING
            else -> RemotePlaybackState.UNKNOWN
        }
    }

    data class PositionInfo(
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val title: String = "",
        val artist: String = ""
    )

    suspend fun getPositionInfo(device: RemoteDevice): PositionInfo = withContext(Dispatchers.IO) {
        val body = """
            <u:GetPositionInfo xmlns:u="$AV_TRANSPORT_NS">
                <InstanceID>0</InstanceID>
            </u:GetPositionInfo>
        """.trimIndent()
        val response = postSoap(device.locationUrl, device.avTransportControlUrl, AV_TRANSPORT_NS, "GetPositionInfo", body)
            ?: return@withContext PositionInfo()

        val relTimeStr = extractXmlValue(response, "RelTime")
        val trackDurationStr = extractXmlValue(response, "TrackDuration")
        val metaData = extractXmlValue(response, "TrackMetaData")

        val title = if (metaData != null) unescapeXml(extractXmlValue(metaData, "dc:title") ?: "") else ""
        val artist = if (metaData != null) unescapeXml(extractXmlValue(metaData, "dc:creator") ?: "") else ""

        PositionInfo(
            positionMs = parseTimeStringToMs(relTimeStr),
            durationMs = parseTimeStringToMs(trackDurationStr),
            title = title,
            artist = artist
        )
    }

    suspend fun getVolume(device: RemoteDevice): Int = withContext(Dispatchers.IO) {
        val body = """
            <u:GetVolume xmlns:u="$RENDERING_CONTROL_NS">
                <InstanceID>0</InstanceID>
                <Channel>Master</Channel>
            </u:GetVolume>
        """.trimIndent()
        val response = postSoap(device.locationUrl, device.renderingControlUrl, RENDERING_CONTROL_NS, "GetVolume", body)
            ?: return@withContext 100

        extractXmlValue(response, "CurrentVolume")?.toIntOrNull() ?: 100
    }

    private fun postSoap(
        baseUrl: String,
        controlPath: String,
        serviceNs: String,
        action: String,
        bodyContent: String
    ): String? {
        if (controlPath.isBlank() && baseUrl.isBlank()) return null
        val fullUrl = resolveControlUrl(baseUrl, controlPath)

        var conn: HttpURLConnection? = null
        return try {
            val url = URL(fullUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                setRequestProperty("SOAPACTION", "\"$serviceNs#$action\"")
                setRequestProperty("User-Agent", "Spindle-DAP-Transport/2.0")
            }

            val envelope = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
$bodyContent
    </s:Body>
</s:Envelope>"""

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(envelope)
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } else {
                Log.w(TAG, "SOAP Action $action returned HTTP $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "SOAP $action to $fullUrl failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun extractXmlValue(xml: String, tagName: String): String? {
        val startTag = "<$tagName"
        val endTag = "</$tagName>"
        val startIndex = xml.indexOf(startTag)
        if (startIndex == -1) return null

        val tagCloseIndex = xml.indexOf('>', startIndex)
        if (tagCloseIndex == -1) return null

        val endIndex = xml.indexOf(endTag, tagCloseIndex)
        if (endIndex == -1) return null

        return xml.substring(tagCloseIndex + 1, endIndex).trim()
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun unescapeXml(str: String): String {
        return str.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }
}
