package com.hana.spindle.remote

import org.junit.Assert.*
import org.junit.Test

class RemoteTransportTest {

    @Test
    fun testRemoteDeviceModelAndSubtitle() {
        val dlnaDevice = RemoteDevice(
            id = "uuid:12345-renderer",
            name = "Marantz NA6006",
            type = RemoteDeviceType.UPNP_DLNA,
            ip = "192.168.1.55",
            port = 49152,
            locationUrl = "http://192.168.1.55:49152/description.xml",
            avTransportControlUrl = "/upnp/control/AVTransport1",
            renderingControlUrl = "/upnp/control/RenderingControl1",
            modelName = "NA6006",
            manufacturer = "Marantz"
        )

        assertEquals("DLNA / UPnP", dlnaDevice.typeBadge)
        assertTrue(dlnaDevice.displaySubtitle.contains("Marantz"))
        assertTrue(dlnaDevice.displaySubtitle.contains("NA6006"))
        assertTrue(dlnaDevice.displaySubtitle.contains("192.168.1.55"))

        val volumioDevice = RemoteDevice(
            id = "volumio-192.168.1.120:3000",
            name = "Volumio Living Room",
            type = RemoteDeviceType.VOLUMIO,
            ip = "192.168.1.120",
            port = 3000,
            locationUrl = "http://192.168.1.120:3000"
        )

        assertEquals("VOLUMIO", volumioDevice.typeBadge)
        assertEquals("Volumio Audiophile Streamer • 192.168.1.120:3000", volumioDevice.displaySubtitle)
    }

    @Test
    fun testUpnpTimeStringConversion() {
        // Milliseconds to HH:MM:SS
        assertEquals("00:00:00", UpnpSoapClient.formatMsToTimeString(0L))
        assertEquals("00:02:15", UpnpSoapClient.formatMsToTimeString(135000L))
        assertEquals("01:15:30", UpnpSoapClient.formatMsToTimeString((1 * 3600 + 15 * 60 + 30) * 1000L))

        // String to Milliseconds
        assertEquals(0L, UpnpSoapClient.parseTimeStringToMs(null))
        assertEquals(0L, UpnpSoapClient.parseTimeStringToMs(""))
        assertEquals(0L, UpnpSoapClient.parseTimeStringToMs("NOT_IMPLEMENTED"))
        assertEquals(135000L, UpnpSoapClient.parseTimeStringToMs("00:02:15"))
        assertEquals(135000L, UpnpSoapClient.parseTimeStringToMs("02:15"))
        assertEquals(4530000L, UpnpSoapClient.parseTimeStringToMs("01:15:30"))
    }

    @Test
    fun testUpnpResolveControlUrl() {
        val base = "http://192.168.1.50:49152/upnp/desc.xml"

        // Relative path starting with /
        val res1 = UpnpSoapClient.resolveControlUrl(base, "/upnp/control/AVTransport1")
        assertEquals("http://192.168.1.50:49152/upnp/control/AVTransport1", res1)

        // Already absolute URL
        val absolute = "http://192.168.1.80:8080/ctl"
        val res2 = UpnpSoapClient.resolveControlUrl(base, absolute)
        assertEquals(absolute, res2)
    }

    @Test
    fun testRemoteTransportStatusFlags() {
        val playing = RemoteTransportStatus(
            state = RemotePlaybackState.PLAYING,
            title = "Aint Misbehavin",
            artist = "Various Artists",
            durationMs = 240000L,
            positionMs = 60000L,
            volume = 85
        )
        assertTrue(playing.isPlaying)
        assertFalse(playing.isPaused)
        assertFalse(playing.isStopped)

        val paused = playing.copy(state = RemotePlaybackState.PAUSED)
        assertFalse(paused.isPlaying)
        assertTrue(paused.isPaused)
        assertFalse(paused.isStopped)

        val stopped = playing.copy(state = RemotePlaybackState.STOPPED)
        assertFalse(stopped.isPlaying)
        assertFalse(stopped.isPaused)
        assertTrue(stopped.isStopped)
    }
}
