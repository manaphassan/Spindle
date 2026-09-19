package com.hana.spindle.lite.audio

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log

data class LiteRadioStation(
    val frequencyMhz: Float,
    val callsign: String,
    val rdsName: String,
    val genre: String,
    val streamUrl: String
)

class LiteRadioEngine(private val context: Context) :
    MediaPlayer.OnPreparedListener,
    MediaPlayer.OnErrorListener,
    MediaPlayer.OnBufferingUpdateListener,
    AudioManager.OnAudioFocusChangeListener {

    companion object {
        private const val TAG = "LiteRadioEngine"

        val PRESETS = listOf(
            LiteRadioStation(
                frequencyMhz = 88.5f,
                callsign = "LO-FI",
                rdsName = "LO-FI RADIO • CHILL BEATS TO RELAX / STUDY",
                genre = "Chillhop / Beats",
                streamUrl = "https://stream.zeno.fm/f3wvbbqmdg8uv"
            ),
            LiteRadioStation(
                frequencyMhz = 93.2f,
                callsign = "ANIMEFM",
                rdsName = "ANIMEFM RADIO • 24/7 ANIME OST & J-POP",
                genre = "Anime & J-Pop",
                streamUrl = "https://listen.moe/fallback"
            ),
            LiteRadioStation(
                frequencyMhz = 98.6f,
                callsign = "INITIAL D",
                rdsName = "INITIAL D WORLD RADIO • EUROBEAT SPEEDWAY",
                genre = "Eurobeat / High Octane",
                streamUrl = "https://stream.laut.fm/eurobeat"
            ),
            LiteRadioStation(
                frequencyMhz = 104.2f,
                callsign = "CITYPOP",
                rdsName = "CITYPOP RADIO • 80S TOKYO GROOVE & VAPOR",
                genre = "City Pop / 80s Groove",
                streamUrl = "https://play.streamafrica.net/japancitypop"
            )
        )
    }

    interface Listener {
        fun onRadioStateChanged(isPlaying: Boolean, isBuffering: Boolean, station: LiteRadioStation?)
        fun onRadioBuffering(percent: Int)
        fun onRadioError(message: String)
    }

    private var mediaPlayer: MediaPlayer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    var listener: Listener? = null
    var currentStation: LiteRadioStation? = PRESETS[0]
        private set
    var isPlaying: Boolean = false
        private set
    var isBuffering: Boolean = false
        private set

    fun playStation(station: LiteRadioStation) {
        currentStation = station
        stopInternal(releaseFocus = false)

        if (!requestAudioFocus()) {
            listener?.onRadioError("Audio focus denied")
            return
        }

        isBuffering = true
        notifyState()

        try {
            val player = MediaPlayer()
            @Suppress("DEPRECATION")
            player.setAudioStreamType(AudioManager.STREAM_MUSIC)
            player.setDataSource(station.streamUrl)
            player.setOnPreparedListener(this)
            player.setOnErrorListener(this)
            player.setOnBufferingUpdateListener(this)
            player.prepareAsync()
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare radio stream: ${e.message}", e)
            isBuffering = false
            isPlaying = false
            notifyState()
            listener?.onRadioError("Stream connection failed")
        }
    }

    fun togglePlayPause() {
        val station = currentStation ?: PRESETS[0]
        if (isPlaying || isBuffering) {
            stop()
        } else {
            playStation(station)
        }
    }

    fun stop() {
        stopInternal(releaseFocus = true)
        isBuffering = false
        isPlaying = false
        notifyState()
    }

    private fun stopInternal(releaseFocus: Boolean) {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing media player: ${e.message}")
        }
        mediaPlayer = null

        if (releaseFocus) {
            abandonAudioFocus()
        }
    }

    fun tuneNext() {
        val list = PRESETS
        val currentIdx = list.indexOfFirst { it.frequencyMhz == currentStation?.frequencyMhz }
        val nextIdx = if (currentIdx < 0 || currentIdx >= list.size - 1) 0 else currentIdx + 1
        playStation(list[nextIdx])
    }

    fun tunePrev() {
        val list = PRESETS
        val currentIdx = list.indexOfFirst { it.frequencyMhz == currentStation?.frequencyMhz }
        val prevIdx = if (currentIdx <= 0) list.size - 1 else currentIdx - 1
        playStation(list[prevIdx])
    }

    fun tuneToFrequency(freqMhz: Float) {
        val closest = PRESETS.minByOrNull { kotlin.math.abs(it.frequencyMhz - freqMhz) } ?: PRESETS[0]
        playStation(closest)
    }

    override fun onPrepared(mp: MediaPlayer?) {
        if (mp != mediaPlayer) return
        try {
            mp?.start()
            isPlaying = true
            isBuffering = false
            notifyState()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting playback after prepare: ${e.message}", e)
            isBuffering = false
            isPlaying = false
            notifyState()
        }
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        Log.e(TAG, "Radio MediaPlayer error: what=$what, extra=$extra")
        isBuffering = false
        isPlaying = false
        notifyState()
        listener?.onRadioError("Playback error ($what, $extra)")
        return true
    }

    override fun onBufferingUpdate(mp: MediaPlayer?, percent: Int) {
        mainHandler.post {
            listener?.onRadioBuffering(percent)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> stop()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (isPlaying) {
                    try {
                        mediaPlayer?.pause()
                        isPlaying = false
                        notifyState()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error pausing radio on transient loss")
                    }
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (!isPlaying && mediaPlayer != null) {
                    try {
                        mediaPlayer?.start()
                        isPlaying = true
                        notifyState()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error resuming radio on focus gain")
                    }
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        @Suppress("DEPRECATION")
        val result = audioManager.requestAudioFocus(
            this,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        @Suppress("DEPRECATION")
        audioManager.abandonAudioFocus(this)
    }

    private fun notifyState() {
        mainHandler.post {
            listener?.onRadioStateChanged(isPlaying, isBuffering, currentStation)
        }
    }

    fun release() {
        stop()
        listener = null
    }
}
