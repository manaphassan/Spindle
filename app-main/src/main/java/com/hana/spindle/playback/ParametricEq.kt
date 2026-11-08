package com.hana.spindle.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class PeqType(val code: String) {
    PEAK("PK"), LOW_SHELF("LSC"), HIGH_SHELF("HSC"), HIGH_PASS("HPQ"), LOW_PASS("LPQ");

    companion object {
        fun fromCode(c: String): PeqType? = when (c.uppercase(Locale.ROOT)) {
            "PK", "PEQ" -> PEAK
            "LSC", "LS" -> LOW_SHELF
            "HSC", "HS" -> HIGH_SHELF
            "HP", "HPQ" -> HIGH_PASS
            "LP", "LPQ" -> LOW_PASS
            else -> null
        }
    }
}

data class PeqBand(
    val type: PeqType = PeqType.PEAK,
    val freqHz: Float = 1000f,
    val gainDb: Float = 0f,
    val q: Float = 1.0f,
    val enabled: Boolean = true
)

data class PeqSettings(
    val enabled: Boolean = false,
    val preampDb: Float = 0f,
    val bands: List<PeqBand> = emptyList()
) {
    /** True when processing would actually change the signal. */
    val isEffective: Boolean
        get() = enabled && (preampDb != 0f || bands.any { it.enabled && (it.gainDb != 0f || it.type == PeqType.HIGH_PASS || it.type == PeqType.LOW_PASS) })

    /** Preamp that keeps the largest boost from clipping (0 dB when nothing boosts). */
    fun autoPreampDb(): Float = -(bands.filter { it.enabled }.maxOfOrNull { it.gainDb } ?: 0f).coerceAtLeast(0f)
}

/** Normalised biquad coefficients (a0 == 1). */
data class BiquadCoeffs(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double)

object PeqMath {
    const val MAX_BANDS = 16

    /** RBJ audio-EQ-cookbook biquad, Q-form shelves (matches AutoEq / Equalizer APO). */
    fun coefficients(band: PeqBand, sampleRate: Int): BiquadCoeffs {
        val f = band.freqHz.toDouble().coerceIn(10.0, sampleRate * 0.49)
        val q = band.q.toDouble().coerceIn(0.1, 20.0)
        val w0 = 2.0 * PI * f / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a = 10.0.pow(band.gainDb / 40.0)
        val b0: Double; val b1: Double; val b2: Double; val a0: Double; val a1: Double; val a2: Double
        when (band.type) {
            PeqType.PEAK -> {
                b0 = 1 + alpha * a; b1 = -2 * cosW; b2 = 1 - alpha * a
                a0 = 1 + alpha / a; a1 = -2 * cosW; a2 = 1 - alpha / a
            }
            PeqType.LOW_SHELF -> {
                val beta = 2 * sqrt(a) * alpha
                b0 = a * ((a + 1) - (a - 1) * cosW + beta)
                b1 = 2 * a * ((a - 1) - (a + 1) * cosW)
                b2 = a * ((a + 1) - (a - 1) * cosW - beta)
                a0 = (a + 1) + (a - 1) * cosW + beta
                a1 = -2 * ((a - 1) + (a + 1) * cosW)
                a2 = (a + 1) + (a - 1) * cosW - beta
            }
            PeqType.HIGH_SHELF -> {
                val beta = 2 * sqrt(a) * alpha
                b0 = a * ((a + 1) + (a - 1) * cosW + beta)
                b1 = -2 * a * ((a - 1) + (a + 1) * cosW)
                b2 = a * ((a + 1) + (a - 1) * cosW - beta)
                a0 = (a + 1) - (a - 1) * cosW + beta
                a1 = 2 * ((a - 1) - (a + 1) * cosW)
                a2 = (a + 1) - (a - 1) * cosW - beta
            }
            PeqType.HIGH_PASS -> {
                b0 = (1 + cosW) / 2; b1 = -(1 + cosW); b2 = (1 + cosW) / 2
                a0 = 1 + alpha; a1 = -2 * cosW; a2 = 1 - alpha
            }
            PeqType.LOW_PASS -> {
                b0 = (1 - cosW) / 2; b1 = 1 - cosW; b2 = (1 - cosW) / 2
                a0 = 1 + alpha; a1 = -2 * cosW; a2 = 1 - alpha
            }
        }
        return BiquadCoeffs(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    /** Magnitude response in dB of one band at [freqHz]; used for tests and the curve preview. */
    fun magnitudeDb(c: BiquadCoeffs, freqHz: Double, sampleRate: Int): Double {
        val w = 2.0 * PI * freqHz / sampleRate
        val cw = cos(w); val sw = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = c.b0 + c.b1 * cw + c.b2 * c2
        val ni = -(c.b1 * sw + c.b2 * s2)
        val dr = 1.0 + c.a1 * cw + c.a2 * c2
        val di = -(c.a1 * sw + c.a2 * s2)
        return 10.0 * kotlin.math.log10((nr * nr + ni * ni) / (dr * dr + di * di))
    }
}

/** Parses AutoEq / Equalizer APO "ParametricEQ.txt" text. */
object AutoEqParametricParser {
    private val preampRe = Regex("""(?i)^\s*preamp\s*:\s*(-?\d+(?:\.\d+)?)\s*db""")
    private val filterRe = Regex(
        """(?i)^\s*filter\s*\d*\s*:\s*(on|off)\s+([a-z]+)\s+fc\s+(\d+(?:\.\d+)?)\s*hz(?:\s+gain\s+(-?\d+(?:\.\d+)?)\s*db)?(?:\s+q\s+(\d+(?:\.\d+)?))?"""
    )

    fun parse(text: String): PeqSettings? {
        var preamp = 0f
        val bands = ArrayList<PeqBand>()
        for (line in text.lineSequence()) {
            preampRe.find(line)?.let { preamp = it.groupValues[1].toFloat(); return@let }
            val m = filterRe.find(line) ?: continue
            val type = PeqType.fromCode(m.groupValues[2]) ?: continue
            val gain = m.groupValues[4].ifEmpty { "0" }.toFloat()
            val q = m.groupValues[5].ifEmpty { if (type == PeqType.PEAK) "1.0" else "0.707" }.toFloat()
            bands += PeqBand(type, m.groupValues[3].toFloat(), gain, q, m.groupValues[1].equals("on", true))
            if (bands.size >= PeqMath.MAX_BANDS) break
        }
        return if (bands.isEmpty()) null else PeqSettings(enabled = true, preampDb = preamp, bands = bands)
    }
}

/** Serialises settings as AutoEq / Equalizer APO text so users can copy, edit and re-import. */
fun PeqSettings.toAutoEqText(): String = buildString {
    append("Preamp: ").append(String.format(Locale.US, "%.1f", preampDb)).append(" dB\n")
    bands.forEachIndexed { i, b ->
        append("Filter ").append(i + 1).append(": ").append(if (b.enabled) "ON " else "OFF ").append(b.type.code)
            .append(String.format(Locale.US, " Fc %.0f Hz Gain %.1f dB Q %.2f\n", b.freqHz, b.gainDb, b.q))
    }
}

/** Shared, persisted parametric EQ settings; processors read a versioned snapshot. */
object ParametricEq {
    private const val PREFS = "spindle_prefs"
    private const val KEY = "pref_peq_settings_json"

    private val _settings = MutableStateFlow(PeqSettings())
    val settings: StateFlow<PeqSettings> = _settings.asStateFlow()

    @Volatile var version: Int = 0
        private set

    val isActive: Boolean get() = _settings.value.isEffective

    fun load(context: Context) {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        fromJson(raw)?.let { update(it, persistContext = null) }
    }

    fun update(settings: PeqSettings, persistContext: Context?) {
        val clean = settings.copy(
            preampDb = settings.preampDb.coerceIn(-24f, 12f),
            bands = settings.bands.take(PeqMath.MAX_BANDS).map {
                it.copy(freqHz = it.freqHz.coerceIn(10f, 24000f), gainDb = it.gainDb.coerceIn(-24f, 24f), q = it.q.coerceIn(0.1f, 20f))
            }
        )
        _settings.value = clean
        version++
        persistContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY, toJson(clean))?.apply()
    }

    fun toJson(s: PeqSettings): String = JSONObject().apply {
        put("enabled", s.enabled)
        put("preamp", s.preampDb.toDouble())
        put("bands", JSONArray().also { arr ->
            s.bands.forEach {
                arr.put(JSONObject().put("t", it.type.name).put("f", it.freqHz.toDouble()).put("g", it.gainDb.toDouble())
                    .put("q", it.q.toDouble()).put("on", it.enabled))
            }
        })
    }.toString()

    fun fromJson(raw: String): PeqSettings? = try {
        val o = JSONObject(raw)
        val arr = o.getJSONArray("bands")
        PeqSettings(
            enabled = o.optBoolean("enabled", false),
            preampDb = o.optDouble("preamp", 0.0).toFloat(),
            bands = (0 until arr.length()).map { i ->
                val b = arr.getJSONObject(i)
                PeqBand(PeqType.valueOf(b.getString("t")), b.getDouble("f").toFloat(), b.getDouble("g").toFloat(),
                    b.getDouble("q").toFloat(), b.optBoolean("on", true))
            }
        )
    } catch (e: Exception) {
        null
    }
}

/**
 * Cascaded-biquad parametric EQ as a Media3 audio processor (16-bit and float PCM).
 * It is only active when the EQ is effective at configure time, so a flat or disabled EQ adds no
 * processing stage. Band edits apply live; switching it on mid-track applies from the next track.
 * Filter state is double precision and cleared on flush (seek / track change).
 */
@UnstableApi
class ParametricEqProcessor(
    private val settingsProvider: () -> PeqSettings = { ParametricEq.settings.value },
    private val versionProvider: () -> Int = { ParametricEq.version }
) : BaseAudioProcessor() {

    private var sampleRate = 0
    private var channels = 0
    private var encoding = C.ENCODING_INVALID
    private var coeffs: Array<BiquadCoeffs> = emptyArray()
    private var preampGain = 1.0
    private var state = emptyArray<DoubleArray>() // [channel][band * 2 + {0,1}]
    private var appliedVersion = -1

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val enc = inputAudioFormat.encoding
        if ((enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) || !settingsProvider().isEffective) {
            return AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        encoding = enc
        appliedVersion = -1
        return inputAudioFormat
    }

    override fun onFlush() {
        appliedVersion = -1
        state = emptyArray()
    }

    override fun onReset() {
        coeffs = emptyArray()
        state = emptyArray()
    }

    private fun refresh() {
        val v = versionProvider()
        if (v == appliedVersion && state.isNotEmpty()) return
        val s = settingsProvider()
        val active = s.bands.filter { it.enabled }
        val newCoeffs = Array(active.size) { PeqMath.coefficients(active[it], sampleRate) }
        if (state.size != channels || newCoeffs.size * 2 != (state.firstOrNull()?.size ?: -1)) {
            state = Array(channels) { DoubleArray(newCoeffs.size * 2) }
        }
        coeffs = newCoeffs
        preampGain = 10.0.pow(s.preampDb / 20.0)
        appliedVersion = v
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val out = replaceOutputBuffer(remaining)
        val live = settingsProvider()
        if (!live.enabled) { // switched off mid-track: pass through untouched
            out.put(inputBuffer); out.flip(); return
        }
        refresh()
        val bytes = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frames = remaining / (bytes * channels)
        for (i in 0 until frames) {
            for (ch in 0 until channels) {
                var x = if (bytes == 4) inputBuffer.getFloat().toDouble() else inputBuffer.getShort() / 32768.0
                x *= preampGain
                val st = state[ch]
                for (b in coeffs.indices) {
                    val c = coeffs[b]
                    val y = c.b0 * x + st[b * 2]
                    st[b * 2] = c.b1 * x - c.a1 * y + st[b * 2 + 1]
                    st[b * 2 + 1] = c.b2 * x - c.a2 * y
                    x = y
                }
                if (bytes == 4) out.putFloat(x.toFloat())
                else out.putShort(max(-32768, min(32767, (x * 32768.0).roundToInt())).toShort())
            }
        }
        if (inputBuffer.hasRemaining()) out.put(inputBuffer) // never drop a trailing partial frame
        out.flip()
    }
}
