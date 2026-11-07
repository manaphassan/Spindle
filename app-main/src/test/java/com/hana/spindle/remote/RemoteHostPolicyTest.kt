package com.hana.spindle.remote

import org.junit.Assert.*
import org.junit.Test

class RemoteHostPolicyTest {
    @Test fun privateHostsAllowed() {
        for (h in listOf("192.168.1.20", "10.0.0.5", "172.16.0.1", "172.31.255.1", "169.254.1.1", "127.0.0.1",
            "localhost", "volumio.local", "::1", "fe80::1", "fd12::3")) assertTrue(h, RemoteHostPolicy.isPrivateHost(h))
    }
    @Test fun publicHostsRejected() {
        for (h in listOf("8.8.8.8", "172.32.0.1", "172.15.0.1", "192.169.1.1", "example.com", "300.1.1.1", "", "1.2.3")) {
            assertFalse(h, RemoteHostPolicy.isPrivateHost(h))
        }
        assertFalse(RemoteHostPolicy.isPrivateHost(null))
    }
    @Test fun urlRules() {
        assertTrue(RemoteHostPolicy.isAllowedUrl("http://192.168.1.2:3000/api"))
        assertTrue(RemoteHostPolicy.isAllowedUrl("https://example.com/x"))
        assertFalse(RemoteHostPolicy.isAllowedUrl("http://example.com/x"))
        assertFalse(RemoteHostPolicy.isAllowedUrl("ftp://192.168.1.2/x"))
        assertFalse(RemoteHostPolicy.isAllowedUrl("not a url"))
    }
}
