package com.hana.spindle.lite.audio

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.hana.spindle.lite.db.Track
import java.io.File
import java.io.FileInputStream

/**
 * Dual chained MediaPlayer audio engine for Spindle Lite.
 * Engineered for low CPU overhead (<3%) and gapless playback on Android 4.4 KitKat.
 */
class LiteAudioEngine(private val context: Context) :
    MediaPlayer.OnCompletionListener,
    MediaPlayer.OnErrorListener,
    MediaPlayer.OnPreparedListener,
    AudioManager.OnAudioFocusChangeListener {

    private var primaryPlayer: MediaPlayer? = null
    private var nextPlayer: MediaPlayer? = null
    private var isNextPlayerChained = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    enum class PlayMode {
        ALL,
        REPEAT_ONE,
        SHUFFLE
    }

    enum class GainStage(val gainDb: Float) {
        LOW_IEM(0f),
        HIGH_CANS(6f)
    }

    enum class ReplayGainMode {
        OFF,
        TRACK,
        ALBUM
    }

    enum class CrossfadeMode(val durationMs: Long) {
        GAPLESS(0L),
        CROSSFADE_2S(2000L),
        CROSSFADE_4S(4000L)
    }

    var playMode: PlayMode = PlayMode.ALL

    var gainStage: GainStage = GainStage.LOW_IEM
        set(value) {
            field = value
            audioFxController.setPreampGainDb(value.gainDb)
        }

    var replayGainMode: ReplayGainMode = ReplayGainMode.TRACK
        set(value) {
            field = value
            applyPlayerVolume()
        }

    var crossfadeMode: CrossfadeMode = CrossfadeMode.GAPLESS

    var isScreenOn: Boolean = true
        set(value) {
            field = value
            if (value && isPlaying) {
                mainHandler.removeCallbacks(progressRunnable)
                mainHandler.post(progressRunnable)
            }
        }

    val audioFxController = LiteAudioFxController(context)

    var playlist: List<Track> = emptyList()
        private set
    private var currentIndex: Int = -1
    var isPlaying: Boolean = false
        private set

    var listener: PlaybackListener? = null
    var serviceListener: PlaybackListener? = null

    private var isCrossfading = false
    private var crossfadeStartTime = 0L

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && primaryPlayer != null) {
                try {
                    val currentPos = primaryPlayer?.currentPosition?.toLong() ?: 0L
                    val duration = primaryPlayer?.duration?.toLong() ?: 0L

                    // If we're at 85% progress and next player not chained, prepare next player
                    if (!isNextPlayerChained && duration > 0 && currentPos > (duration * 0.85)) {
                        chainNextTrack()
                    }

                    // Check if crossfade transition should trigger
                    if (crossfadeMode != CrossfadeMode.GAPLESS && !isCrossfading && nextPlayer != null &&
                        duration > 0 && currentPos >= (duration - crossfadeMode.durationMs)
                    ) {
                        startCrossfade()
                    }

                    if (isScreenOn) {
                        notifyStateChanged(currentPos, duration)
                    }
                } catch (e: Exception) {
                    // Safe catch if player was released
                }
                val delay = if (isScreenOn) 30L else 2000L
                mainHandler.postDelayed(this, delay)
            }
        }
    }

    fun setPlaylist(tracks: List<Track>, startIndex: Int = 0, autoPlay: Boolean = true) {
        this.playlist = tracks
        if (tracks.isNotEmpty() && startIndex in tracks.indices) {
            this.currentIndex = startIndex
            if (autoPlay) {
                playTrackAt(startIndex)
            } else {
                notifyStateChanged(0L, tracks[startIndex].durationMs)
            }
        }
    }

    fun playTrackAt(index: Int) {
        if (index !in playlist.indices) return
        this.currentIndex = index
        val track = playlist[index]

        releasePlayers()

        val file = File(track.filePath)
        if (!file.exists()) {
            listener?.onPlaybackError("File not found: ${file.name}")
            return
        }

        try {
            val player = MediaPlayer().apply {
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                setOnCompletionListener(this@LiteAudioEngine)
                setOnErrorListener(this@LiteAudioEngine)
                setOnPreparedListener(this@LiteAudioEngine)

                // Direct file path data source ensures the native descriptor remains open during decode
                setDataSource(track.filePath)
                prepareAsync()
            }
            primaryPlayer = player
        } catch (e: Exception) {
            listener?.onPlaybackError("Failed to open track: ${track.title}")
        }
    }

    private fun chainNextTrack() {
        val nextIndex = currentIndex + 1
        if (nextIndex in playlist.indices && !isNextPlayerChained) {
            isNextPlayerChained = true
            val nextTrack = playlist[nextIndex]
            Thread {
                val file = File(nextTrack.filePath)
                if (!file.exists()) {
                    isNextPlayerChained = false
                    return@Thread
                }

                try {
                    val next = MediaPlayer().apply {
                        setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                        setAudioStreamType(AudioManager.STREAM_MUSIC)
                        setOnErrorListener(this@LiteAudioEngine)

                        setDataSource(nextTrack.filePath)
                        prepare()
                    }

                    mainHandler.post {
                        if (isPlaying && primaryPlayer != null) {
                            try {
                                if (crossfadeMode == CrossfadeMode.GAPLESS) {
                                    primaryPlayer?.setNextMediaPlayer(next)
                                }
                                nextPlayer = next
                            } catch (e: Exception) {
                                next.release()
                                isNextPlayerChained = false
                            }
                        } else {
                            next.release()
                            isNextPlayerChained = false
                        }
                    }
                } catch (e: Exception) {
                    mainHandler.post {
                        isNextPlayerChained = false
                        nextPlayer?.release()
                        nextPlayer = null
                    }
                }
            }.start()
        }
    }

    fun calculateEffectiveVolume(index: Int = currentIndex): Float {
        if (replayGainMode == ReplayGainMode.OFF) return 1.0f
        val track = if (index in playlist.indices) playlist[index] else null ?: return 1.0f
        val gainDb = track.replayGainDb ?: 0f
        if (gainDb == 0f) return 1.0f
        val linear = Math.pow(10.0, (gainDb / 20.0)).toFloat()
        return linear.coerceIn(0.1f, 1.25f)
    }

    fun applyPlayerVolume() {
        val vol = calculateEffectiveVolume()
        try {
            primaryPlayer?.setVolume(vol, vol)
        } catch (ignored: Exception) {}
    }

    private fun startCrossfade() {
        val next = nextPlayer ?: return
        if (isCrossfading) return
        isCrossfading = true
        crossfadeStartTime = System.currentTimeMillis()
        val durationMs = crossfadeMode.durationMs.coerceAtLeast(500L)

        try {
            next.setVolume(0f, 0f)
            next.start()
        } catch (e: Exception) {
            isCrossfading = false
            return
        }

        val crossfadeRunnable = object : Runnable {
            override fun run() {
                if (!isCrossfading || primaryPlayer == null || nextPlayer == null) {
                    isCrossfading = false
                    return
                }
                val elapsed = System.currentTimeMillis() - crossfadeStartTime
                val progress = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)

                val baseVolPrimary = calculateEffectiveVolume(currentIndex)
                val baseVolNext = calculateEffectiveVolume(currentIndex + 1)

                val angle = progress * (Math.PI / 2.0)
                val volPrimary = (Math.cos(angle) * baseVolPrimary).toFloat().coerceIn(0f, 1.25f)
                val volNext = (Math.sin(angle) * baseVolNext).toFloat().coerceIn(0f, 1.25f)

                try {
                    primaryPlayer?.setVolume(volPrimary, volPrimary)
                    nextPlayer?.setVolume(volNext, volNext)
                } catch (ignored: Exception) {}

                if (progress < 1.0f) {
                    mainHandler.postDelayed(this, 40L)
                } else {
                    isCrossfading = false
                    val completedTrack = currentTrack
                    val oldPrimary = primaryPlayer
                    primaryPlayer = nextPlayer
                    nextPlayer = null
                    isNextPlayerChained = false
                    currentIndex++
                    primaryPlayer?.setOnCompletionListener(this@LiteAudioEngine)
                    primaryPlayer?.audioSessionId?.let { audioFxController.attachSession(it) }
                    try {
                        oldPrimary?.stop()
                        oldPrimary?.release()
                    } catch (ignored: Exception) {}
                    notifyStateChanged()
                    listener?.onTrackCompleted(completedTrack)
                    serviceListener?.onTrackCompleted(completedTrack)
                }
            }
        }
        mainHandler.post(crossfadeRunnable)
    }

    fun togglePlayPause() {
        if (isPlaying) {
            pause()
        } else {
            if (primaryPlayer == null) {
                val idx = if (currentIndex in playlist.indices) currentIndex else 0
                if (playlist.isNotEmpty() && idx in playlist.indices) {
                    playTrackAt(idx)
                }
            } else {
                play()
            }
        }
    }

    fun play() {
        if (primaryPlayer == null) {
            val idx = if (currentIndex in playlist.indices) currentIndex else 0
            if (playlist.isNotEmpty() && idx in playlist.indices) {
                playTrackAt(idx)
            }
            return
        }
        if (requestAudioFocus()) {
            primaryPlayer?.start()
            isPlaying = true
            mainHandler.post(progressRunnable)
            notifyStateChanged()
        }
    }

    fun pause() {
        primaryPlayer?.pause()
        isPlaying = false
        mainHandler.removeCallbacks(progressRunnable)
        notifyStateChanged()
    }

    fun next() {
        if (playlist.isEmpty()) return
        when (playMode) {
            PlayMode.REPEAT_ONE -> {
                seekTo(0)
                play()
            }
            PlayMode.SHUFFLE -> {
                if (playlist.size == 1) {
                    seekTo(0)
                    play()
                } else {
                    var nextIdx = kotlin.random.Random.nextInt(playlist.size)
                    if (nextIdx == currentIndex) {
                        nextIdx = (nextIdx + 1) % playlist.size
                    }
                    playTrackAt(nextIdx)
                }
            }
            PlayMode.ALL -> {
                if (currentIndex + 1 in playlist.indices) {
                    playTrackAt(currentIndex + 1)
                } else {
                    playTrackAt(0) // Loop to first
                }
            }
        }
    }

    fun previous() {
        if (playlist.isEmpty()) return
        val currentPos = primaryPlayer?.currentPosition ?: 0
        if (currentPos > 3000) {
            // Seek to start if past 3s
            seekTo(0)
        } else if (currentIndex - 1 in playlist.indices) {
            playTrackAt(currentIndex - 1)
        } else {
            playTrackAt(playlist.size - 1)
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            primaryPlayer?.seekTo(positionMs.toInt())
            notifyStateChanged(positionMs, primaryPlayer?.duration?.toLong() ?: 0L)
        } catch (e: Exception) {
            // Safe catch
        }
    }

    fun seekToFraction(fraction: Float) {
        val duration = primaryPlayer?.duration?.toLong() ?: 0L
        if (duration > 0L) {
            val targetMs = (fraction * duration).toLong().coerceIn(0L, duration)
            seekTo(targetMs)
        }
    }

    fun fastForward(deltaMs: Long = 5000L) {
        val current = primaryPlayer?.currentPosition?.toLong() ?: 0L
        val duration = primaryPlayer?.duration?.toLong() ?: 0L
        seekTo((current + deltaMs).coerceAtMost(duration))
    }

    fun rewind(deltaMs: Long = 5000L) {
        val current = primaryPlayer?.currentPosition?.toLong() ?: 0L
        seekTo((current - deltaMs).coerceAtLeast(0L))
    }

    override fun onPrepared(mp: MediaPlayer?) {
        if (requestAudioFocus()) {
            applyPlayerVolume()
            mp?.start()
            mp?.audioSessionId?.let { audioFxController.attachSession(it) }
            isPlaying = true
            isNextPlayerChained = false
            isCrossfading = false
            mainHandler.post(progressRunnable)
            notifyStateChanged()
        }
    }

    override fun onCompletion(mp: MediaPlayer?) {
        if (isCrossfading) {
            return
        }
        val completedTrack = currentTrack
        if (playMode == PlayMode.REPEAT_ONE) {
            seekTo(0)
            play()
            listener?.onTrackCompleted(completedTrack)
            serviceListener?.onTrackCompleted(completedTrack)
            return
        }
        if (playMode == PlayMode.ALL && isNextPlayerChained && nextPlayer != null) {
            // Seamless swap
            primaryPlayer?.release()
            primaryPlayer = nextPlayer
            nextPlayer = null
            isNextPlayerChained = false
            currentIndex++
            applyPlayerVolume()
            primaryPlayer?.setOnCompletionListener(this)
            primaryPlayer?.audioSessionId?.let { audioFxController.attachSession(it) }
            notifyStateChanged()
            listener?.onTrackCompleted(completedTrack)
            serviceListener?.onTrackCompleted(completedTrack)
        } else {
            next()
            listener?.onTrackCompleted(completedTrack)
            serviceListener?.onTrackCompleted(completedTrack)
        }
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        isPlaying = false
        mainHandler.removeCallbacks(progressRunnable)
        listener?.onPlaybackError("Audio playback error ($what, $extra)")
        serviceListener?.onPlaybackError("Audio playback error ($what, $extra)")
        releasePlayers()
        return true
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                val duckVol = calculateEffectiveVolume() * 0.2f
                primaryPlayer?.setVolume(duckVol, duckVol)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                applyPlayerVolume()
                play()
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val result = audioManager.requestAudioFocus(
            this,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun releasePlayers() {
        mainHandler.removeCallbacks(progressRunnable)
        isNextPlayerChained = false
        isCrossfading = false
        try {
            primaryPlayer?.stop()
            primaryPlayer?.release()
        } catch (ignored: Exception) {}
        primaryPlayer = null

        try {
            nextPlayer?.stop()
            nextPlayer?.release()
        } catch (ignored: Exception) {}
        nextPlayer = null
    }

    fun release() {
        releasePlayers()
        audioFxController.release()
        audioManager.abandonAudioFocus(this)
    }

    val currentTrack: Track?
        get() = if (currentIndex in playlist.indices) playlist[currentIndex] else null

    private fun notifyStateChanged(
        currentPos: Long = primaryPlayer?.currentPosition?.toLong() ?: 0L,
        duration: Long = primaryPlayer?.duration?.toLong() ?: currentTrack?.durationMs ?: 0L
    ) {
        val state = PlaybackState(
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            currentPositionMs = currentPos,
            durationMs = duration,
            trackIndex = currentIndex,
            totalTracks = playlist.size
        )
        listener?.onPlaybackStateChanged(state)
        serviceListener?.onPlaybackStateChanged(state)
    }
}
