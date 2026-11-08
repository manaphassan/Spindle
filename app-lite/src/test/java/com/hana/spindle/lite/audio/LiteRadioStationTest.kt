package com.hana.spindle.lite.audio

import org.junit.Assert.*
import org.junit.Test

class LiteRadioStationTest {
    @Test fun presetsUseHttpsOnModernAndroid() {
        for (s in LiteRadioEngine.PRESETS) {
            assertTrue(s.callsign, s.resolveUrl(21).startsWith("https://"))
            assertTrue(s.callsign, s.resolveUrl(34).startsWith("https://"))
            assertTrue(s.streamUrl.startsWith("https://"))
        }
    }

    @Test fun plainHttpOnlyOnKitKat() {
        val s = LiteRadioEngine.PRESETS.first()
        assertTrue(s.resolveUrl(19).startsWith("http://"))
        assertEquals("https://x/y", LiteRadioStation(1f, "A", "A", "g", "https://x/y").resolveUrl(19))
    }
}
