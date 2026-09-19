package com.hana.spindle.launcher

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Loads all installed launchable applications on the device.
 */
class AppListLoader(private val context: Context) {

    suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val appList = ArrayList<AppInfo>(resolveInfos.size)

        for (resolveInfo in resolveInfos) {
            val pkg = resolveInfo.activityInfo.packageName
            // Exclude Spindle itself from the app drawer
            if (pkg == context.packageName) continue

            val label = resolveInfo.loadLabel(pm)?.toString() ?: pkg
            val icon = resolveInfo.loadIcon(pm)
            val activityName = resolveInfo.activityInfo.name

            appList.add(
                AppInfo(
                    label = label,
                    packageName = pkg,
                    activityName = activityName,
                    icon = icon
                )
            )
        }

        appList.sortedBy { it.label.lowercase(Locale.ROOT) }
    }
}
