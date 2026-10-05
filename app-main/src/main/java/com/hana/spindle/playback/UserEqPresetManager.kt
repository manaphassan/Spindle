package com.hana.spindle.playback

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Storage manager for custom User EQ Presets and Headphone Acoustic Profiles.
 * Persists presets as a local JSON file in internal app storage.
 */
class UserEqPresetManager(
    private val context: Context? = null,
    private val baseDir: File? = null
) {

    private val presetFile: File
        get() = File(baseDir ?: context?.filesDir ?: File("."), "user_eq_presets.json")

    @Volatile
    private var cachedPresets: MutableList<UserEqPreset>? = null

    /**
     * Retrieves all saved user EQ presets, ordered by creation time descending.
     * Automatically populates reference audiophile profiles on initial launch.
     */
    @Synchronized
    fun getPresets(): List<UserEqPreset> {
        cachedPresets?.let { return it.toList() }

        val loaded = loadFromDisk()
        val defaults = createDefaultReferencePresets()
        if (loaded.isEmpty()) {
            saveToDisk(defaults)
            cachedPresets = defaults.toMutableList()
            return defaults
        }

        // Automatically merge any newly added built-in reference profiles
        val merged = loaded.toMutableList()
        var updated = false
        for (d in defaults) {
            if (merged.none { it.name.equals(d.name, ignoreCase = true) }) {
                merged.add(d)
                updated = true
            }
        }
        if (updated) {
            saveToDisk(merged)
        }

        cachedPresets = merged
        return merged
    }

    /**
     * Finds a preset by name (case-insensitive).
     */
    @Synchronized
    fun findPresetByName(name: String): UserEqPreset? {
        return getPresets().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
    }

    /**
     * Saves or updates a user EQ preset.
     */
    @Synchronized
    fun savePreset(preset: UserEqPreset): Boolean {
        return saveOrUpdatePreset(preset, overwriteExistingName = false)
    }

    /**
     * Saves or updates a user EQ preset with optional overwrite of same-named preset.
     */
    @Synchronized
    fun saveOrUpdatePreset(preset: UserEqPreset, overwriteExistingName: Boolean = false): Boolean {
        val current = getPresets().toMutableList()
        val existingIndexById = current.indexOfFirst { it.id == preset.id }
        val targetIndex = if (existingIndexById >= 0) {
            existingIndexById
        } else if (overwriteExistingName) {
            current.indexOfFirst { it.name.equals(preset.name.trim(), ignoreCase = true) }
        } else {
            -1
        }

        if (targetIndex >= 0) {
            val old = current[targetIndex]
            current[targetIndex] = preset.copy(id = old.id, isBuiltIn = old.isBuiltIn)
        } else {
            current.add(0, preset)
        }
        val success = saveToDisk(current)
        if (success) {
            cachedPresets = current
        }
        return success
    }

    /**
     * Renames a preset and updates its acoustic description.
     */
    @Synchronized
    fun renamePreset(id: String, newName: String, newDesc: String): Boolean {
        val current = getPresets().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return false
        val old = current[index]
        current[index] = old.copy(name = newName.trim(), description = newDesc.trim())
        val success = saveToDisk(current)
        if (success) {
            cachedPresets = current
        }
        return success
    }

    /**
     * Deletes a user preset by ID.
     */
    @Synchronized
    fun deletePreset(id: String): Boolean {
        val current = getPresets().toMutableList()
        val removed = current.removeAll { it.id == id && !it.isBuiltIn }
        if (removed) {
            val success = saveToDisk(current)
            if (success) {
                cachedPresets = current
            }
            return success
        }
        return false
    }

    /**
     * Exports a preset to an external JSON string formatted for file sharing.
     */
    fun exportPresetToJson(preset: UserEqPreset): String {
        return preset.toJson().toString(2)
    }

    /**
     * Imports a preset from a raw string, supporting both Spindle JSON and
     * standard AutoEq / Squiglink GraphicEQ formats.
     */
    fun importPreset(content: String, fallbackName: String = "Imported EQ"): UserEqPreset? {
        val trimmed = content.trim()
        if (trimmed.startsWith("{")) {
            val fromJson = importPresetFromJson(trimmed)
            if (fromJson != null) return fromJson
        }
        return parseAutoEqGraphicEq(trimmed, fallbackName)
    }

    /**
     * Imports a preset from a raw JSON string.
     */
    fun importPresetFromJson(jsonString: String): UserEqPreset? {
        return try {
            val json = JSONObject(jsonString)
            UserEqPreset.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse imported preset JSON", e)
            null
        }
    }

    /**
     * Parses standard AutoEq / Squiglink GraphicEQ string format:
     * e.g. "GraphicEQ: 20 0.0; 25 0.5; 31.5 1.2; 63 2.5; ... 16000 -2.0"
     * Interpolates to the 10 standard ISO center frequencies (31Hz..16kHz) and clamps to [-12, +12] dB.
     */
    fun parseAutoEqGraphicEq(rawText: String, presetName: String): UserEqPreset? {
        try {
            var text = rawText.trim()
            if (text.startsWith("GraphicEQ:", ignoreCase = true)) {
                text = text.substring(10).trim()
            }

            // Split by semicolon, comma (if pairs are semicolon-separated), or newlines
            val tokens = text.split(Regex("[;\\r\\n]+"))
            val rawPoints = mutableListOf<Pair<Float, Float>>()

            for (token in tokens) {
                val cleaned = token.trim()
                if (cleaned.isEmpty()) continue
                val parts = cleaned.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
                if (parts.size >= 2) {
                    val freq = parts[0].toFloatOrNull()
                    val gain = parts[1].toFloatOrNull()
                    if (freq != null && gain != null && freq > 0f) {
                        rawPoints.add(Pair(freq, gain))
                    }
                }
            }

            if (rawPoints.size < 3) {
                Log.w(TAG, "AutoEq text contained insufficient frequency points: ${rawPoints.size}")
                return null
            }

            // Sort points by frequency ascending
            rawPoints.sortBy { it.first }

            val isoCenterFreqs = floatArrayOf(
                31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
            )

            val calculatedGains = mutableListOf<Float>()

            for (targetFreq in isoCenterFreqs) {
                val gain = interpolateGainAtFrequency(rawPoints, targetFreq)
                val clamped = gain.coerceIn(-12.0f, 12.0f)
                val rounded = Math.round(clamped * 10f) / 10f
                calculatedGains.add(rounded)
            }

            return UserEqPreset(
                name = presetName.ifBlank { "AutoEq Target" },
                description = "Imported AutoEq / Squiglink GraphicEQ curve (${rawPoints.size} measurement points)",
                gainsDb = calculatedGains,
                qFactors = List(10) { 1.414f },
                isParametric = false,
                bassBoost = 0,
                crossfeedStrength = 0,
                isBuiltIn = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing AutoEq GraphicEQ text", e)
            return null
        }
    }

    private fun interpolateGainAtFrequency(points: List<Pair<Float, Float>>, targetFreq: Float): Float {
        if (targetFreq <= points.first().first) return points.first().second
        if (targetFreq >= points.last().first) return points.last().second

        for (i in 0 until points.size - 1) {
            val (f1, g1) = points[i]
            val (f2, g2) = points[i + 1]

            if (targetFreq in f1..f2) {
                if (f1 == f2) return g1
                // Logarithmic frequency interpolation
                val log1 = kotlin.math.ln(f1)
                val log2 = kotlin.math.ln(f2)
                val logT = kotlin.math.ln(targetFreq)
                val factor = (logT - log1) / (log2 - log1)
                return g1 + (factor * (g2 - g1)).toFloat()
            }
        }
        return 0f
    }

    private fun loadFromDisk(): List<UserEqPreset> {
        val file = presetFile
        if (!file.exists()) return emptyList()

        return try {
            val content = file.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(content)
            val list = mutableListOf<UserEqPreset>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(UserEqPreset.fromJson(obj))
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading presets from disk", e)
            emptyList()
        }
    }

    private fun saveToDisk(presets: List<UserEqPreset>): Boolean {
        return try {
            val jsonArray = JSONArray()
            for (p in presets) {
                jsonArray.put(p.toJson())
            }
            presetFile.writeText(jsonArray.toString(2), Charsets.UTF_8)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving presets to disk", e)
            false
        }
    }

    /**
     * Reference audiophile target profiles calibrated for legendary headphones and IEMs.
     */
    private fun createDefaultReferencePresets(): List<UserEqPreset> {
        return listOf(
            UserEqPreset(
                name = "Sennheiser HD 600",
                description = "Harman Target with sub-bass extension & smooth treble",
                gainsDb = listOf(4.5f, 3.2f, 1.2f, 0.0f, -0.5f, 0.0f, 1.0f, -1.5f, -0.8f, 1.2f),
                qFactors = listOf(1.414f, 1.414f, 1.414f, 1.414f, 1.414f, 1.414f, 2.0f, 2.5f, 1.414f, 1.414f),
                centerFreqsHz = listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000),
                isParametric = true,
                bassBoost = 200,
                crossfeedStrength = 300,
                isBuiltIn = true
            ),
            UserEqPreset(
                name = "Sony WH-1000XM4/XM5",
                description = "Harman Over-Ear correction: tames 200Hz boom, reveals upper mid clarity",
                gainsDb = listOf(1.0f, -1.5f, -3.5f, -2.0f, 0.5f, 1.5f, 3.0f, 2.5f, 1.0f, 0.5f),
                qFactors = listOf(1.414f, 1.414f, 2.2f, 1.8f, 1.414f, 1.414f, 1.8f, 2.0f, 1.414f, 1.414f),
                centerFreqsHz = listOf(31, 63, 160, 300, 600, 1200, 2500, 4800, 8000, 16000),
                isParametric = true,
                bassBoost = 0,
                crossfeedStrength = 200,
                isBuiltIn = true
            ),
            UserEqPreset(
                name = "Audio-Technica ATH-M50x",
                description = "Flatter studio target: cleans up 200Hz mud and smooths 9kHz sibilance",
                gainsDb = listOf(1.5f, 0.5f, -1.8f, -2.2f, -0.5f, 0.5f, 1.0f, 0.5f, -2.5f, -1.0f),
                qFactors = listOf(1.414f, 1.414f, 1.8f, 2.0f, 1.414f, 1.414f, 1.414f, 1.8f, 3.0f, 1.414f),
                centerFreqsHz = listOf(31, 63, 180, 280, 500, 1000, 2000, 4000, 9000, 16000),
                isParametric = true,
                bassBoost = 0,
                crossfeedStrength = 150,
                isBuiltIn = true
            ),
            UserEqPreset(
                name = "HiFiMAN Sundara",
                description = "Planar magnetic linear sub-bass lift and 6kHz resonance notch",
                gainsDb = listOf(4.0f, 3.0f, 1.5f, 0.0f, 0.0f, 0.5f, -0.5f, 1.0f, -2.0f, 1.0f),
                qFactors = listOf(1.0f, 1.2f, 1.414f, 1.414f, 1.414f, 1.414f, 1.414f, 1.8f, 3.5f, 1.414f),
                centerFreqsHz = listOf(31, 63, 125, 250, 500, 1000, 2000, 4200, 6000, 14000),
                isParametric = true,
                bassBoost = 100,
                crossfeedStrength = 250,
                isBuiltIn = true
            ),
            UserEqPreset(
                name = "Beyerdynamic DT 770/990",
                description = "Mount Beyer treble notch (-4dB @ 8kHz) and clean sub-bass curve",
                gainsDb = listOf(2.5f, 1.5f, 0.0f, 0.5f, 1.0f, 0.5f, 0.0f, -1.0f, -4.0f, -1.5f),
                qFactors = listOf(1.414f, 1.414f, 1.414f, 1.414f, 1.414f, 1.414f, 1.414f, 2.0f, 4.0f, 2.0f),
                centerFreqsHz = listOf(31, 63, 125, 250, 500, 1000, 2000, 4500, 8200, 16000),
                isParametric = true,
                bassBoost = 50,
                crossfeedStrength = 200,
                isBuiltIn = true
            ),
            UserEqPreset(
                name = "Sony IER-M9",
                description = "Audiophile Studio Monitor & vocal intimacy profile",
                gainsDb = listOf(1.0f, 0.5f, 0.0f, 0.5f, 1.2f, 1.5f, 0.5f, -1.0f, 1.5f, 2.0f),
                qFactors = List(10) { 1.414f },
                centerFreqsHz = listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000),
                isParametric = false,
                bassBoost = 100,
                crossfeedStrength = 200,
                isBuiltIn = true
            ),
            UserEqPreset(
                name = "Moondrop Blessing 2: Dusk",
                description = "Crinacle IEF Neutral Target with dynamic sub-bass shelf",
                gainsDb = listOf(2.5f, 1.8f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 0.0f, -1.2f, 0.5f),
                qFactors = List(10) { 1.414f },
                centerFreqsHz = listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000),
                isParametric = false,
                bassBoost = 150,
                crossfeedStrength = 150,
                isBuiltIn = true
            ),
            UserEqPreset(
                name = "Vintage Tube Warmth",
                description = "Harmonic 2nd-order analog warmth & soft high roll-off",
                gainsDb = listOf(2.2f, 1.8f, 1.0f, 0.5f, 0.8f, 0.0f, -0.5f, -1.2f, -2.0f, -3.2f),
                qFactors = List(10) { 1.414f },
                centerFreqsHz = listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000),
                isParametric = false,
                bassBoost = 250,
                crossfeedStrength = 400,
                isBuiltIn = true
            )
        )
    }

    companion object {
        private const val TAG = "UserEqPresetManager"
    }
}
