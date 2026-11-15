package com.hana.spindle.ui

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hana.spindle.R
import com.hana.spindle.playback.AutoEqParametricParser
import com.hana.spindle.playback.ParametricEq
import com.hana.spindle.playback.toAutoEqText

/** Text-based parametric EQ editor: AutoEq / Equalizer APO lines in, parsed bands out. */
class DialogParametricEq : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(ctx).apply {
            setText(R.string.peq_dialog_title)
            textSize = 18f
        })
        root.addView(TextView(ctx).apply {
            setText(R.string.peq_dialog_hint)
            textSize = 12f
        })
        val editor = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 8
            gravity = android.view.Gravity.TOP
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            setText(ParametricEq.settings.value.toAutoEqText())
        }
        root.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val buttons = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(textRes: Int, onClick: () -> Unit) = Button(ctx).apply {
            setText(textRes)
            minHeight = (48 * resources.displayMetrics.density).toInt()
            setOnClickListener { onClick() }
        }.also { buttons.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }

        button(R.string.peq_apply) {
            val parsed = AutoEqParametricParser.parse(editor.text.toString())
            if (parsed == null) {
                Toast.makeText(ctx, R.string.peq_parse_error, Toast.LENGTH_SHORT).show()
            } else {
                val keepEnabled = ParametricEq.settings.value.enabled
                ParametricEq.update(parsed.copy(enabled = keepEnabled || parsed.enabled), ctx)
                Toast.makeText(ctx, getString(R.string.peq_applied, parsed.bands.size), Toast.LENGTH_SHORT).show()
                editor.setText(ParametricEq.settings.value.toAutoEqText())
            }
        }
        button(R.string.peq_auto_preamp) {
            val parsed = AutoEqParametricParser.parse(editor.text.toString())
            if (parsed != null) editor.setText(parsed.copy(preampDb = parsed.autoPreampDb()).toAutoEqText())
        }
        button(R.string.peq_reset) {
            ParametricEq.update(ParametricEq.settings.value.copy(preampDb = 0f, bands = emptyList()), ctx)
            editor.setText(ParametricEq.settings.value.toAutoEqText())
        }
        root.addView(buttons)
        return root
    }
}
