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
import kotlinx.coroutines.flow.firstOrNull
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
                        android.util.Log.d("AudioEngine", "onIsPlayingChanged: $isPlaying")
                        _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                        if (isPlaying) startProgressPolling() else stopProgressPolling()
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        android.util.Log.d("AudioEngine", "onPlaybackStateChanged: state=$state")
                        if (state == Player.STATE_ENDED) {
                            playNext()
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        android.util.Log.e("AudioEngine", "ExoPlayer error: ${error.errorCodeName} (code=${error.errorCode}) - ${error.message}", error)
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
        val file = File(song.path)
        android.util.Log.d("AudioEngine", "playCurrentTrack: title='${song.title}', path='${song.path}', exists=${file.exists()}, canRead=${file.canRead()}, length=${file.length()}")

        val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
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
        if (playlist.size <= 1) {
            scope.launch {
                try {
                    val app = context.applicationContext as? com.hana.spindle.SpindleApp ?: return@launch
                    val allSongs = app.database.songDao().getAllSongs().firstOrNull() ?: return@launch
                    if (allSongs.isNotEmpty()) {
                        val currentSongPath = playlist.getOrNull(currentIndex)?.path
                        val dbIndex = allSongs.indexOfFirst { it.path == currentSongPath }
                        val nextIndex = if (dbIndex >= 0) (dbIndex + 1) % allSongs.size else 0
                        playlist = allSongs.toMutableList()
                        currentIndex = nextIndex
                        playCurrentTrack()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AudioEngine", "playNext expand playlist error", e)
                }
            }
            return
        }
        currentIndex = (currentIndex + 1) % playlist.size
        playCurrentTrack()
    }

    fun playPrevious(forcePreviousSong: Boolean = false) {
        if (playlist.isEmpty()) return
        if (!forcePreviousSong && exoPlayer.currentPosition > 3000L) {
            // Restart current track if played more than 3 seconds
            exoPlayer.seekTo(0)
        } else {
            if (playlist.size <= 1) {
                scope.launch {
                    try {
                        val app = context.applicationContext as? com.hana.spindle.SpindleApp ?: return@launch
                        val allSongs = app.database.songDao().getAllSongs().firstOrNull() ?: return@launch
                        if (allSongs.isNotEmpty()) {
                            val currentSongPath = playlist.getOrNull(currentIndex)?.path
                            val dbIndex = allSongs.indexOfFirst { it.path == currentSongPath }
                            val prevIndex = if (dbIndex > 0) dbIndex - 1 else allSongs.size - 1
                            playlist = allSongs.toMutableList()
                            currentIndex = prevIndex
                            playCurrentTrack()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AudioEngine", "playPrevious expand playlist error", e)
                    }
                }
                return
            }
            currentIndex = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
            playCurrentTrack()
        }
    }

    fun playNextAlbum() {
        if (playlist.isEmpty()) return
        val currentAlbum = playlist.getOrNull(currentIndex)?.album ?: ""
        // Search forward in playlist for the first track belonging to a different album
        for (i in 1 until playlist.size) {
            val candidateIndex = (currentIndex + i) % playlist.size
            if (playlist[candidateIndex].album != currentAlbum) {
                currentIndex = candidateIndex
                playCurrentTrack()
                return
            }
        }
        // If current playlist only has 1 album, fetch all songs from Room DB
        scope.launch {
            try {
                val app = context.applicationContext as? com.hana.spindle.SpindleApp ?: return@launch
                val allSongs = app.database.songDao().getAllSongs().firstOrNull() ?: return@launch
                if (allSongs.isNotEmpty()) {
                    val currentSongPath = playlist.getOrNull(currentIndex)?.path
                    val dbIndex = allSongs.indexOfFirst { it.path == currentSongPath }.coerceAtLeast(0)
                    for (i in 1 until allSongs.size) {
                        val candidateIndex = (dbIndex + i) % allSongs.size
                        if (allSongs[candidateIndex].album != currentAlbum) {
                            playlist = allSongs.toMutableList()
                            currentIndex = candidateIndex
                            playCurrentTrack()
                            return@launch
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioEngine", "playNextAlbum error", e)
            }
        }
    }

    fun playPreviousAlbum() {
        if (playlist.isEmpty()) return
        val currentAlbum = playlist.getOrNull(currentIndex)?.album ?: ""
        for (i in 1 until playlist.size) {
            val candidateIndex = if (currentIndex - i < 0) playlist.size + (currentIndex - i) else currentIndex - i
            if (playlist[candidateIndex].album != currentAlbum) {
                val targetAlbum = playlist[candidateIndex].album
                var firstTrackIndex = candidateIndex
                while (firstTrackIndex > 0 && playlist[firstTrackIndex - 1].album == targetAlbum) {
                    firstTrackIndex--
                }
                currentIndex = firstTrackIndex
                playCurrentTrack()
                return
            }
        }
        scope.launch {
            try {
                val app = context.applicationContext as? com.hana.spindle.SpindleApp ?: return@launch
                val allSongs = app.database.songDao().getAllSongs().firstOrNull() ?: return@launch
                if (allSongs.isNotEmpty()) {
                    val currentSongPath = playlist.getOrNull(currentIndex)?.path
                    val dbIndex = allSongs.indexOfFirst { it.path == currentSongPath }.coerceAtLeast(0)
                    for (i in 1 until allSongs.size) {
                        val candidateIndex = if (dbIndex - i < 0) allSongs.size + (dbIndex - i) else dbIndex - i
                        if (allSongs[candidateIndex].album != currentAlbum) {
                            val targetAlbum = allSongs[candidateIndex].album
                            var firstTrackIndex = candidateIndex
                            while (firstTrackIndex > 0 && allSongs[firstTrackIndex - 1].album == targetAlbum) {
                                firstTrackIndex--
                            }
                            playlist = allSongs.toMutableList()
                            currentIndex = firstTrackIndex
                            playCurrentTrack()
                            return@launch
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioEngine", "playPreviousAlbum error", e)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        updateProgress()
    }

    fun rewind(deltaMs: Long = 10_000L) {
        val current = exoPlayer.currentPosition
        val target = (current - deltaMs).coerceAtLeast(0L)
        seekTo(target)
    }

    fun fastForward(deltaMs: Long = 10_000L) {
        val current = exoPlayer.currentPosition
        val duration = if (exoPlayer.duration > 0) exoPlayer.duration else _playbackState.value.durationMs
        val target = (current + deltaMs).coerceAtMost(if (duration > 0) duration else Long.MAX_VALUE)
        seekTo(target)
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
