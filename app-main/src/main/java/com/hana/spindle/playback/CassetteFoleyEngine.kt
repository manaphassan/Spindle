package com.hana.spindle.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.hana.spindle.licensing.StudioUnlockManager
import com.hana.spindle.licensing.StudioUnlockManager.StudioFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Ultra-low-latency Vintage Cassette Mechanical Foley SoundPool Engine.
 *
 * Simulates the physical acoustic behavior of classic high-end analog tape decks
 * (precision direct-drive tape heads, dual-capstan solenoids, and studio reel mechanisms):
 * 1. Play: Heavy mechanical solenoid engagement clack & head sled slide.
 * 2. Stop/Pause: Spring-loaded head release click.
 * 3. Spool/Seek: High-speed brass gear and motor flutter.
 * 4. Eject/Load: Cassette carriage open pop and well seating thud.
 * 5. Switch: Solid metal bias/Dolby toggle switch detent snap.
 *
 * Procedurally generates 16-bit 44.1kHz PCM WAV samples at runtime,
 * requiring zero external binary audio assets while providing zero-latency playback.
 */
class CassetteFoleyEngine(private val context: Context) {

    companion object {
        private const val TAG = "CassetteFoleyEngine"
        private const val SAMPLE_RATE = 44100
        const val PREF_FOLEY_ENABLED = "pref_cassette_foley_enabled"
        const val PREF_METRO_CLICKS_ENABLED = "pref_metro_clicks_enabled"

        // Sound IDs
        const val SOUND_PLAY_SOLENOID = 1
        const val SOUND_STOP_RELEASE = 2
        const val SOUND_MOTOR_SPOOL = 3
        const val SOUND_CARRIAGE_EJECT = 4
        const val SOUND_SWITCH_SNAP = 5
        const val SOUND_METRO_TICK = 6
        const val SOUND_TILE_LATCH = 7
        const val SOUND_ROTARY_RATCHET = 8
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var soundPool: SoundPool? = null
    private val soundIdMap = mutableMapOf<Int, Int>()
    private var isLoaded = false

    var isEnabled: Boolean = true
    var isMetroClicksEnabled: Boolean = true
        private set

    val haptics = SpindleHaptics(context)

    val isProceduralUnlocked: Boolean
        get() = StudioUnlockManager.isFeatureUnlocked(context, StudioFeature.PROCEDURAL_FOLEY)

    init {
        val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        isEnabled = prefs.getBoolean(PREF_FOLEY_ENABLED, true)
        isMetroClicksEnabled = prefs.getBoolean(PREF_METRO_CLICKS_ENABLED, true)

        initSoundPool()
    }

    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(audioAttributes)
            .build()

        scope.launch {
            try {
                val foleyDir = File(context.cacheDir, "foley").apply { mkdirs() }

                val filePlay = File(foleyDir, "solenoid_play.wav")
                if (!filePlay.exists()) generatePlaySolenoidWav(filePlay)

                val fileStop = File(foleyDir, "release_stop.wav")
                if (!fileStop.exists()) generateReleaseStopWav(fileStop)

                val fileSpool = File(foleyDir, "motor_spool.wav")
                if (!fileSpool.exists()) generateMotorSpoolWav(fileSpool)

                val fileEject = File(foleyDir, "carriage_eject.wav")
                if (!fileEject.exists()) generateCarriageEjectWav(fileEject)

                val fileSwitch = File(foleyDir, "switch_snap.wav")
                if (!fileSwitch.exists()) generateSwitchSnapWav(fileSwitch)

                val fileTick = File(foleyDir, "metro_tick.wav")
                if (!fileTick.exists()) generateMetroTickWav(fileTick)

                val fileLatch = File(foleyDir, "tile_latch.wav")
                if (!fileLatch.exists()) generateTileLatchWav(fileLatch)

                val fileRatchet = File(foleyDir, "rotary_ratchet.wav")
                if (!fileRatchet.exists()) generateRotaryRatchetWav(fileRatchet)

                soundPool?.let { sp ->
                    soundIdMap[SOUND_PLAY_SOLENOID] = sp.load(filePlay.absolutePath, 1)
                    soundIdMap[SOUND_STOP_RELEASE] = sp.load(fileStop.absolutePath, 1)
                    soundIdMap[SOUND_MOTOR_SPOOL] = sp.load(fileSpool.absolutePath, 1)
                    soundIdMap[SOUND_CARRIAGE_EJECT] = sp.load(fileEject.absolutePath, 1)
                    soundIdMap[SOUND_SWITCH_SNAP] = sp.load(fileSwitch.absolutePath, 1)
                    soundIdMap[SOUND_METRO_TICK] = sp.load(fileTick.absolutePath, 1)
                    soundIdMap[SOUND_TILE_LATCH] = sp.load(fileLatch.absolutePath, 1)
                    soundIdMap[SOUND_ROTARY_RATCHET] = sp.load(fileRatchet.absolutePath, 1)
                }
                isLoaded = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize procedural foley sounds", e)
            }
        }
    }

    fun playSolenoidClack() {
        haptics.vibrateSolenoid()
        val vol = if (isProceduralUnlocked) 0.45f else 0.25f
        playSound(SOUND_PLAY_SOLENOID, vol)
    }

    fun playReleaseClick() {
        haptics.vibrateRelease()
        val vol = if (isProceduralUnlocked) 0.40f else 0.20f
        playSound(SOUND_STOP_RELEASE, vol)
    }

    fun playMotorSpool() {
        haptics.vibrateRotaryRatchet()
        val vol = if (isProceduralUnlocked) 0.35f else 0.20f
        playSound(SOUND_MOTOR_SPOOL, vol)
    }

    fun playCarriageEject() {
        haptics.vibrateCarriageEject()
        val vol = if (isProceduralUnlocked) 0.50f else 0.25f
        playSound(SOUND_CARRIAGE_EJECT, vol)
    }

    fun playSwitchSnap() {
        haptics.vibrateSwitchSnap()
        val vol = if (isProceduralUnlocked) 0.38f else 0.20f
        playSound(SOUND_SWITCH_SNAP, vol)
    }

    /**
     * Crisp, ultra-short mechanical micro-click for Zune jump letters, navigation detents, and buttons.
     */
    fun playMetroTick(pitch: Float = 1.0f) {
        haptics.vibrateMetroTick()
        if (!isMetroClicksEnabled) return
        val vol = if (isProceduralUnlocked) 0.38f else 0.22f
        playSound(SOUND_METRO_TICK, vol, pitch)
    }

    /**
     * Tactile mechanical tile press and relay click for Live Tiles, App Bar icons, and action pills.
     */
    fun playTilePress(pitch: Float = 1.0f) {
        haptics.vibrateTilePress()
        if (!isMetroClicksEnabled) return
        val vol = if (isProceduralUnlocked) 0.42f else 0.25f
        playSound(SOUND_TILE_LATCH, vol, pitch)
    }

    /**
     * Tactile relay latch / release click for pinning and unpinning items to the Start Screen.
     */
    fun playPinAction(pinned: Boolean) {
        haptics.vibrateTilePress()
        if (!isMetroClicksEnabled) return
        val pitch = if (pinned) 1.25f else 0.85f
        val vol = if (isProceduralUnlocked) 0.45f else 0.28f
        playSound(SOUND_TILE_LATCH, vol, pitch)
    }

    /**
     * Stepped mechanical ratchet click for analog radio dial sweep and precision sliders.
     */
    fun playRotaryRatchet(pitch: Float = 1.0f) {
        haptics.vibrateRotaryRatchet()
        if (!isMetroClicksEnabled) return
        val vol = if (isProceduralUnlocked) 0.32f else 0.20f
        playSound(SOUND_ROTARY_RATCHET, vol, pitch)
    }

    fun startMotorSpoolLoop(initialPitch: Float = 0.9f): Int {
        if (!isEnabled || !isLoaded) return 0
        if (!isProceduralUnlocked) {
            // Free Core tier: single subtle mechanical spool burst without continuous motor looping or pitch-ramping
            playSound(SOUND_MOTOR_SPOOL, 0.25f)
            return 0
        }
        val sp = soundPool ?: return 0
        val soundId = soundIdMap[SOUND_MOTOR_SPOOL] ?: return 0
        return sp.play(soundId, 0.38f, 0.38f, 2, -1, initialPitch.coerceIn(0.5f, 2.0f))
    }

    fun setMotorSpoolPitch(streamId: Int, pitch: Float) {
        if (streamId == 0 || !isProceduralUnlocked) return
        soundPool?.setRate(streamId, pitch.coerceIn(0.5f, 2.0f))
    }

    fun stopMotorSpoolLoop(streamId: Int) {
        if (streamId == 0) return
        soundPool?.stop(streamId)
    }

    fun playAutoStopClack() {
        haptics.vibrateSolenoid()
        if (isProceduralUnlocked) {
            playSound(SOUND_PLAY_SOLENOID, 0.55f)
            playSound(SOUND_STOP_RELEASE, 0.45f)
        } else {
            playSound(SOUND_STOP_RELEASE, 0.25f)
        }
    }

    private fun playSound(soundKey: Int, volume: Float, pitch: Float = 1.0f) {
        if (!isEnabled || !isLoaded) return
        val sp = soundPool ?: return
        val soundId = soundIdMap[soundKey] ?: return
        sp.play(soundId, volume, volume, 1, 0, pitch.coerceIn(0.5f, 2.0f))
    }

    fun setFoleyEnabled(enabled: Boolean) {
        isEnabled = enabled
        val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_FOLEY_ENABLED, enabled).apply()
    }

    fun setMetroClicksEnabled(enabled: Boolean) {
        isMetroClicksEnabled = enabled
        val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_METRO_CLICKS_ENABLED, enabled).apply()
    }

    // =========================================================================
    // PROCEDURAL ACOUSTIC WAV GENERATORS (16-bit 44.1kHz PCM)
    // =========================================================================

    private fun generatePlaySolenoidWav(outputFile: File) {
        // 55ms: 5ms sharp transient click + 135Hz metallic chassis resonance
        val durationSec = 0.055f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            // 5ms initial click
            val click = if (t < 0.005f) {
                (sin(2 * PI * 3200 * t) * (1.0f - t / 0.005f)).toFloat()
            } else 0f

            // 135Hz chassis resonance with 18ms decay
            val resonance = (sin(2 * PI * 135 * t) * exp(-t / 0.018f)).toFloat()

            // 280Hz head sled sliding friction
            val friction = if (t < 0.025f) (sin(2 * PI * 280 * t) * exp(-t / 0.010f) * 0.4f).toFloat() else 0f

            val mixed = (click * 0.6f + resonance * 0.7f + friction * 0.3f).coerceIn(-1.0f, 1.0f)
            samples[i] = (mixed * 32767).toInt().toShort()
        }
        writeWavFile(outputFile, samples)
    }

    private fun generateReleaseStopWav(outputFile: File) {
        // 35ms: Sharp 2200Hz release spring click
        val durationSec = 0.035f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val click = (sin(2 * PI * 2200 * t) * exp(-t / 0.007f)).toFloat()
            val spring = (sin(2 * PI * 650 * t) * exp(-t / 0.015f) * 0.3f).toFloat()
            val mixed = (click * 0.8f + spring * 0.4f).coerceIn(-1.0f, 1.0f)
            samples[i] = (mixed * 32767).toInt().toShort()
        }
        writeWavFile(outputFile, samples)
    }

    private fun generateMotorSpoolWav(outputFile: File) {
        // 110ms: High-speed dual-gear whirr (360Hz & 720Hz harmonics)
        val durationSec = 0.110f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val motor = sin(2 * PI * 360 * t).toFloat() * 0.5f
            val gearHarmonic = sin(2 * PI * 720 * t).toFloat() * 0.25f
            val envelope = exp(-t / 0.065f).toFloat()
            val mixed = ((motor + gearHarmonic) * envelope).coerceIn(-1.0f, 1.0f)
            samples[i] = (mixed * 32767).toInt().toShort()
        }
        writeWavFile(outputFile, samples)
    }

    private fun generateCarriageEjectWav(outputFile: File) {
        // 80ms: Latch pop (1400Hz) + low carriage chassis thud (95Hz)
        val durationSec = 0.080f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val pop = (sin(2 * PI * 1400 * t) * exp(-t / 0.009f)).toFloat()
            val thud = (sin(2 * PI * 95 * t) * exp(-t / 0.035f)).toFloat()
            val mixed = (pop * 0.6f + thud * 0.7f).coerceIn(-1.0f, 1.0f)
            samples[i] = (mixed * 32767).toInt().toShort()
        }
        writeWavFile(outputFile, samples)
    }

    private fun generateSwitchSnapWav(outputFile: File) {
        // 20ms: Solid metal toggle switch detent click (3400Hz)
        val durationSec = 0.020f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val snap = (sin(2 * PI * 3400 * t) * exp(-t / 0.004f)).toFloat()
            samples[i] = (snap.coerceIn(-1.0f, 1.0f) * 32767).toInt().toShort()
        }
        writeWavFile(outputFile, samples)
    }

    private fun generateMetroTickWav(outputFile: File) {
        // 12ms: Precision mechanical detent tick (4200Hz transient + 2400Hz body resonance)
        val durationSec = 0.012f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            // 4.2kHz sharp impact with 2ms decay
            val click = (sin(2 * PI * 4200 * t) * exp(-t / 0.0022f)).toFloat()
            // 2.4kHz indexing tooth harmonic with 4.5ms decay
            val tooth = (sin(2 * PI * 2400 * t) * exp(-t / 0.0045f) * 0.45f).toFloat()
            // 900Hz micro-chassis resonance
            val body = (sin(2 * PI * 900 * t) * exp(-t / 0.006f) * 0.25f).toFloat()

            val mixed = (click * 0.75f + tooth + body).coerceIn(-1.0f, 1.0f)
            samples[i] = (mixed * 32767).toInt().toShort()
        }
        writeWavFile(outputFile, samples)
    }

    private fun generateTileLatchWav(outputFile: File) {
        // 24ms: Tactile sprung tile relay latch (3100Hz primary click + 1600Hz secondary catch + 350Hz body)
        val durationSec = 0.024f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val primary = (sin(2 * PI * 3100 * t) * exp(-t / 0.003f)).toFloat()
            // Secondary bounce catch at 4ms
            val secondary = if (t >= 0.004f) {
                val dt = t - 0.004f
                (sin(2 * PI * 1600 * dt) * exp(-dt / 0.005f) * 0.5f).toFloat()
            } else 0f
            // Low body thud
            val body = (sin(2 * PI * 350 * t) * exp(-t / 0.012f) * 0.35f).toFloat()

            val mixed = (primary * 0.7f + secondary + body).coerceIn(-1.0f, 1.0f)
            samples[i] = (mixed * 32767).toInt().toShort()
        }
        writeWavFile(outputFile, samples)
    }

    private fun generateRotaryRatchetWav(outputFile: File) {
        // 16ms: Precision rotary encoder ratchet tooth step (3600Hz transient click + 1100Hz tooth friction)
        val durationSec = 0.016f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val click = (sin(2 * PI * 3600 * t) * exp(-t / 0.0025f)).toFloat()
            val friction = (sin(2 * PI * 1100 * t) * exp(-t / 0.007f) * 0.4f).toFloat()

            val mixed = (click * 0.8f + friction).coerceIn(-1.0f, 1.0f)
            samples[i] = (mixed * 32767).toInt().toShort()
        }
        writeWavFile(outputFile, samples)
    }

    private fun writeWavFile(file: File, samples: ShortArray) {
        val totalDataLen = samples.size * 2
        val totalAudioLen = totalDataLen + 36
        val byteRate = SAMPLE_RATE * 2 // 16-bit mono

        val header = ByteBuffer.allocate(44).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalAudioLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // Subchunk1Size for PCM
            putShort(1) // AudioFormat (1 = PCM)
            putShort(1) // NumChannels (1 = Mono)
            putInt(SAMPLE_RATE)
            putInt(byteRate)
            putShort(2) // BlockAlign
            putShort(16) // BitsPerSample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
        }

        FileOutputStream(file).use { fos ->
            fos.write(header.array())
            val buffer = ByteBuffer.allocate(samples.size * 2).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                for (s in samples) putShort(s)
            }
            fos.write(buffer.array())
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        isLoaded = false
    }
}
