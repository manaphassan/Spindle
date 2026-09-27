package com.hana.spindle.remote

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.Locale

/**
 * High-performance SSDP (Simple Service Discovery Protocol) and Subnet Streamer scanner.
 * Discovers UPnP/DLNA MediaRenderers and Volumio Audiophile Streamers.
 */
class SsdpDiscovery(
    private val context: Context,
    private val volumioClient: VolumioRestClient = VolumioRestClient()
) {

    companion object {
        private const val TAG = "SsdpDiscovery"
        private const val SSDP_ADDR = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val DISCOVERY_TIMEOUT_MS = 3500

        private const val SEARCH_TARGET_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"
        private const val SEARCH_TARGET_AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
        private const val SEARCH_TARGET_ROOT = "upnp:rootdevice"
    }

    suspend fun discoverStreamers(): List<RemoteDevice> = withContext(Dispatchers.IO) {
        val discoveredDevices = LinkedHashMap<String, RemoteDevice>()
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        var multicastLock: WifiManager.MulticastLock? = null

        try {
            multicastLock = wifiManager?.createMulticastLock("spindle_ssdp_lock")?.apply {
                setReferenceCounted(false)
                acquire()
            }

            // 1. Send SSDP M-SEARCH requests
            val ssdpTargets = listOf(
                SEARCH_TARGET_RENDERER,
                SEARCH_TARGET_AV_TRANSPORT,
                SEARCH_TARGET_ROOT
            )

            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply {
                    soTimeout = 1200
                    reuseAddress = true
                }

                val group = InetAddress.getByName(SSDP_ADDR)

                for (target in ssdpTargets) {
                    val mSearch = "M-SEARCH * HTTP/1.1\r\n" +
                            "HOST: $SSDP_ADDR:$SSDP_PORT\r\n" +
                            "MAN: \"ssdp:discover\"\r\n" +
                            "MX: 2\r\n" +
                            "ST: $target\r\n\r\n"

                    val txData = mSearch.toByteArray(Charsets.UTF_8)
                    val txPacket = DatagramPacket(txData, txData.size, group, SSDP_PORT)
                    socket.send(txPacket)
                }

                val rxBuffer = ByteArray(8192)
                val rxPacket = DatagramPacket(rxBuffer, rxBuffer.size)
                val startTime = System.currentTimeMillis()

                val pendingLocations = mutableSetOf<String>()

                while (System.currentTimeMillis() - startTime < DISCOVERY_TIMEOUT_MS) {
                    try {
                        socket.receive(rxPacket)
                        val response = String(rxPacket.data, 0, rxPacket.length, Charsets.UTF_8)
                        val headers = parseHeaders(response)
                        val location = headers["location"]
                        if (!location.isNullOrBlank() && !pendingLocations.contains(location)) {
                            pendingLocations.add(location)
                            val device = parseDeviceDescription(location)
                            if (device != null) {
                                discoveredDevices[device.id] = device
                            }
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        // End of packet window
                        break
                    } catch (e: Exception) {
                        Log.d(TAG, "SSDP receive loop notice: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "SSDP Multicast scan error: ${e.message}")
            } finally {
                socket?.close()
            }

            // 2. Direct Volumio Subnet Probe (Common IP patterns and volumio.local)
            probeVolumioEndpoints(wifiManager, discoveredDevices)

        } finally {
            try {
                multicastLock?.let {
                    if (it.isHeld) it.release()
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        discoveredDevices.values.toList()
    }

    private suspend fun probeVolumioEndpoints(
        wifiManager: WifiManager?,
        discoveredDevices: LinkedHashMap<String, RemoteDevice>
    ) {
        // Probe "volumio.local"
        try {
            val vLocal = volumioClient.probeEndpoint("volumio.local", 3000)
            if (vLocal != null) {
                discoveredDevices[vLocal.id] = vLocal
            }
        } catch (e: Exception) {
            // ignore
        }

        // Check Gateway IP for common standalone Raspberry Pi / Volumio hotspot or AP
        val dhcp = wifiManager?.dhcpInfo
        if (dhcp != null && dhcp.gateway != 0) {
            val gwIp = formatIpAddress(dhcp.gateway)
            if (gwIp.isNotBlank()) {
                val vGw = volumioClient.probeEndpoint(gwIp, 3000)
                if (vGw != null) {
                    discoveredDevices[vGw.id] = vGw
                }
            }
        }
    }

    private fun formatIpAddress(ip: Int): String {
        return (ip and 0xFF).toString() + "." +
                ((ip shr 8) and 0xFF) + "." +
                ((ip shr 16) and 0xFF) + "." +
                ((ip shr 24) and 0xFF)
    }

    private fun parseHeaders(response: String): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        val lines = response.split("\r\n", "\n")
        for (line in lines) {
            val idx = line.indexOf(':')
            if (idx > 0) {
                val key = line.substring(0, idx).trim().lowercase(Locale.US)
                val value = line.substring(idx + 1).trim()
                headers[key] = value
            }
        }
        return headers
    }

    private fun parseDeviceDescription(locationUrl: String): RemoteDevice? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(locationUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 2500
                setRequestProperty("User-Agent", "Spindle-DAP-Transport/2.0")
            }

            if (conn.responseCode !in 200..299) return null

            val xmlContent = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }

            parseDeviceXml(locationUrl, url.host, url.port.let { if (it <= 0) 80 else it }, xmlContent)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to parse UPnP description from $locationUrl: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseDeviceXml(
        locationUrl: String,
        host: String,
        port: Int,
        xmlContent: String
    ): RemoteDevice? {
        var friendlyName = ""
        var manufacturer = ""
        var modelName = ""
        var udn = ""
        var avTransportControlUrl = ""
        var renderingControlUrl = ""

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlContent))

            var eventType = parser.eventType
            var currentTag = ""
            var insideService = false
            var currentServiceType = ""
            var currentControlUrl = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name.lowercase(Locale.US)
                        if (currentTag == "service") {
                            insideService = true
                            currentServiceType = ""
                            currentControlUrl = ""
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text.trim()
                        if (text.isNotEmpty()) {
                            if (insideService) {
                                when (currentTag) {
                                    "servicetype" -> currentServiceType = text
                                    "controlurl" -> currentControlUrl = text
                                }
                            } else {
                                when (currentTag) {
                                    "friendlyname" -> if (friendlyName.isEmpty()) friendlyName = text
                                    "manufacturer" -> if (manufacturer.isEmpty()) manufacturer = text
                                    "modelname" -> if (modelName.isEmpty()) modelName = text
                                    "udn" -> if (udn.isEmpty()) udn = text
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val endTag = parser.name.lowercase(Locale.US)
                        if (endTag == "service") {
                            insideService = false
                            if (currentServiceType.contains("AVTransport", ignoreCase = true)) {
                                avTransportControlUrl = currentControlUrl
                            } else if (currentServiceType.contains("RenderingControl", ignoreCase = true)) {
                                renderingControlUrl = currentControlUrl
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "XML parse error for $locationUrl: ${e.message}")
        }

        // Only register device if it supports AVTransport or is a MediaRenderer
        if (avTransportControlUrl.isNotBlank() || friendlyName.isNotBlank()) {
            val id = if (udn.isNotBlank()) udn else "upnp-$host:$port"
            val displayName = if (friendlyName.isNotBlank()) friendlyName else "UPnP Media Renderer ($host)"
            return RemoteDevice(
                id = id,
                name = displayName,
                type = RemoteDeviceType.UPNP_DLNA,
                ip = host,
                port = port,
                locationUrl = locationUrl,
                avTransportControlUrl = avTransportControlUrl,
                renderingControlUrl = renderingControlUrl,
                modelName = modelName,
                manufacturer = manufacturer,
                isOnline = true
            )
        }
        return null
    }
}
