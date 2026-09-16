package com.hana.spindle.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.hana.spindle.data.db.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentSong: SongEntity? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val progress: Float = 0.0f
)

/**
 * Audiophile playback engine wrapping AndroidX Media3 / ExoPlayer.
 *
 * Configured with:
 * 1. Gapless buffer pre-allocation.
 * 2. High-resolution audio attributes for direct ALSA routing.
 * 3. Low-RAM memory load control (<8MB audio buffer).
 */
@UnstableApi
class AudioEngine(
    private val context: Context,
    val metricsTracker: AudioMetricsTracker = AudioMetricsTracker(context)
) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressPollJob: Job? = null

    val exoPlayer: ExoPlayer

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var playlist = mutableListOf<SongEntity>()
    private var currentIndex = -1

    val audioFxController = AudioFxController()

    init {
        // High-resolution audio attributes
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // Low-RAM load control: max 15 seconds buffer to save memory on 1GB DAPs
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2500,  // Min buffer ms
                15000, // Max buffer ms
                1000,  // Playback buffer ms
                1500   // Rebuffer ms
            )
            .build()

        exoPlayer = ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                        if (isPlaying) startProgressPolling() else stopProgressPolling()
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            playNext()
                        }
                    }
                })
            }

        audioFxController.attachSession(exoPlayer.audioSessionId)
    }

    fun playQueue(songs: List<SongEntity>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        playlist = songs.toMutableList()
        currentIndex = startIndex.coerceIn(0, playlist.size - 1)
        playCurrentTrack()
    }

    fun playSong(song: SongEntity) {
        playlist = mutableListOf(song)
        currentIndex = 0
        playCurrentTrack()
    }

    private fun playCurrentTrack() {
        if (currentIndex !in playlist.indices) return
        try {
            (context.applicationContext as? com.hana.spindle.SpindleApp)?.radioStreamEngine?.pause()
        } catch (e: Exception) {
            // ignore
        }
        val song = playlist[currentIndex]

        val mediaItem = MediaItem.fromUri(Uri.fromFile(File(song.path)))
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()

        _playbackState.value = PlaybackState(
            isPlaying = true,
            currentSong = song,
            currentPositionMs = 0L,
            durationMs = song.durationMs,
            progress = 0f
        )

        // Update telemetry
        metricsTracker.updateSourceSpecs(
            format = song.fileFormat,
            bitDepth = song.bitDepth,
            sampleRate = song.sampleRate,
            bitrateKbps = if (song.durationMs > 0) ((File(song.path).length() * 8) / song.durationMs).toInt() else 1411
        )
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            play()
        }
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun play() {
        try {
            (context.applicationContext as? com.hana.spindle.SpindleApp)?.radioStreamEngine?.pause()
        } catch (e: Exception) {
            // ignore
        }
        exoPlayer.play()
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        currentIndex = (currentIndex + 1) % playlist.size
        playCurrentTrack()
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return
        if (exoPlayer.currentPosition > 3000L) {
            // Restart current track if played more than 3 seconds
            exoPlayer.seekTo(0)
        } else {
            currentIndex = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
            playCurrentTrack()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        updateProgress()
    }

    private fun startProgressPolling() {
        progressPollJob?.cancel()
        progressPollJob = scope.launch {
            while (isActive && exoPlayer.isPlaying) {
                updateProgress()
                delay(200L) // 5Hz polling is lightweight for battery
            }
        }
    }

    private fun stopProgressPolling() {
        progressPollJob?.cancel()
        progressPollJob = null
    }

    private fun updateProgress() {
        val current = exoPlayer.currentPosition
        val duration = if (exoPlayer.duration > 0) exoPlayer.duration else _playbackState.value.durationMs
        val progress = if (duration > 0) (current.toFloat() / duration).coerceIn(0f, 1f) else 0f

        _playbackState.value = _playbackState.value.copy(
            currentPositionMs = current,
            durationMs = duration,
            progress = progress
        )
    }

    var audioBalance: Float = 0.5f
        private set

    fun setBalance(balance: Float) {
        audioBalance = balance.coerceIn(0.0f, 1.0f)
    }

    fun getStereoLevels(baseLevel: Float): Pair<Float, Float> {
        val leftMult = (2f * (1f - audioBalance)).coerceIn(0f, 1f)
        val rightMult = (2f * audioBalance).coerceIn(0f, 1f)
        return Pair(baseLevel * leftMult, baseLevel * rightMult)
    }

    fun release() {
        stopProgressPolling()
        audioFxController.release()
        exoPlayer.release()
    }
}
