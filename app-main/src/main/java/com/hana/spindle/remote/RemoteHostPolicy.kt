package com.hana.spindle.remote

import java.net.URL

/**
 * Cleartext HTTP is only acceptable for devices on the local network (Volumio, UPnP renderers).
 * This rejects any other host so a rogue SSDP/UPnP reply cannot steer requests to the internet.
 * Host names are never resolved here (no blocking DNS); only literals, "localhost" and ".local".
 */
object RemoteHostPolicy {

    fun isPrivateHost(host: String?): Boolean {
        val h = host?.trim()?.trim('[', ']')?.lowercase() ?: return false
        if (h.isEmpty()) return false
        if (h == "localhost" || h.endsWith(".local")) return true
        if (h.contains(':')) { // IPv6 literal
            return h == "::1" || h.startsWith("fe80:") || h.startsWith("fc") || h.startsWith("fd")
        }
        val p = h.split('.')
        if (p.size != 4) return false
        val o = p.map { it.toIntOrNull() ?: return false }
        if (o.any { it !in 0..255 }) return false
        return o[0] == 10 ||
            o[0] == 127 ||
            (o[0] == 192 && o[1] == 168) ||
            (o[0] == 172 && o[1] in 16..31) ||
            (o[0] == 169 && o[1] == 254)
    }

    /** True if [urlStr] is https, or http to a private host. */
    fun isAllowedUrl(urlStr: String): Boolean = try {
        val u = URL(urlStr)
        when (u.protocol.lowercase()) {
            "https" -> true
            "http" -> isPrivateHost(u.host)
            else -> false
        }
    } catch (e: Exception) {
        false
    }
}
