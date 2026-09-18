package com.hana.spindle.launcher

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight persistent manager that tracks recently launched applications.
 * Ensures fast O(1) recency lookups and deduplicated chronological order.
 */
class RecentAppsManager(
    context: Context? = null,
    private val prefs: SharedPreferences = context!!.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
) {

    companion object {
        private const val PREFS_NAME = "spindle_recent_apps"
        private const val KEY_RECENT_PACKAGES = "recent_packages"
        const val MAX_RECENT_APPS = 20
        private const val DELIMITER = ","
    }

    /**
     * Records an application launch, moving it to the top of the recency list.
     */
    fun recordAppLaunch(packageName: String) {
        if (packageName.isBlank()) return
        val current = getRecentPackageNames().toMutableList()
        current.remove(packageName)
        current.add(0, packageName)
        if (current.size > MAX_RECENT_APPS) {
            current.subList(MAX_RECENT_APPS, current.size).clear()
        }
        val serialized = current.joinToString(DELIMITER)
        prefs.edit().putString(KEY_RECENT_PACKAGES, serialized).apply()
    }

    /**
     * Retrieves ordered list of recently launched package names (newest first).
     */
    fun getRecentPackageNames(): List<String> {
        val raw = prefs.getString(KEY_RECENT_PACKAGES, null) ?: return emptyList()
        return raw.split(DELIMITER).filter { it.isNotBlank() }
    }

    /**
     * Resolves the recent package names against the full installed apps list.
     * Preserves chronological recency order.
     */
    fun getRecentApps(allApps: List<AppInfo>): List<AppInfo> {
        val recentPackages = getRecentPackageNames()
        val appMap = allApps.associateBy { it.packageName }
        val result = ArrayList<AppInfo>(recentPackages.size)
        for (pkg in recentPackages) {
            val app = appMap[pkg]
            if (app != null) {
                result.add(app)
            }
        }
        return result
    }

    /**
     * Clears all recorded recent apps.
     */
    fun clearRecentApps() {
        prefs.edit().remove(KEY_RECENT_PACKAGES).apply()
    }
}
