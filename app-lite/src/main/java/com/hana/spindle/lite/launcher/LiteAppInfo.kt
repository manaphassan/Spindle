package com.hana.spindle.lite.launcher

import android.graphics.drawable.Drawable

data class LiteAppInfo(
    val label: String,
    val packageName: String,
    val className: String,
    val icon: Drawable? = null
)
