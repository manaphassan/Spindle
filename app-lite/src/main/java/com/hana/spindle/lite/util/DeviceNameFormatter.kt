package com.hana.spindle.lite.util

import android.os.Build

/**
 * Dynamic industrial nameplate formatter for Spindle Lite.
 * Formats hardware name based on Build manufacturer/model or codename,
 * with support for custom audiophile engraving presets.
 */
object DeviceNameFormatter {

    val NAMEPLATE_PRESETS = listOf(
        "AUTO",
        "SPINDLE • REFERENCE DAP",
        "HIGH BIAS • MASTER RECORDER",
        "DIRECT ALSA • 192K LOSSLESS",
        "SATSUMA AUDIO EDITION"
    )

    fun getFormattedNameplate(presetIndex: Int): String {
        return if (presetIndex <= 0 || presetIndex >= NAMEPLATE_PRESETS.size) {
            getHardwareDeviceName()
        } else {
            NAMEPLATE_PRESETS[presetIndex]
        }
    }

    /**
     * Resolves the hardware manufacturer and model cleanly without repetition.
     * Examples: "SONY ERICSSON ST17i", "SONY SO-02J", "FIIO M11"
     */
    fun getHardwareDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val raw = when {
            manufacturer.isNotEmpty() && model.isNotEmpty() -> {
                if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
            }
            model.isNotEmpty() -> model
            manufacturer.isNotEmpty() -> manufacturer
            else -> "SPINDLE DAP"
        }
        return raw.uppercase()
    }

    /**
     * Formats the Android OS version and official dessert codename.
     * Example on ST17i: "ANDROID 4.4.4 • KITKAT"
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

    fun getDeviceNameplate(): String {
        return getHardwareDeviceName()
    }
}
