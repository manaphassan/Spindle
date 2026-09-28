package com.hana.spindle.playback

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Model representing a user-defined custom Equalizer & DSP tuning profile.
 * Tailored for specific IEMs, headphones, or personalized acoustic targets.
 */
data class UserEqPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val gainsDb: List<Float>, // 10 ISO center frequencies (-15.0f to +15.0f dB)
    val qFactors: List<Float> = List(10) { 1.414f }, // 10 Parametric Q factors (0.5 to 6.0)
    val isParametric: Boolean = false,
    val bassBoost: Int = 0, // 0 to 1000
    val crossfeedStrength: Int = 0, // 0 to 1000
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("schemaVersion", 1)
        json.put("id", id)
        json.put("name", name)
        json.put("description", description)
        
        val gainsArray = JSONArray()
        for (g in gainsDb) {
            gainsArray.put(g.toDouble())
        }
        json.put("gainsDb", gainsArray)

        val qArray = JSONArray()
        for (q in qFactors) {
            qArray.put(q.toDouble())
        }
        json.put("qFactors", qArray)

        json.put("isParametric", isParametric)
        json.put("bassBoost", bassBoost)
        json.put("crossfeedStrength", crossfeedStrength)
        json.put("isBuiltIn", isBuiltIn)
        json.put("createdAt", createdAt)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): UserEqPreset {
            val id = json.optString("id", UUID.randomUUID().toString())
            val name = json.optString("name", "Custom Preset")
            val description = json.optString("description", "")

            val gainsArray = json.optJSONArray("gainsDb")
            val gains = mutableListOf<Float>()
            if (gainsArray != null) {
                for (i in 0 until gainsArray.length()) {
                    gains.add(gainsArray.optDouble(i, 0.0).toFloat())
                }
            }
            // Pad or trim to exactly 10 bands
            while (gains.size < 10) gains.add(0.0f)
            val finalGains = gains.take(10)

            val qArray = json.optJSONArray("qFactors")
            val qFactors = mutableListOf<Float>()
            if (qArray != null) {
                for (i in 0 until qArray.length()) {
                    qFactors.add(qArray.optDouble(i, 1.414).toFloat())
                }
            }
            while (qFactors.size < 10) qFactors.add(1.414f)
            val finalQ = qFactors.take(10)

            val isParametric = json.optBoolean("isParametric", false)
            val bassBoost = json.optInt("bassBoost", 0)
            val crossfeedStrength = json.optInt("crossfeedStrength", 0)
            val isBuiltIn = json.optBoolean("isBuiltIn", false)
            val createdAt = json.optLong("createdAt", System.currentTimeMillis())

            return UserEqPreset(
                id = id,
                name = name,
                description = description,
                gainsDb = finalGains,
                qFactors = finalQ,
                isParametric = isParametric,
                bassBoost = bassBoost,
                crossfeedStrength = crossfeedStrength,
                isBuiltIn = isBuiltIn,
                createdAt = createdAt
            )
        }
    }
}
