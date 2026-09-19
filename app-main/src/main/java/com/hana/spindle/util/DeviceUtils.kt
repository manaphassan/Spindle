package com.hana.spindle.util

import android.content.Context
import android.os.Build

object DeviceUtils {

    /**
     * Resolves the hardware device branding name (e.g., "SONY SO-02J", "FIIO M11", "IBASSO DX160").
     * Supports user custom nameplate override if configured in SharedPreferences.
     */
    fun getDeviceName(context: Context? = null): String {
        if (context != null) {
            try {
                val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
                val custom = prefs.getString("pref_custom_device_name", null)?.trim()
                if (!custom.isNullOrEmpty()) {
                    return custom.uppercase()
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        return getHardwareDeviceName()
    }

    /**
     * Formats the manufacturer and model cleanly without duplication.
     */
    fun getHardwareDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val raw = when {
            model.startsWith(manufacturer, ignoreCase = true) -> model
            manufacturer.isNotEmpty() && model.isNotEmpty() -> "$manufacturer $model"
            model.isNotEmpty() -> model
            manufacturer.isNotEmpty() -> manufacturer
            else -> "SPINDLE DECK"
        }
        return raw.uppercase()
    }

    /**
     * Formats the Android OS version and official dessert codename.
     * Examples:
     * - Android 4.1: "ANDROID 4.1 • JELLY BEAN"
     * - Android 4.4: "ANDROID 4.4 • KITKAT"
     * - Android 5.0: "ANDROID 5.0 • LOLLIPOP"
     * - Android 6.0: "ANDROID 6.0 • MARSHMALLOW"
     * - Android 7.1: "ANDROID 7.1 • NOUGAT"
     * - Android 8.0: "ANDROID 8.0 • OREO"
     * - Android 9.0: "ANDROID 9.0 • PIE"
     * - Android 10+: "ANDROID 10", etc.
     */
    fun getAndroidVersionString(): String {
        val sdkInt = Build.VERSION.SDK_INT
        val release = Build.VERSION.RELEASE.orEmpty().trim()
        val codename = when (sdkInt) {
            in 1..3 -> "BASE"
            4 -> "DONUT"
            in 5..7 -> "ECLAIR"
            8 -> "FROYO"
            in 9..10 -> "GINGERBREAD"
            in 11..13 -> "HONEYCOMB"
            in 14..15 -> "ICE CREAM SANDWICH"
            in 16..18 -> "JELLY BEAN"
            19, 20 -> "KITKAT"
            21, 22 -> "LOLLIPOP"
            23 -> "MARSHMALLOW"
            24, 25 -> "NOUGAT"
            26, 27 -> "OREO"
            28 -> "PIE"
            29 -> "ANDROID 10"
            30 -> "ANDROID 11"
            31, 32 -> "ANDROID 12"
            33 -> "ANDROID 13"
            34 -> "ANDROID 14"
            35 -> "ANDROID 15"
            else -> if (release.isNotEmpty()) "ANDROID $release" else "ANDROID OS"
        }

        return if (release.isNotEmpty() && !codename.startsWith("ANDROID")) {
            "ANDROID $release • $codename"
        } else if (codename.startsWith("ANDROID")) {
            codename
        } else {
            "ANDROID • $codename"
        }
    }
}
