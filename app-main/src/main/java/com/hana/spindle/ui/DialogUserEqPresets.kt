package com.hana.spindle.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hana.spindle.R
import com.hana.spindle.SpindleApp
import com.hana.spindle.databinding.DialogSaveUserEqPresetBinding
import com.hana.spindle.databinding.DialogUserEqPresetsBinding
import com.hana.spindle.databinding.ItemUserEqPresetBinding
import com.hana.spindle.playback.AudioFxController
import com.hana.spindle.playback.UserEqPreset
import java.util.Locale

/**
 * BottomSheetDialogFragment for managing custom audiophile User EQ & Parametric profiles.
 * Allows applying, saving, deleting, exporting, and importing headphone/IEM target curves.
 */
class DialogUserEqPresets(
    private val fxController: AudioFxController,
    private val openSaveDirectly: Boolean = false,
    private val onPresetApplied: (() -> Unit)? = null
) : BottomSheetDialogFragment() {

    private var _binding: DialogUserEqPresetsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: UserEqPresetAdapter

    // File picker launcher for importing .json / .spindle-eq.json preset curves
    private val importFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            importPresetFromUri(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogUserEqPresetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (openSaveDirectly) {
            view.post { showSavePresetDialog() }
        }

        binding.btnCloseUserEq.setOnClickListener {
            dismiss()
        }

        binding.btnSaveCurrentCurve.setOnClickListener {
            showSavePresetDialog()
        }

        binding.btnImportPreset.setOnClickListener {
            try {
                importFileLauncher.launch("*/*")
            } catch (e: Exception) {
                try {
                    importFileLauncher.launch("application/json")
                } catch (e2: Exception) {
                    Toast.makeText(requireContext(), "No file manager found", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnPasteClipboard.setOnClickListener {
            importPresetFromClipboard()
        }

        adapter = UserEqPresetAdapter(
            activePresetName = fxController.currentPresetName,
            onPresetClick = { preset ->
                fxController.applyUserPreset(preset)
                onPresetApplied?.invoke()
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                Toast.makeText(requireContext(), "Applied: ${preset.name}", Toast.LENGTH_SHORT).show()
                dismiss()
            },
            onEditClick = { preset ->
                showEditPresetDialog(preset)
            },
            onExportClick = { preset ->
                exportPreset(preset)
            },
            onDeleteClick = { preset ->
                confirmDeletePreset(preset)
            }
        )

        binding.rvUserPresets.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUserPresets.adapter = adapter

        refreshPresetsList()
    }

    private fun refreshPresetsList() {
        val app = requireActivity().application as SpindleApp
        val presets = app.userEqPresetManager.getPresets()
        adapter.submitList(presets, fxController.currentPresetName)
        binding.tvEmptyPresets.visibility = if (presets.isEmpty()) View.VISIBLE else View.GONE
        binding.rvUserPresets.visibility = if (presets.isEmpty()) View.GONE else View.VISIBLE

        val activePreset = app.userEqPresetManager.findPresetByName(fxController.currentPresetName)
        if (activePreset != null && !activePreset.isBuiltIn) {
            binding.btnUpdateActiveCurve.visibility = View.VISIBLE
            binding.btnUpdateActiveCurve.text = "OVERWRITE \"${activePreset.name.uppercase()}\""
            binding.btnUpdateActiveCurve.setOnClickListener {
                confirmOverwritePreset(activePreset)
            }
        } else {
            binding.btnUpdateActiveCurve.visibility = View.GONE
        }
    }

    private fun confirmOverwritePreset(activePreset: UserEqPreset) {
        val app = requireActivity().application as SpindleApp
        AlertDialog.Builder(requireContext())
            .setTitle("Overwrite Preset")
            .setMessage("Update \"${activePreset.name}\" with current slider gains & DSP settings?")
            .setPositiveButton("Overwrite") { _, _ ->
                val updated = fxController.captureCurrentStateAsPreset(
                    name = activePreset.name,
                    description = activePreset.description
                ).copy(id = activePreset.id, isBuiltIn = false)
                val success = app.userEqPresetManager.saveOrUpdatePreset(updated, overwriteExistingName = true)
                if (success) {
                    fxController.applyUserPreset(updated)
                    onPresetApplied?.invoke()
                    Toast.makeText(requireContext(), "Preset \"${updated.name}\" updated!", Toast.LENGTH_SHORT).show()
                    refreshPresetsList()
                } else {
                    Toast.makeText(requireContext(), "Failed to update preset", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showEditPresetDialog(preset: UserEqPreset) {
        val context = requireContext()
        val app = requireActivity().application as SpindleApp
        val dialogBinding = DialogSaveUserEqPresetBinding.inflate(LayoutInflater.from(context))

        dialogBinding.etPresetName.setText(preset.name)
        dialogBinding.etPresetDescription.setText(preset.description)
        dialogBinding.btnConfirmSavePreset.text = "UPDATE"
        dialogBinding.tvSaveCurrentSummary.text = "EDIT PROFILE METADATA"
        dialogBinding.tvSaveBandsSummary.text = "Preset ID: ${preset.id.take(8)}..."

        val dialog = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.btnCancelSavePreset.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirmSavePreset.setOnClickListener {
            val newName = dialogBinding.etPresetName.text?.toString()?.trim().orEmpty()
            if (newName.isEmpty()) {
                dialogBinding.etPresetName.error = "Name cannot be empty"
                return@setOnClickListener
            }
            val newDesc = dialogBinding.etPresetDescription.text?.toString()?.trim().orEmpty()
            val success = app.userEqPresetManager.renamePreset(preset.id, newName, newDesc)
            if (success) {
                if (fxController.currentPresetName.equals(preset.name, ignoreCase = true)) {
                    fxController.updateCurrentPresetName(newName)
                }
                onPresetApplied?.invoke()
                dialog.dismiss()
                Toast.makeText(context, "Preset updated!", Toast.LENGTH_SHORT).show()
                refreshPresetsList()
            } else {
                Toast.makeText(context, "Failed to update preset", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun importPresetFromClipboard() {
        val app = requireActivity().application as SpindleApp
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
        if (clip.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Clipboard is empty. Copy AutoEq or Spindle JSON first.", Toast.LENGTH_SHORT).show()
            return
        }

        val preset = app.userEqPresetManager.importPreset(clip, "AutoEq Target")
        if (preset != null) {
            val success = app.userEqPresetManager.savePreset(preset)
            if (success) {
                fxController.applyUserPreset(preset)
                onPresetApplied?.invoke()
                Toast.makeText(requireContext(), "Imported & Applied: ${preset.name}", Toast.LENGTH_SHORT).show()
                refreshPresetsList()
            } else {
                Toast.makeText(requireContext(), "Failed to save imported preset", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "Unrecognized EQ format in clipboard (AutoEq GraphicEQ or Spindle JSON required)", Toast.LENGTH_LONG).show()
        }
    }

    private fun showSavePresetDialog() {
        val context = requireContext()
        val app = requireActivity().application as SpindleApp
        val dialogBinding = DialogSaveUserEqPresetBinding.inflate(LayoutInflater.from(context))

        val modeText = if (fxController.isParametricMode) "PARAMETRIC" else "10-BAND ISO"
        val bassPct = (fxController.bassBoostStrength / 10)
        val crossfeedPct = (fxController.crossfeedStrength / 10)
        dialogBinding.tvSaveCurrentSummary.text = "MODE: $modeText • BASS: +$bassPct% • CROSSFEED: $crossfeedPct%"

        val gains = fxController.isoBandsGainDb
        val sb = StringBuilder("31Hz..16kHz: ")
        for (i in 0 until 10) {
            val g = gains[i]
            val sign = if (g > 0) "+" else ""
            sb.append(String.format(Locale.US, "%s%.1f", sign, g))
            if (i < 9) sb.append(", ") else sb.append(" dB")
        }
        dialogBinding.tvSaveBandsSummary.text = sb.toString()

        val dialog = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.btnCancelSavePreset.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirmSavePreset.setOnClickListener {
            val name = dialogBinding.etPresetName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                dialogBinding.etPresetName.error = "Name cannot be empty"
                return@setOnClickListener
            }

            val desc = dialogBinding.etPresetDescription.text?.toString()?.trim().orEmpty()
            val newPreset = fxController.captureCurrentStateAsPreset(name = name, description = desc)
            val success = app.userEqPresetManager.saveOrUpdatePreset(newPreset, overwriteExistingName = true)
            if (success) {
                fxController.applyUserPreset(newPreset)
                onPresetApplied?.invoke()
                dialog.dismiss()
                Toast.makeText(context, "Preset \"$name\" saved!", Toast.LENGTH_SHORT).show()
                refreshPresetsList()
            } else {
                Toast.makeText(context, "Failed to save preset", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun exportPreset(preset: UserEqPreset) {
        val app = requireActivity().application as SpindleApp
        val jsonString = app.userEqPresetManager.exportPresetToJson(preset)

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, jsonString)
            putExtra(Intent.EXTRA_SUBJECT, "Spindle EQ - ${preset.name}")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export EQ Preset: ${preset.name}")
        startActivity(shareIntent)
    }

    private fun confirmDeletePreset(preset: UserEqPreset) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Preset")
            .setMessage("Delete \"${preset.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                val app = requireActivity().application as SpindleApp
                app.userEqPresetManager.deletePreset(preset.id)
                Toast.makeText(requireContext(), "Preset deleted", Toast.LENGTH_SHORT).show()
                refreshPresetsList()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun importPresetFromUri(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val content = inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            if (content.isNullOrBlank()) {
                Toast.makeText(requireContext(), "File is empty", Toast.LENGTH_SHORT).show()
                return
            }

            val app = requireActivity().application as SpindleApp
            val preset = app.userEqPresetManager.importPreset(content, "Imported Target")
            if (preset != null) {
                app.userEqPresetManager.savePreset(preset)
                Toast.makeText(requireContext(), "Imported: ${preset.name}", Toast.LENGTH_SHORT).show()
                refreshPresetsList()
            } else {
                Toast.makeText(requireContext(), "Invalid Spindle EQ or AutoEq preset file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error importing preset: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // =========================================================================
    // RECYCLER VIEW ADAPTER
    // =========================================================================
    private class UserEqPresetAdapter(
        private var activePresetName: String,
        private val onPresetClick: (UserEqPreset) -> Unit,
        private val onEditClick: (UserEqPreset) -> Unit,
        private val onExportClick: (UserEqPreset) -> Unit,
        private val onDeleteClick: (UserEqPreset) -> Unit
    ) : RecyclerView.Adapter<UserEqPresetAdapter.ViewHolder>() {

        private val items = mutableListOf<UserEqPreset>()

        fun submitList(newItems: List<UserEqPreset>, currentActive: String) {
            items.clear()
            items.addAll(newItems)
            activePresetName = currentActive
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemUserEqPresetBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(private val b: ItemUserEqPresetBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: UserEqPreset) {
                b.tvPresetName.text = item.name
                b.tvPresetDescription.visibility = if (item.description.isNotBlank()) View.VISIBLE else View.GONE
                b.tvPresetDescription.text = item.description

                val isActive = item.name.equals(activePresetName, ignoreCase = true)
                b.tvActiveBadge.visibility = if (isActive) View.VISIBLE else View.GONE

                b.tvPresetModeBadge.text = if (item.isParametric) "PARAMETRIC" else "10-BAND ISO"
                b.tvPresetBassBadge.text = "BASS: +${item.bassBoost / 10}%"
                b.tvPresetCrossfeedBadge.text = "CROSSFEED: ${item.crossfeedStrength / 10}%"

                val sb = StringBuilder("31Hz..16kHz: ")
                for (i in 0 until item.gainsDb.size.coerceAtMost(10)) {
                    val g = item.gainsDb[i]
                    val sign = if (g > 0) "+" else ""
                    sb.append(String.format(Locale.US, "%s%.1f", sign, g))
                    if (i < 9) sb.append(", ") else sb.append(" dB")
                }
                b.tvPresetCurveSummary.text = sb.toString()

                b.btnEditPreset.visibility = if (item.isBuiltIn) View.GONE else View.VISIBLE
                b.btnDeletePreset.visibility = if (item.isBuiltIn) View.GONE else View.VISIBLE

                b.containerPresetItem.setOnClickListener {
                    onPresetClick(item)
                }

                b.btnEditPreset.setOnClickListener {
                    onEditClick(item)
                }

                b.btnExportPreset.setOnClickListener {
                    onExportClick(item)
                }

                b.btnDeletePreset.setOnClickListener {
                    onDeleteClick(item)
                }
            }
        }
    }
}
