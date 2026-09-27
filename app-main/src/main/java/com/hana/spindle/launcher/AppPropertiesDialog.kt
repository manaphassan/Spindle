package com.hana.spindle.launcher

import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.Window
import android.widget.Toast
import com.hana.spindle.databinding.DialogAppPropertiesBinding
import java.io.File
import java.util.Locale

/**
 * Audiophile Cassette Bay dialog for managing application properties:
 * - App Info (System Settings)
 * - Permissions Manager
 * - Storage & Clear Cache
 * - Uninstall Application
 * - Direct Launch
 */
object AppPropertiesDialog {

    fun show(
        context: Context,
        app: AppInfo,
        onAppChanged: (() -> Unit)? = null
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val binding = DialogAppPropertiesBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val width = (context.resources.displayMetrics.widthPixels * 0.92).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        // Populate header
        binding.ivAppIcon.setImageDrawable(app.icon)
        binding.tvAppName.text = app.label
        binding.tvPackageName.text = app.packageName

        val pm = context.packageManager
        val packageInfo = try { pm.getPackageInfo(app.packageName, 0) } catch (e: Exception) { null }
        val appInfoObj = try { pm.getApplicationInfo(app.packageName, 0) } catch (e: Exception) { null }
        val isSystemApp = appInfoObj?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false

        val versionName = packageInfo?.versionName ?: "N/A"
        val apkFile = appInfoObj?.sourceDir?.let { File(it) }
        val apkSizeStr = if (apkFile != null && apkFile.exists()) {
            String.format(Locale.US, "%.1f MB", apkFile.length() / (1024f * 1024f))
        } else null

        val specsList = ArrayList<String>()
        specsList.add("v$versionName")
        if (apkSizeStr != null) specsList.add(apkSizeStr)
        if (appInfoObj != null) specsList.add("API ${appInfoObj.targetSdkVersion}")
        binding.tvAppSpecs.text = specsList.joinToString(" • ")

        if (isSystemApp) {
            binding.tvAppTypeBadge.text = "SYSTEM APP"
            binding.tvAppTypeBadge.setTextColor(Color.parseColor("#F59E0B"))
            binding.tvAppTypeBadge.setBackgroundColor(Color.parseColor("#261C12"))
        } else {
            binding.tvAppTypeBadge.text = "USER APP"
            binding.tvAppTypeBadge.setTextColor(Color.parseColor("#38BDF8"))
            binding.tvAppTypeBadge.setBackgroundColor(Color.parseColor("#0F2231"))
        }

        binding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // Action 1: Open App
        binding.btnActionOpen.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            dialog.dismiss()
            try {
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                } else {
                    val fallback = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        component = ComponentName(app.packageName, app.activityName)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    }
                    context.startActivity(fallback)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open ${app.label}: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Action 2: App Info
        binding.btnActionInfo.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            dialog.dismiss()
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", app.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open App Info: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Action 3: App Permissions
        binding.btnActionPermissions.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            dialog.dismiss()
            try {
                val permIntent = Intent("android.intent.action.MANAGE_APP_PERMISSIONS").apply {
                    putExtra(Intent.EXTRA_PACKAGE_NAME, app.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(permIntent)
            } catch (e: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", app.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            }
        }

        // Action 4: Clear Cache & Storage
        binding.btnActionCache.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            dialog.dismiss()
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", app.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Toast.makeText(context, "Tap 'Storage & cache' to clear cache for ${app.label}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open Storage settings: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Action 5: Uninstall App
        if (isSystemApp) {
            binding.btnActionUninstall.alpha = 0.5f
            binding.tvUninstallDesc.text = "Preinstalled system app (Cannot uninstall, tap to disable via App Info)"
            binding.btnActionUninstall.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                dialog.dismiss()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", app.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Toast.makeText(context, "Tap 'Disable' to deactivate ${app.label}", Toast.LENGTH_SHORT).show()
            }
        } else {
            binding.btnActionUninstall.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                dialog.dismiss()
                try {
                    val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.fromParts("package", app.packageName, null)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(uninstallIntent)
                    onAppChanged?.invoke()
                } catch (e: Exception) {
                    val fallback = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                        data = Uri.fromParts("package", app.packageName, null)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallback)
                    onAppChanged?.invoke()
                }
            }
        }

        dialog.show()
    }
}
