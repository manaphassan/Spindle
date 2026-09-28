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
                importFileLauncher.launch("application/json")
            } catch (e: Exception) {
                try {
                    importFileLauncher.launch("*/*")
                } catch (e2: Exception) {
                    Toast.makeText(requireContext(), "No file manager found", Toast.LENGTH_SHORT).show()
                }
            }
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
            val success = app.userEqPresetManager.savePreset(newPreset)
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
            .setNegativeButton("Cancel", null)
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
            val preset = app.userEqPresetManager.importPresetFromJson(content)
            if (preset != null) {
                app.userEqPresetManager.savePreset(preset)
                Toast.makeText(requireContext(), "Imported: ${preset.name}", Toast.LENGTH_SHORT).show()
                refreshPresetsList()
            } else {
                Toast.makeText(requireContext(), "Invalid Spindle EQ preset file", Toast.LENGTH_SHORT).show()
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

                b.btnDeletePreset.visibility = if (item.isBuiltIn) View.GONE else View.VISIBLE

                b.containerPresetItem.setOnClickListener {
                    onPresetClick(item)
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
