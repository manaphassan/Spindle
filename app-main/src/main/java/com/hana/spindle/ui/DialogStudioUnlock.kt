package com.hana.spindle.ui

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.hana.spindle.R
import com.hana.spindle.licensing.StudioUnlockManager

/**
 * Audiophile Dialog for Spindle Studio Collector Pass & Offline Token Redemption.
 */
class DialogStudioUnlock(
    private val context: Context,
    private val onUnlockStateChanged: (() -> Unit)? = null
) {

    fun show() {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_studio_unlock, null)
        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvStatusBadge = view.findViewById<TextView>(R.id.tvStudioStatusBadge)
        val tvPolicyDesc = view.findViewById<TextView>(R.id.tvStudioPolicyDesc)
        val btnClose = view.findViewById<ImageButton>(R.id.btnStudioClose)
        val btnGooglePlay = view.findViewById<Button>(R.id.btnStudioGooglePlay)
        val etToken = view.findViewById<EditText>(R.id.etStudioToken)
        val btnRedeemToken = view.findViewById<Button>(R.id.btnStudioRedeemToken)
        val btnSponsorLink = view.findViewById<Button>(R.id.btnStudioSponsorLink)

        fun updateUiState() {
            val isUnlocked = StudioUnlockManager.isStudioUnlocked(context)
            if (isUnlocked) {
                tvStatusBadge.text = context.getString(R.string.studio_badge_unlocked)
                tvStatusBadge.setTextColor(Color.parseColor("#00E676"))
                val source = StudioUnlockManager.getUnlockSource(context)
                tvPolicyDesc.text = "Lifetime Studio Collector Active ($source).\nAll analog tape saturation DSP, custom laser engraving & foley soundpacks unlocked."
                btnGooglePlay.visibility = View.GONE
            } else {
                tvStatusBadge.text = context.getString(R.string.studio_badge_free)
                tvStatusBadge.setTextColor(Color.parseColor("#38BDF8"))
                tvPolicyDesc.text = context.getString(R.string.studio_policy_desc)
                btnGooglePlay.visibility = View.VISIBLE
            }
        }

        updateUiState()

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        btnGooglePlay.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.hana.spindle")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Google Play Store unavailable on this DAP.\nUse offline sponsor key redemption below.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        btnRedeemToken.setOnClickListener {
            val input = etToken.text?.toString().orEmpty()
            if (input.isBlank()) {
                Toast.makeText(context, context.getString(R.string.studio_hint_token), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val success = StudioUnlockManager.redeemSponsorToken(context, input)
            if (success) {
                Toast.makeText(context, context.getString(R.string.studio_redeem_success), Toast.LENGTH_LONG).show()
                updateUiState()
                onUnlockStateChanged?.invoke()
            } else {
                Toast.makeText(context, context.getString(R.string.studio_redeem_failed), Toast.LENGTH_LONG).show()
            }
        }

        btnSponsorLink.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/manaphassan")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Sponsor Spindle: https://paypal.me/manaphassan", Toast.LENGTH_LONG).show()
            }
        }

        dialog.show()
    }
}
