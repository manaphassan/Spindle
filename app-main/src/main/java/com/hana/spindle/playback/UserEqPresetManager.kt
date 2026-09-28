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
class UserEqPresetManager(private val context: Context) {

    private val presetFile: File
        get() = File(context.filesDir, "user_eq_presets.json")

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
        if (loaded.isEmpty()) {
            val defaults = createDefaultReferencePresets()
            saveToDisk(defaults)
            cachedPresets = defaults.toMutableList()
            return defaults
        }

        cachedPresets = loaded.toMutableList()
        return loaded
    }

    /**
     * Saves or updates a user EQ preset.
     */
    @Synchronized
    fun savePreset(preset: UserEqPreset): Boolean {
        val current = getPresets().toMutableList()
        val existingIndex = current.indexOfFirst { it.id == preset.id }
        if (existingIndex >= 0) {
            current[existingIndex] = preset
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
     * Deletes a user preset by ID.
     */
    @Synchronized
    fun deletePreset(id: String): Boolean {
        val current = getPresets().toMutableList()
        val removed = current.removeAll { it.id == id }
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
                gainsDb = listOf(3.5f, 2.8f, 1.2f, 0.0f, -0.5f, 0.0f, 1.0f, -1.5f, -0.8f, 1.2f),
                qFactors = listOf(1.414f, 1.414f, 1.414f, 1.414f, 1.414f, 1.414f, 2.0f, 2.5f, 1.414f, 1.414f),
                isParametric = true,
                bassBoost = 200,
                crossfeedStrength = 300,
                isBuiltIn = true
            ),
            UserEqPreset(
                name = "Sony IER-M9",
                description = "Audiophile Studio Monitor & vocal intimacy profile",
                gainsDb = listOf(1.0f, 0.5f, 0.0f, 0.5f, 1.2f, 1.5f, 0.5f, -1.0f, 1.5f, 2.0f),
                qFactors = List(10) { 1.414f },
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
