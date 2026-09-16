package com.hana.spindle.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

data class RadioStation(
    val frequencyMhz: Float,
    val callsign: String,
    val rdsName: String,
    val genre: String,
    val streamUrl: String
)

data class RadioPlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentStation: RadioStation? = null,
    val currentFrequency: Float = 98.2f,
    val volume: Float = 0.8f,
    val isMuted: Boolean = false
)

class RadioStreamEngine(context: Context) {

    companion object {
        val PRESET_STATIONS = listOf(
            RadioStation(
                frequencyMhz = 88.5f,
                callsign = "LO-FI",
                rdsName = "LO-FI RADIO • CHILL BEATS TO RELAX / STUDY",
                genre = "Chillhop / Beats",
                streamUrl = "https://stream.zeno.fm/f3wvbbqmdg8uv"
            ),
            RadioStation(
                frequencyMhz = 93.2f,
                callsign = "ANIMEFM",
                rdsName = "ANIMEFM RADIO • 24/7 ANIME OST & J-POP",
                genre = "Anime & J-Pop",
                streamUrl = "https://listen.moe/stream"
            ),
            RadioStation(
                frequencyMhz = 98.6f,
                callsign = "INITIAL D",
                rdsName = "INITIAL D WORLD RADIO • EUROBEAT SPEEDWAY",
                genre = "Eurobeat / High Octane",
                streamUrl = "https://stream.nightride.fm/nightride.m4a"
            ),
            RadioStation(
                frequencyMhz = 104.2f,
                callsign = "CITYPOP",
                rdsName = "CITYPOP RADIO • 80S TOKYO GROOVE & VAPOR",
                genre = "City Pop / 80s Groove",
                streamUrl = "https://play.streamafrica.net/japancitypop"
            )
        )
    }

    private val _radioState = MutableStateFlow(
        RadioPlaybackState(
            currentStation = PRESET_STATIONS[0],
            currentFrequency = 88.5f
        )
    )
    val radioState: StateFlow<RadioPlaybackState> = _radioState.asStateFlow()

    private val exoPlayer: ExoPlayer

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // Fast-buffering LoadControl for live internet radio (3.5 sec max)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1500,  // Min buffer ms
                5000,  // Max buffer ms
                1000,  // Playback buffer ms
                1500   // Rebuffer ms
            )
            .build()

        exoPlayer = ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                volume = 0.8f
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _radioState.value = _radioState.value.copy(isPlaying = isPlaying)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val isBuffering = (playbackState == Player.STATE_BUFFERING) && exoPlayer.playWhenReady
                        _radioState.value = _radioState.value.copy(isBuffering = isBuffering)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _radioState.value = _radioState.value.copy(isPlaying = false, isBuffering = false)
                    }
                })
            }
    }

    fun tuneTo(frequency: Float) {
        val matchingStation = findNearestStation(frequency)
        _radioState.value = _radioState.value.copy(
            currentFrequency = frequency,
            currentStation = matchingStation
        )

        if (matchingStation != null) {
            playStation(matchingStation)
        } else {
            // Static / White Noise or silence between stations
            pause()
        }
    }

    fun playStation(station: RadioStation) {
        _radioState.value = _radioState.value.copy(
            currentStation = station,
            currentFrequency = station.frequencyMhz,
            isBuffering = true
        )
        val mediaItem = MediaItem.fromUri(station.streamUrl)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying || _radioState.value.isBuffering) {
            pause()
        } else {
            _radioState.value.currentStation?.let { playStation(it) }
        }
    }

    fun pause() {
        exoPlayer.stop()
        _radioState.value = _radioState.value.copy(isPlaying = false, isBuffering = false)
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        exoPlayer.volume = if (_radioState.value.isMuted) 0f else clamped
        _radioState.value = _radioState.value.copy(volume = clamped)
    }

    fun toggleMute(): Boolean {
        val newMuted = !_radioState.value.isMuted
        exoPlayer.volume = if (newMuted) 0f else _radioState.value.volume
        _radioState.value = _radioState.value.copy(isMuted = newMuted)
        return newMuted
    }

    fun seekNextStation() {
        val curFreq = _radioState.value.currentFrequency
        val next = PRESET_STATIONS.firstOrNull { it.frequencyMhz > curFreq + 0.1f } ?: PRESET_STATIONS.first()
        playStation(next)
    }

    fun seekPrevStation() {
        val curFreq = _radioState.value.currentFrequency
        val prev = PRESET_STATIONS.lastOrNull { it.frequencyMhz < curFreq - 0.1f } ?: PRESET_STATIONS.last()
        playStation(prev)
    }

    private fun findNearestStation(frequency: Float): RadioStation? {
        return PRESET_STATIONS.find { abs(it.frequencyMhz - frequency) < 0.25f }
    }

    fun release() {
        exoPlayer.release()
    }
}
