package com.hana.spindle.lite.util

import android.os.Build

/**
 * Dynamic industrial nameplate formatter for Spindle Lite.
 * Formats hardware name based on Build manufacturer/model or codename.
 */
object DeviceNameFormatter {

    fun getDeviceNameplate(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val device = Build.DEVICE.orEmpty().trim()

        return when {
            device.equals("satsuma", ignoreCase = true) || model.contains("WT19", ignoreCase = true) -> {
                "SPINDLE • SATSUMA HVGA"
            }
            model.startsWith(manufacturer, ignoreCase = true) -> {
                model.uppercase()
            }
            manufacturer.isNotEmpty() && model.isNotEmpty() -> {
                "${manufacturer.uppercase()} ${model.uppercase()}"
            }
            model.isNotEmpty() -> {
                model.uppercase()
            }
            else -> {
                "SPINDLE LITE PORTABLE"
            }
        }
    }
}
