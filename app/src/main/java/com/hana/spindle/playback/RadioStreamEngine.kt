package com.hana.spindle.playback

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.hana.spindle.SpindleApp
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
    val currentFrequency: Float = 88.5f,
    val volume: Float = 0.8f,
    val isMuted: Boolean = false,
    val nowPlayingTitle: String? = null
)

class RadioStreamEngine(private val context: Context) {

    companion object {
        private const val TAG = "RadioStreamEngine"

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
                streamUrl = "https://listen.moe/fallback"
            ),
            RadioStation(
                frequencyMhz = 98.6f,
                callsign = "INITIAL D",
                rdsName = "INITIAL D WORLD RADIO • EUROBEAT SPEEDWAY",
                genre = "Eurobeat / High Octane",
                streamUrl = "https://stream.laut.fm/eurobeat"
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

    private val prefs = context.getSharedPreferences("spindle_radio_stations", Context.MODE_PRIVATE)

    var userStations: MutableList<RadioStation> = loadStations()
        private set

    private fun loadStations(): MutableList<RadioStation> {
        val jsonStr = prefs.getString("stations_json", null)
        if (jsonStr.isNullOrBlank()) {
            return PRESET_STATIONS.toMutableList()
        }
        return try {
            val jsonArray = org.json.JSONArray(jsonStr)
            val list = mutableListOf<RadioStation>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    RadioStation(
                        frequencyMhz = obj.getDouble("frequencyMhz").toFloat(),
                        callsign = obj.getString("callsign"),
                        rdsName = obj.optString("rdsName", obj.getString("callsign")),
                        genre = obj.optString("genre", "Radio"),
                        streamUrl = obj.getString("streamUrl")
                    )
                )
            }
            if (list.isEmpty()) PRESET_STATIONS.toMutableList() else list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading stations json", e)
            PRESET_STATIONS.toMutableList()
        }
    }

    private fun saveStations() {
        try {
            val jsonArray = org.json.JSONArray()
            for (station in userStations) {
                val obj = org.json.JSONObject().apply {
                    put("frequencyMhz", station.frequencyMhz.toDouble())
                    put("callsign", station.callsign)
                    put("rdsName", station.rdsName)
                    put("genre", station.genre)
                    put("streamUrl", station.streamUrl)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString("stations_json", jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving stations json", e)
        }
    }

    fun addStation(station: RadioStation) {
        userStations.removeAll { it.frequencyMhz == station.frequencyMhz }
        userStations.add(station)
        userStations.sortBy { it.frequencyMhz }
        saveStations()
    }

    fun removeStation(frequencyMhz: Float) {
        userStations.removeAll { it.frequencyMhz == frequencyMhz }
        saveStations()
        if (_radioState.value.currentStation?.frequencyMhz == frequencyMhz) {
            if (userStations.isNotEmpty()) {
                tuneTo(userStations[0].frequencyMhz)
            } else {
                pause()
            }
        }
    }

    fun resetStationsToDefaults() {
        userStations.clear()
        userStations.addAll(PRESET_STATIONS)
        prefs.edit().remove("stations_json").apply()
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

        // Robust HTTP data source supporting Icecast/Shoutcast redirects & ICY metadata
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Spindle/1.0 (Linux; Android DAP) ExoPlayer")
            .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        // Fast-buffering LoadControl for live internet radio
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2000,  // Min buffer ms
                8000,  // Max buffer ms
                1500,  // Playback buffer ms
                2000   // Rebuffer ms
            )
            .build()

        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                volume = 0.8f
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.i(TAG, "onIsPlayingChanged: $isPlaying")
                        _radioState.value = _radioState.value.copy(isPlaying = isPlaying)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val isBuffering = (playbackState == Player.STATE_BUFFERING) && exoPlayer.playWhenReady
                        Log.i(TAG, "onPlaybackStateChanged: state=$playbackState, isBuffering=$isBuffering, playWhenReady=${exoPlayer.playWhenReady}")
                        _radioState.value = _radioState.value.copy(isBuffering = isBuffering)
                    }

                    override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                        val title = mediaMetadata.title?.toString()
                            ?: mediaMetadata.displayTitle?.toString()
                        Log.i(TAG, "onMediaMetadataChanged: title='$title', artist='${mediaMetadata.artist}'")
                        if (!title.isNullOrBlank()) {
                            _radioState.value = _radioState.value.copy(nowPlayingTitle = title)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "onPlayerError: ${error.errorCodeName} - ${error.message}", error)
                        _radioState.value = _radioState.value.copy(isPlaying = false, isBuffering = false)
                    }
                })
            }
    }

    val audioSessionId: Int
        get() = exoPlayer.audioSessionId

    fun tuneTo(frequency: Float) {
        val matchingStation = findNearestStation(frequency)
        val prevStation = _radioState.value.currentStation
        _radioState.value = _radioState.value.copy(
            currentFrequency = frequency,
            currentStation = matchingStation
        )

        if (matchingStation != null) {
            if (prevStation?.frequencyMhz != matchingStation.frequencyMhz || (!exoPlayer.isPlaying && !_radioState.value.isBuffering)) {
                playStation(matchingStation)
            }
        } else {
            // Static / White Noise or silence between stations
            pause()
        }
    }

    fun playStation(station: RadioStation) {
        Log.i(TAG, "playStation: ${station.callsign} (${station.frequencyMhz} MHz) -> ${station.streamUrl}")
        try {
            (context.applicationContext as? SpindleApp)?.audioEngine?.pause()
        } catch (e: Exception) {
            Log.w(TAG, "Could not pause audioEngine: ${e.message}")
        }
        _radioState.value = _radioState.value.copy(
            currentStation = station,
            currentFrequency = station.frequencyMhz,
            isBuffering = true,
            nowPlayingTitle = null
        )
        val mediaItem = MediaItem.Builder()
            .setUri(station.streamUrl)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setMaxPlaybackSpeed(1.02f)
                    .setMinPlaybackSpeed(0.98f)
                    .build()
            )
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying || _radioState.value.isBuffering) {
            pause()
        } else {
            val targetStation = _radioState.value.currentStation ?: PRESET_STATIONS[0]
            playStation(targetStation)
        }
    }

    fun pause() {
        Log.i(TAG, "pause() called: stopping exoPlayer")
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
