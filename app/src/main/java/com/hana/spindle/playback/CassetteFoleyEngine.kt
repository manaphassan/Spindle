package com.hana.spindle.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
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
 * Simulates the physical acoustic behavior of vintage high-end tape decks
 * (Sony Walkman WM-DD9, Nakamichi Dragon, TDK, Revox reel mechanisms):
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

        // Sound IDs
        const val SOUND_PLAY_SOLENOID = 1
        const val SOUND_STOP_RELEASE = 2
        const val SOUND_MOTOR_SPOOL = 3
        const val SOUND_CARRIAGE_EJECT = 4
        const val SOUND_SWITCH_SNAP = 5
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var soundPool: SoundPool? = null
    private val soundIdMap = mutableMapOf<Int, Int>()
    private var isLoaded = false

    var isEnabled: Boolean = true

    init {
        val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        isEnabled = prefs.getBoolean(PREF_FOLEY_ENABLED, true)

        initSoundPool()
    }

    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
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

                soundPool?.let { sp ->
                    soundIdMap[SOUND_PLAY_SOLENOID] = sp.load(filePlay.absolutePath, 1)
                    soundIdMap[SOUND_STOP_RELEASE] = sp.load(fileStop.absolutePath, 1)
                    soundIdMap[SOUND_MOTOR_SPOOL] = sp.load(fileSpool.absolutePath, 1)
                    soundIdMap[SOUND_CARRIAGE_EJECT] = sp.load(fileEject.absolutePath, 1)
                    soundIdMap[SOUND_SWITCH_SNAP] = sp.load(fileSwitch.absolutePath, 1)
                }
                isLoaded = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize procedural foley sounds", e)
            }
        }
    }

    fun playSolenoidClack() {
        playSound(SOUND_PLAY_SOLENOID, 0.45f)
    }

    fun playReleaseClick() {
        playSound(SOUND_STOP_RELEASE, 0.40f)
    }

    fun playMotorSpool() {
        playSound(SOUND_MOTOR_SPOOL, 0.35f)
    }

    fun playCarriageEject() {
        playSound(SOUND_CARRIAGE_EJECT, 0.50f)
    }

    fun playSwitchSnap() {
        playSound(SOUND_SWITCH_SNAP, 0.38f)
    }

    private fun playSound(soundKey: Int, volume: Float) {
        if (!isEnabled || !isLoaded) return
        val sp = soundPool ?: return
        val soundId = soundIdMap[soundKey] ?: return
        sp.play(soundId, volume, volume, 1, 0, 1.0f)
    }

    fun setFoleyEnabled(enabled: Boolean) {
        isEnabled = enabled
        val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_FOLEY_ENABLED, enabled).apply()
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
