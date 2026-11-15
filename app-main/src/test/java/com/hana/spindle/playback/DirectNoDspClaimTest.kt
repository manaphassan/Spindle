package com.hana.spindle.playback

import org.junit.Assert.*
import org.junit.Test

class DirectNoDspClaimTest {
    @Test fun claimsDirectOnlyWhenPrefDirectRouteAndNoDsp() {
        assertTrue(AudioMetricsTracker.computeDirectNoDsp(true, true, false))
        assertFalse(AudioMetricsTracker.computeDirectNoDsp(true, true, true))
        assertFalse(AudioMetricsTracker.computeDirectNoDsp(true, false, false))
        assertFalse(AudioMetricsTracker.computeDirectNoDsp(false, true, false))
    }
}
