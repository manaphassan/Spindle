package com.hana.spindle.launcher

import android.graphics.drawable.Drawable

/**
 * Lightweight data model representing an installed launchable application.
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable? = null
)
