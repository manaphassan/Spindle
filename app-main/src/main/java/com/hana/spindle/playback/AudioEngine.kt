package com.hana.spindle.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.hana.spindle.data.CueSheetParser
import com.hana.spindle.data.LyricsData
import com.hana.spindle.data.LyricsParser
import com.hana.spindle.data.TagParser
import com.hana.spindle.data.db.TrackEntity
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

enum class ShuffleMode {
    OFF,
    ALL,
    ALBUM
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingSeconds: Long = 0L,
    val initialMinutes: Int = 0,
    val stopAfterCurrentTrack: Boolean = false
)

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentTrack: TrackEntity? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val progress: Float = 0.0f,
    val shuffleMode: ShuffleMode = ShuffleMode.OFF,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val currentLyrics: LyricsData? = null,
    val activeLyricIndex: Int = -1
) {
    val currentSong: TrackEntity? get() = currentTrack
}

/**
 * Audiophile playback engine wrapping AndroidX Media3 / ExoPlayer.
 *
 * Configured with:
 * 1. Gapless buffer pre-allocation.
 * 2. High-resolution audio attributes for direct ALSA routing.
 * 3. Low-RAM memory load control (<8MB audio buffer).
 * 4. 3-state Shuffle & 3-state Repeat modes (inspired by Poweramp).
 * 5. Synchronized lyrics streaming & timestamp lookup.
 * 6. Sleep timer with gentle audio volume fade-out.
 * 7. Active Playback Queue reordering and management.
 */
@UnstableApi
class AudioEngine(
    private val context: Context,
    val metricsTracker: AudioMetricsTracker = AudioMetricsTracker(context)
) : AudioTransport {

    override val isPlaying: Boolean get() = _playbackState.value.isPlaying

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressPollJob: Job? = null

    val exoPlayer: ExoPlayer

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _sleepTimerState = MutableStateFlow(SleepTimerState())
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState.asStateFlow()
    private var sleepTimerJob: Job? = null
    private var isFadingOut: Boolean = false

    private val _currentQueueFlow = MutableStateFlow<List<TrackEntity>>(emptyList())
    val currentQueueFlow: StateFlow<List<TrackEntity>> = _currentQueueFlow.asStateFlow()

    private val _currentQueueIndexFlow = MutableStateFlow<Int>(-1)
    val currentQueueIndexFlow: StateFlow<Int> = _currentQueueIndexFlow.asStateFlow()

    private var originalPlaylist = mutableListOf<TrackEntity>()
    private var playlist = mutableListOf<TrackEntity>()
    private var currentIndex = -1

    val audioFxController = AudioFxController()
    val foleyEngine = CassetteFoleyEngine(context)

    // ReplayGain Audiophile Loudness Normalization (OFF, TRACK, ALBUM)
    var replayGainMode: com.hana.spindle.core.ReplayGainMode = com.hana.spindle.core.ReplayGainMode.TRACK
        private set
    val isReplayGainEnabled: Boolean
        get() = replayGainMode != com.hana.spindle.core.ReplayGainMode.OFF

    var replayGainPreampDb: Float = 0.0f
        private set
    var currentReplayGainDb: Float = 0.0f
        private set

    // Crossfade Transition Mode (GAPLESS, 2s, 4s)
    var crossfadeMode: com.hana.spindle.core.CrossfadeMode = com.hana.spindle.core.CrossfadeMode.GAPLESS
        private set

    private fun syncQueueState() {
        _currentQueueFlow.value = playlist.toList()
        _currentQueueIndexFlow.value = currentIndex
        notifyWidgetUpdate()
    }

    private fun notifyWidgetUpdate() {
        try {
            val intent = android.content.Intent("com.hana.spindle.widget.ACTION_UPDATE_WIDGET")
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            // ignore
        }
    }

    private var wasPausedByUnplug = false

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                val prefs = this@AudioEngine.context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
                if (prefs.getBoolean("pref_auto_pause_unplug", true)) {
                    if (exoPlayer.isPlaying) {
                        wasPausedByUnplug = true
                        pause()
                    }
                }
            }
        }
    }

    fun onAudioDeviceConnected() {
        val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("pref_auto_resume_plug", false) && wasPausedByUnplug) {
            wasPausedByUnplug = false
            play()
        }
    }

    init {
        val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        val savedRgMode = prefs.getString("pref_replaygain_mode", null)
        replayGainMode = if (savedRgMode != null) {
            try {
                com.hana.spindle.core.ReplayGainMode.valueOf(savedRgMode)
            } catch (_: Exception) {
                com.hana.spindle.core.ReplayGainMode.TRACK
            }
        } else {
            if (prefs.getBoolean("pref_replaygain_enabled", true)) {
                com.hana.spindle.core.ReplayGainMode.TRACK
            } else {
                com.hana.spindle.core.ReplayGainMode.OFF
            }
        }
        replayGainPreampDb = prefs.getFloat("pref_replaygain_preamp_db", 0.0f)

        val savedCrossfade = prefs.getString("pref_crossfade_mode", com.hana.spindle.core.CrossfadeMode.GAPLESS.name)
        crossfadeMode = try {
            com.hana.spindle.core.CrossfadeMode.valueOf(savedCrossfade ?: com.hana.spindle.core.CrossfadeMode.GAPLESS.name)
        } catch (_: Exception) {
            com.hana.spindle.core.CrossfadeMode.GAPLESS
        }
        audioFxController.crossfadeMode = crossfadeMode

        val savedTapeName = prefs.getString("pref_tape_formulation", AudioFxController.TapeFormulation.TYPE_IV_METAL.name)
        val formulation = try {
            AudioFxController.TapeFormulation.valueOf(savedTapeName ?: AudioFxController.TapeFormulation.TYPE_IV_METAL.name)
        } catch (_: Exception) {
            AudioFxController.TapeFormulation.TYPE_IV_METAL
        }
        audioFxController.setTapeFormulation(formulation)

        val savedDolbyName = prefs.getString("pref_dolby_mode", AudioFxController.DolbyMode.OFF.name)
        val dolby = try {
            AudioFxController.DolbyMode.valueOf(savedDolbyName ?: AudioFxController.DolbyMode.OFF.name)
        } catch (_: Exception) {
            AudioFxController.DolbyMode.OFF
        }
        audioFxController.setDolbyMode(dolby)

        val savedSatEnabled = prefs.getBoolean("pref_tape_saturation_enabled", false)
        if (savedSatEnabled && com.hana.spindle.licensing.StudioUnlockManager.isFeatureUnlocked(
                context,
                com.hana.spindle.licensing.StudioUnlockManager.StudioFeature.ANALOG_TAPE_SATURATION
            )
        ) {
            audioFxController.setTapeSaturationEnabled(true)
        }

        val savedCrossfeed = prefs.getInt("pref_dsp_crossfeed_strength", 0)
        audioFxController.setCrossfeedStrength(savedCrossfeed)

        try {
            context.registerReceiver(
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // High-resolution audio attributes
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // Standard load control: stable buffering for local high-bitrate MicroSD FLAC playback
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000, // minBufferMs: 15s
                50_000, // maxBufferMs: 50s
                500,    // bufferForPlaybackMs: 500ms
                1_000   // bufferForPlaybackAfterRebufferMs: 1s
            )
            .setTargetBufferBytes(16 * 1024 * 1024) // 16MB buffer for 24-bit 96/192kHz hi-res audio
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: AudioSink,
                eventHandler: Handler,
                eventListener: AudioRendererEventListener,
                out: ArrayList<Renderer>
            ) {
                out.add(
                    QualcommFlacMediaCodecAudioRenderer(
                        context,
                        mediaCodecSelector,
                        enableDecoderFallback,
                        eventHandler,
                        eventListener,
                        audioSink
                    )
                )
            }
        }.apply {
            // Disable Float output on Android 8.0/Oreo to prevent AudioFlinger 4MB shared-memory OOM
            // on 24-bit 96kHz/192kHz streams (not enough memory for AudioTrack).
            setEnableAudioFloatOutput(false)
            setEnableAudioTrackPlaybackParams(false)
        }

        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setLoadControl(loadControl)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addAnalyticsListener(androidx.media3.exoplayer.util.EventLogger("SpindlePlayer"))
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        android.util.Log.d("AudioEngine", "onIsPlayingChanged: $isPlaying")
                        _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                        notifyWidgetUpdate()
                        if (isPlaying) {
                            startProgressPolling()
                        } else {
                            stopProgressPolling()
                            saveLastPlayed(playlist.getOrNull(currentIndex)?.path, exoPlayer.currentPosition)
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        android.util.Log.d("AudioEngine", "onPlaybackStateChanged: state=$state")
                        if (state == Player.STATE_ENDED) {
                            handleTrackEnded()
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                            val newIndex = exoPlayer.currentMediaItemIndex
                            if (newIndex in playlist.indices && newIndex != currentIndex) {
                                currentIndex = newIndex
                                val newSong = playlist[newIndex]
                                val physicalPath = CueSheetParser.getAudioFilePath(newSong.path)
                                val file = File(physicalPath)
                                applyReplayGain(file)
                                _playbackState.value = _playbackState.value.copy(
                                    currentTrack = newSong,
                                    durationMs = newSong.durationMs,
                                    currentPositionMs = 0L,
                                    progress = 0f
                                )
                                loadLyricsForTrack(newSong, physicalPath)
                                metricsTracker.updateSourceSpecs(
                                    format = newSong.fileFormat,
                                    bitDepth = newSong.bitDepth,
                                    sampleRate = newSong.sampleRate,
                                    bitrateKbps = if (newSong.bitrateKbps > 0) newSong.bitrateKbps else 1411
                                )
                                syncQueueState()
                            }
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        android.util.Log.e("AudioEngine", "ExoPlayer error: ${error.errorCodeName} (code=${error.errorCode}) - ${error.message}", error)
                    }
                })
            }

        audioFxController.attachSession(exoPlayer.audioSessionId)
        restoreLastPlayedSong()
    }

    private fun handleTrackEnded() {
        android.util.Log.d("AudioEngine", "handleTrackEnded: repeatMode=${_playbackState.value.repeatMode}, currentIndex=$currentIndex, size=${playlist.size}")
        if (_sleepTimerState.value.isActive && _sleepTimerState.value.stopAfterCurrentTrack) {
            pause()
            seekTo(0)
            cancelSleepTimer()
            return
        }

        when (_playbackState.value.repeatMode) {
            RepeatMode.ONE -> {
                seekTo(0)
                play()
            }
            RepeatMode.ALL -> {
                playNext()
            }
            RepeatMode.OFF -> {
                if (currentIndex + 1 < playlist.size) {
                    playNext()
                } else {
                    pause()
                    seekTo(0)
                }
            }
        }
    }

    fun playQueue(tracks: List<TrackEntity>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        originalPlaylist = tracks.toMutableList()
        playlist = tracks.toMutableList()
        currentIndex = startIndex.coerceIn(0, playlist.size - 1)
        syncQueueState()

        if (_playbackState.value.shuffleMode != ShuffleMode.OFF) {
            applyShuffleMode(_playbackState.value.shuffleMode)
        } else {
            playCurrentTrack()
        }
    }

    fun playTrack(track: TrackEntity) {
        originalPlaylist = mutableListOf(track)
        playlist = mutableListOf(track)
        currentIndex = 0
        syncQueueState()
        playCurrentTrack()
    }

    fun playSong(song: TrackEntity) = playTrack(song)

    fun playNextInQueue(track: TrackEntity) {
        if (playlist.isEmpty()) {
            playTrack(track)
            return
        }
        val insertIndex = (currentIndex + 1).coerceIn(0, playlist.size)
        playlist.add(insertIndex, track)
        originalPlaylist.add(track)
        syncQueueState()
    }

    fun addToQueue(track: TrackEntity) {
        if (playlist.isEmpty()) {
            playTrack(track)
            return
        }
        playlist.add(track)
        originalPlaylist.add(track)
        syncQueueState()
    }

    fun moveQueueItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition !in playlist.indices || toPosition !in playlist.indices || fromPosition == toPosition) return
        val item = playlist.removeAt(fromPosition)
        playlist.add(toPosition, item)
        if (currentIndex == fromPosition) {
            currentIndex = toPosition
        } else if (fromPosition < currentIndex && toPosition >= currentIndex) {
            currentIndex--
        } else if (fromPosition > currentIndex && toPosition <= currentIndex) {
            currentIndex++
        }
        syncQueueState()
    }

    fun removeQueueItem(position: Int) {
        if (position !in playlist.indices) return
        if (position == currentIndex) {
            if (playlist.size > 1) {
                playNext()
                val removeIdx = if (position < currentIndex) position else position
                playlist.removeAt(removeIdx)
                if (currentIndex > removeIdx) currentIndex--
            } else {
                pause()
                playlist.clear()
                originalPlaylist.clear()
                currentIndex = -1
                _playbackState.value = PlaybackState()
            }
        } else {
            playlist.removeAt(position)
            if (position < currentIndex) {
                currentIndex--
            }
        }
        syncQueueState()
    }

    fun clearUpcomingQueue() {
        if (playlist.isEmpty() || currentIndex !in playlist.indices) return
        val past = playlist.take(currentIndex + 1)
        playlist.clear()
        playlist.addAll(past)
        originalPlaylist.clear()
        originalPlaylist.addAll(past)
        syncQueueState()
    }

    fun playQueueIndex(index: Int) {
        if (index in playlist.indices) {
            currentIndex = index
            playCurrentTrack()
        }
    }

    fun startSleepTimer(minutes: Int, stopAfterCurrentTrack: Boolean = false) {
        cancelSleepTimer()
        if (minutes <= 0 && !stopAfterCurrentTrack) return

        val totalSeconds = if (stopAfterCurrentTrack) {
            val remMs = (_playbackState.value.durationMs - _playbackState.value.currentPositionMs).coerceAtLeast(0L)
            (remMs / 1000L).coerceAtLeast(1L)
        } else {
            minutes * 60L
        }

        _sleepTimerState.value = SleepTimerState(
            isActive = true,
            remainingSeconds = totalSeconds,
            initialMinutes = minutes,
            stopAfterCurrentTrack = stopAfterCurrentTrack
        )

        sleepTimerJob = scope.launch {
            var currentSecs = totalSeconds
            while (isActive && currentSecs > 0) {
                delay(1000L)
                currentSecs--

                if (_sleepTimerState.value.stopAfterCurrentTrack) {
                    val remMs = (_playbackState.value.durationMs - _playbackState.value.currentPositionMs).coerceAtLeast(0L)
                    currentSecs = remMs / 1000L
                }

                // Smooth fade-out in final 10 seconds
                if (currentSecs in 1..10) {
                    val targetVol = (currentSecs.toFloat() / 10f).coerceIn(0f, 1f)
                    exoPlayer.volume = targetVol
                    isFadingOut = true
                }

                _sleepTimerState.value = _sleepTimerState.value.copy(remainingSeconds = currentSecs)

                if (currentSecs <= 0) break
            }

            pause()
            exoPlayer.volume = 1.0f
            isFadingOut = false
            _sleepTimerState.value = SleepTimerState(isActive = false, remainingSeconds = 0L)
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (isFadingOut) {
            exoPlayer.volume = 1.0f
            isFadingOut = false
        }
        _sleepTimerState.value = SleepTimerState(isActive = false, remainingSeconds = 0L)
    }

    fun setReplayGainMode(mode: com.hana.spindle.core.ReplayGainMode) {
        replayGainMode = mode
        val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("pref_replaygain_mode", mode.name)
            .putBoolean("pref_replaygain_enabled", mode != com.hana.spindle.core.ReplayGainMode.OFF)
            .apply()
        playlist.getOrNull(currentIndex)?.let { applyReplayGain(File(CueSheetParser.getAudioFilePath(it.path))) }
    }

    fun setReplayGainEnabled(enabled: Boolean) {
        setReplayGainMode(if (enabled) com.hana.spindle.core.ReplayGainMode.TRACK else com.hana.spindle.core.ReplayGainMode.OFF)
    }

    fun setCrossfadeMode(mode: com.hana.spindle.core.CrossfadeMode) {
        crossfadeMode = mode
        audioFxController.crossfadeMode = mode
        context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("pref_crossfade_mode", mode.name)
            .apply()
    }

    fun setReplayGainPreamp(preampDb: Float) {
        replayGainPreampDb = preampDb.coerceIn(-12.0f, 12.0f)
        val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("pref_replaygain_preamp_db", replayGainPreampDb).apply()
        playlist.getOrNull(currentIndex)?.let { applyReplayGain(File(CueSheetParser.getAudioFilePath(it.path))) }
    }

    fun setTapeFormulation(formulation: AudioFxController.TapeFormulation) {
        audioFxController.setTapeFormulation(formulation)
        context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("pref_tape_formulation", formulation.name)
            .apply()
    }

    fun setDolbyMode(mode: AudioFxController.DolbyMode) {
        audioFxController.setDolbyMode(mode)
        context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("pref_dolby_mode", mode.name)
            .apply()
    }

    fun setCrossfeedMode(mode: AudioFxController.CrossfeedMode) {
        audioFxController.setCrossfeedMode(mode)
        context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("pref_dsp_crossfeed_strength", audioFxController.crossfeedStrength)
            .apply()
    }

    fun setCrossfeedStrength(strength: Int) {
        audioFxController.setCrossfeedStrength(strength)
        context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("pref_dsp_crossfeed_strength", audioFxController.crossfeedStrength)
            .apply()
    }

    val isTapeSaturationEnabled: Boolean
        get() = audioFxController.isTapeSaturationEnabled

    fun setTapeSaturation(enabled: Boolean): Boolean {
        if (enabled && !com.hana.spindle.licensing.StudioUnlockManager.isFeatureUnlocked(
                context,
                com.hana.spindle.licensing.StudioUnlockManager.StudioFeature.ANALOG_TAPE_SATURATION
            )
        ) {
            return false
        }
        audioFxController.setTapeSaturationEnabled(enabled)
        context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("pref_tape_saturation_enabled", enabled)
            .apply()
        return true
    }

    private fun applyReplayGain(trackFile: File) {
        if (replayGainMode == com.hana.spindle.core.ReplayGainMode.OFF) {
            currentReplayGainDb = 0f
            if (!isFadingOut) exoPlayer.volume = 1.0f
            return
        }
        currentReplayGainDb = TagParser.extractReplayGainDb(trackFile, replayGainMode)
        val effectiveDb = currentReplayGainDb + replayGainPreampDb
        val targetVolume = if (effectiveDb == 0f) 1.0f else {
            Math.pow(10.0, (effectiveDb / 20.0)).toFloat().coerceIn(0.1f, 1.0f)
        }
        if (!isFadingOut) {
            exoPlayer.volume = targetVolume
        }
    }

    private fun buildMediaItem(track: TrackEntity): MediaItem {
        val physicalPath = CueSheetParser.getAudioFilePath(track.path)
        val uri = Uri.fromFile(File(physicalPath))
        return if (CueSheetParser.isCueVirtualPath(track.path)) {
            val startMs = CueSheetParser.getCueStartTimeMs(track.path)
            val endMs = if (track.durationMs > 0L) startMs + track.durationMs else C.TIME_END_OF_SOURCE
            MediaItem.Builder()
                .setUri(uri)
                .setMediaId(track.path)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
                .build()
        } else {
            MediaItem.Builder()
                .setUri(uri)
                .setMediaId(track.path)
                .build()
        }
    }

    private fun playCurrentTrack() {
        if (currentIndex !in playlist.indices) return
        try {
            (context.applicationContext as? com.hana.spindle.SpindleApp)?.radioStreamEngine?.pause()
        } catch (e: Exception) {
            // ignore
        }
        val track = playlist[currentIndex]
        val physicalPath = CueSheetParser.getAudioFilePath(track.path)
        val file = File(physicalPath)
        android.util.Log.d("AudioEngine", "playCurrentTrack: title='${track.title}', path='${track.path}', physical='$physicalPath', exists=${file.exists()}, length=${file.length()}")

        // Provide full playlist items with CUE-aware sample-accurate clipping
        val mediaItems = playlist.map { buildMediaItem(it) }
        exoPlayer.setMediaItems(mediaItems, currentIndex, 0L)
        exoPlayer.prepare()
        applyReplayGain(file)
        if (crossfadeMode != com.hana.spindle.core.CrossfadeMode.GAPLESS) {
            exoPlayer.volume = 0.02f
        }
        foleyEngine.playSolenoidClack()
        exoPlayer.play()

        val bitrate = if (track.bitrateKbps > 0) track.bitrateKbps else if (track.durationMs > 0 && file.exists()) ((file.length() * 8L) / track.durationMs).toInt() else 1411

        _playbackState.value = _playbackState.value.copy(
            isPlaying = true,
            currentTrack = track,
            currentPositionMs = 0L,
            durationMs = track.durationMs,
            progress = 0f,
            currentLyrics = null,
            activeLyricIndex = -1
        )

        // Increment play count asynchronously
        scope.launch(Dispatchers.IO) {
            try {
                val app = context.applicationContext as? com.hana.spindle.SpindleApp
                app?.database?.trackDao()?.incrementPlayCount(track.id)
            } catch (ignored: Exception) {}
        }

        // Load lyrics asynchronously (with automatic LRCLIB fallback)
        loadLyricsForTrack(track, physicalPath)

        // Update telemetry
        metricsTracker.updateSourceSpecs(
            format = track.fileFormat,
            bitDepth = track.bitDepth,
            sampleRate = track.sampleRate,
            bitrateKbps = bitrate
        )
        saveLastPlayed(track.path, 0L)
        syncQueueState()
    }

    private fun saveLastPlayed(path: String?, pos: Long) {
        if (path.isNullOrEmpty()) return
        try {
            val prefs = context.getSharedPreferences("spindle_playback_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("last_played_song_path", path)
                .putLong("last_played_song_pos", pos)
                .apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun restoreLastPlayedSong() {
        scope.launch(Dispatchers.IO) {
            try {
                val app = context.applicationContext as? com.hana.spindle.SpindleApp ?: return@launch
                val prefs = context.getSharedPreferences("spindle_playback_prefs", Context.MODE_PRIVATE)
                val lastPath = prefs.getString("last_played_song_path", null)
                val lastPos = prefs.getLong("last_played_song_pos", 0L)

                // If no song path was saved or tape was ejected, keep player empty (device name will display)
                if (lastPath.isNullOrEmpty()) return@launch

                val allSongs = app.database.songDao().getAllSongs().firstOrNull() ?: return@launch
                if (allSongs.isEmpty()) return@launch

                val songIndex = allSongs.indexOfFirst { it.path == lastPath }
                if (songIndex < 0) return@launch

                val song = allSongs[songIndex]
                val targetPos = if (lastPos in 0L..song.durationMs) lastPos else 0L

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if (playlist.isNotEmpty() || exoPlayer.isPlaying) return@withContext
                    originalPlaylist = allSongs.toMutableList()
                    playlist = allSongs.toMutableList()
                    currentIndex = songIndex
                    prepareTrackWithoutPlaying(song, targetPos)
                    syncQueueState()
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioEngine", "restoreLastPlayedSong error", e)
            }
        }
    }

    /**
     * Executes authentic cassette tape ejection:
     * 1. Plays mechanical carriage pop foley sound.
     * 2. Stops playback and clears media items from audio engine.
     * 3. Clears queue and active song from state.
     * 4. Removes saved track from persistent preferences so it remains empty on next launch.
     */
    fun ejectCassette() {
        foleyEngine.playCarriageEject()
        try {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        } catch (e: Exception) {
            // ignore
        }
        stopProgressPolling()

        playlist.clear()
        originalPlaylist.clear()
        currentIndex = -1

        _playbackState.value = PlaybackState(
            isPlaying = false,
            currentTrack = null,
            currentPositionMs = 0L,
            durationMs = 0L,
            progress = 0f,
            currentLyrics = null,
            activeLyricIndex = -1
        )

        try {
            val prefs = context.getSharedPreferences("spindle_playback_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .remove("last_played_song_path")
                .remove("last_played_song_pos")
                .apply()
        } catch (e: Exception) {
            // ignore
        }

        syncQueueState()
    }

    private fun prepareTrackWithoutPlaying(track: TrackEntity, initialPositionMs: Long = 0L) {
        val physicalPath = CueSheetParser.getAudioFilePath(track.path)
        val file = File(physicalPath)
        if (!file.exists()) return
        val mediaItems = playlist.map { buildMediaItem(it) }
        exoPlayer.setMediaItems(mediaItems, currentIndex, initialPositionMs)
        exoPlayer.prepare()
        applyReplayGain(file)
        val bitrate = if (track.bitrateKbps > 0) track.bitrateKbps else if (track.durationMs > 0 && file.exists()) ((file.length() * 8L) / track.durationMs).toInt() else 1411

        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            currentTrack = track,
            currentPositionMs = initialPositionMs,
            durationMs = track.durationMs,
            progress = if (track.durationMs > 0) (initialPositionMs.toFloat() / track.durationMs).coerceIn(0f, 1f) else 0f,
            currentLyrics = null,
            activeLyricIndex = -1
        )

        loadLyricsForTrack(track, physicalPath)

        metricsTracker.updateSourceSpecs(
            format = track.fileFormat,
            bitDepth = track.bitDepth,
            sampleRate = track.sampleRate,
            bitrateKbps = bitrate
        )
    }

    override fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            play()
        }
    }

    override fun pause() {
        foleyEngine.playReleaseClick()
        exoPlayer.pause()
        saveLastPlayed(playlist.getOrNull(currentIndex)?.path, exoPlayer.currentPosition)
    }

    override fun play() {
        foleyEngine.playSolenoidClack()
        try {
            (context.applicationContext as? com.hana.spindle.SpindleApp)?.radioStreamEngine?.pause()
        } catch (e: Exception) {
            // ignore
        }
        exoPlayer.play()
    }

    fun toggleShuffle(): ShuffleMode {
        val nextMode = when (_playbackState.value.shuffleMode) {
            ShuffleMode.OFF -> ShuffleMode.ALL
            ShuffleMode.ALL -> ShuffleMode.ALBUM
            ShuffleMode.ALBUM -> ShuffleMode.OFF
        }
        setShuffleMode(nextMode)
        return nextMode
    }

    fun setShuffleMode(mode: ShuffleMode) {
        _playbackState.value = _playbackState.value.copy(shuffleMode = mode)
        applyShuffleMode(mode)
    }

    private fun applyShuffleMode(mode: ShuffleMode) {
        if (originalPlaylist.isEmpty()) return
        val currentSong = playlist.getOrNull(currentIndex) ?: originalPlaylist.firstOrNull()

        when (mode) {
            ShuffleMode.OFF -> {
                playlist = originalPlaylist.toMutableList()
                currentIndex = if (currentSong != null) playlist.indexOfFirst { it.id == currentSong.id }.coerceAtLeast(0) else 0
            }
            ShuffleMode.ALL -> {
                val remaining = originalPlaylist.filter { it.id != currentSong?.id }.shuffled()
                playlist = if (currentSong != null) (listOf(currentSong) + remaining).toMutableList() else remaining.toMutableList()
                currentIndex = 0
            }
            ShuffleMode.ALBUM -> {
                val currentAlbum = currentSong?.album ?: ""
                val albumSongs = originalPlaylist.filter { it.album == currentAlbum }
                val otherSongs = originalPlaylist.filter { it.album != currentAlbum }
                val remainingAlbum = albumSongs.filter { it.id != currentSong?.id }.shuffled()
                val shuffledAlbum = if (currentSong != null) listOf(currentSong) + remainingAlbum else remainingAlbum
                playlist = (shuffledAlbum + otherSongs).toMutableList()
                currentIndex = 0
            }
        }
    }

    fun toggleRepeat(): RepeatMode {
        val nextMode = when (_playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        setRepeatMode(nextMode)
        return nextMode
    }

    fun setRepeatMode(mode: RepeatMode) {
        _playbackState.value = _playbackState.value.copy(repeatMode = mode)
        // RepeatMode.ONE natively loops the single item in ExoPlayer.
        // RepeatMode.ALL and OFF use Player.REPEAT_MODE_OFF so that ExoPlayer triggers STATE_ENDED,
        // allowing AudioEngine.handleTrackEnded() to advance currentIndex and play the next song.
        exoPlayer.repeatMode = if (mode == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    override fun playNext() {
        foleyEngine.playMotorSpool()
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
                        originalPlaylist = allSongs.toMutableList()
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

    override fun playPrevious(forcePreviousSong: Boolean) {
        foleyEngine.playMotorSpool()
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
                            originalPlaylist = allSongs.toMutableList()
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
        for (i in 1 until playlist.size) {
            val candidateIndex = (currentIndex + i) % playlist.size
            if (playlist[candidateIndex].album != currentAlbum) {
                currentIndex = candidateIndex
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
                        val candidateIndex = (dbIndex + i) % allSongs.size
                        if (allSongs[candidateIndex].album != currentAlbum) {
                            originalPlaylist = allSongs.toMutableList()
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
                            originalPlaylist = allSongs.toMutableList()
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
        foleyEngine.playMotorSpool()
        exoPlayer.seekTo(positionMs)
        updateProgress()
    }

    override fun rewind(deltaMs: Long) {
        val current = exoPlayer.currentPosition
        val target = (current - deltaMs).coerceAtLeast(0L)
        seekTo(target)
    }

    override fun fastForward(deltaMs: Long) {
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

    private var lastSaveProgressTime = 0L

    private fun updateProgress() {
        val current = exoPlayer.currentPosition
        val duration = if (exoPlayer.duration > 0) exoPlayer.duration else _playbackState.value.durationMs
        val progress = if (duration > 0) (current.toFloat() / duration).coerceIn(0f, 1f) else 0f

        val lyrics = _playbackState.value.currentLyrics
        val activeIndex = lyrics?.getActiveIndex(current) ?: -1

        _playbackState.value = _playbackState.value.copy(
            currentPositionMs = current,
            durationMs = duration,
            progress = progress,
            activeLyricIndex = activeIndex
        )

        // Equal-power sinusoidal crossfade modulation near track boundaries
        if (!isFadingOut) {
            val effectiveDb = if (replayGainMode == com.hana.spindle.core.ReplayGainMode.OFF) 0f else (currentReplayGainDb + replayGainPreampDb)
            val baseVol = if (effectiveDb == 0f) 1.0f else Math.pow(10.0, (effectiveDb / 20.0)).toFloat().coerceIn(0.1f, 1.0f)

            if (crossfadeMode != com.hana.spindle.core.CrossfadeMode.GAPLESS && duration > crossfadeMode.durationMs) {
                val remainingMs = duration - current
                val fadeMs = crossfadeMode.durationMs

                if (remainingMs in 0L..fadeMs) {
                    val fadeProgress = 1.0f - (remainingMs.toFloat() / fadeMs).coerceIn(0f, 1f)
                    val angle = fadeProgress * (Math.PI / 2.0)
                    val mult = Math.cos(angle).toFloat().coerceIn(0.01f, 1.0f)
                    exoPlayer.volume = baseVol * mult
                } else if (current in 0L..fadeMs && current > 0L) {
                    val fadeProgress = (current.toFloat() / fadeMs).coerceIn(0f, 1f)
                    val angle = fadeProgress * (Math.PI / 2.0)
                    val mult = Math.sin(angle).toFloat().coerceIn(0.01f, 1.0f)
                    exoPlayer.volume = baseVol * mult
                } else {
                    if (exoPlayer.volume != baseVol) {
                        exoPlayer.volume = baseVol
                    }
                }
            } else {
                if (exoPlayer.volume != baseVol) {
                    exoPlayer.volume = baseVol
                }
            }
        }

        val now = System.currentTimeMillis()
        if (now - lastSaveProgressTime > 4000L) {
            lastSaveProgressTime = now
            saveLastPlayed(playlist.getOrNull(currentIndex)?.path, current)
        }
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

    private fun loadLyricsForTrack(track: TrackEntity, physicalPath: String) {
        scope.launch(Dispatchers.IO) {
            val app = context.applicationContext as? com.hana.spindle.SpindleApp
            var lyrics = LyricsParser.loadLyrics(physicalPath, context)
            if (lyrics == null && app != null && app.lyricsFetcher.isAutoDownloadEnabled()) {
                lyrics = app.lyricsFetcher.fetchLyricsForTrack(track)
            }
            if (_playbackState.value.currentTrack?.id == track.id) {
                _playbackState.value = _playbackState.value.copy(currentLyrics = lyrics)
            }
        }
    }

    fun setLyricsOffset(offsetMs: Long) {
        val currentTrack = _playbackState.value.currentTrack ?: return
        val lyrics = _playbackState.value.currentLyrics ?: return
        lyrics.offsetMs = offsetMs
        _playbackState.value = _playbackState.value.copy(currentLyrics = lyrics)
        LyricsParser.saveLyricOffset(context, currentTrack.path, offsetMs)
    }

    suspend fun reloadLyricsForCurrentTrack(forceDownload: Boolean = false): LyricsData? {
        val currentTrack = _playbackState.value.currentTrack ?: return null
        val physicalPath = CueSheetParser.getAudioFilePath(currentTrack.path)
        val app = context.applicationContext as? com.hana.spindle.SpindleApp
        val lyrics = if (forceDownload && app != null) {
            app.lyricsFetcher.fetchLyricsForTrack(currentTrack, forceRefresh = true)
        } else {
            LyricsParser.loadLyrics(physicalPath, context)
        }
        _playbackState.value = _playbackState.value.copy(currentLyrics = lyrics)
        return lyrics
    }

    fun release() {
        try {
            context.unregisterReceiver(becomingNoisyReceiver)
        } catch (ignored: Exception) {}
        stopProgressPolling()
        audioFxController.release()
        exoPlayer.release()
    }
}

/**
 * Custom MediaCodec audio renderer that intercepts Qualcomm OMX FLAC decoders
 * and injects the proprietary ExtendedACodec parameters (bit-width, min/max block size,
 * min/max frame size) extracted directly from the stream CSD header.
 *
 * This prevents Qualcomm's ExtendedACodec from defaulting to 16-sample block sizes and 16-bit
 * resolution, which otherwise crashes or stalls 24-bit 96kHz/192kHz playback.
 */
@UnstableApi
private class QualcommFlacMediaCodecAudioRenderer(
    context: Context,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler,
    eventListener: AudioRendererEventListener,
    audioSink: AudioSink
) : MediaCodecAudioRenderer(
    context,
    mediaCodecSelector,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    audioSink
) {
    override fun getMediaFormat(
        format: Format,
        codecMimeType: String,
        codecMaxInputSize: Int,
        codecOperatingRate: Float
    ): MediaFormat {
        val mediaFormat = super.getMediaFormat(format, codecMimeType, codecMaxInputSize, codecOperatingRate)
        if (codecMimeType == MimeTypes.AUDIO_FLAC || codecMimeType.contains("flac", ignoreCase = true)) {
            val csd = format.initializationData.firstOrNull()
            if (csd != null && csd.size >= 18) {
                val offset = when {
                    csd.size >= 42 && (csd[4].toInt() and 0x7F) == 0 && csd[5] == 0.toByte() && csd[7] == 0x22.toByte() -> 8
                    csd.size >= 38 && csd[0] == 0x66.toByte() && csd[1] == 0x4C.toByte() -> 4
                    else -> 0
                }
                if (csd.size >= offset + 18) {
                    val minBlockSize = ((csd[offset].toInt() and 0xFF) shl 8) or (csd[offset + 1].toInt() and 0xFF)
                    val maxBlockSize = ((csd[offset + 2].toInt() and 0xFF) shl 8) or (csd[offset + 3].toInt() and 0xFF)
                    val minFrameSize = ((csd[offset + 4].toInt() and 0xFF) shl 16) or ((csd[offset + 5].toInt() and 0xFF) shl 8) or (csd[offset + 6].toInt() and 0xFF)
                    val maxFrameSize = ((csd[offset + 7].toInt() and 0xFF) shl 16) or ((csd[offset + 8].toInt() and 0xFF) shl 8) or (csd[offset + 9].toInt() and 0xFF)
                    val b12 = csd[offset + 12].toInt() and 0xFF
                    val bitsPerSample = ((b12 ushr 1) and 0x1F) + 1

                    val bitWidth = when (format.pcmEncoding) {
                        C.ENCODING_PCM_24BIT -> 24
                        C.ENCODING_PCM_32BIT -> 32
                        else -> if (bitsPerSample > 0) bitsPerSample else 16
                    }

                    android.util.Log.i(
                        "AudioEngine",
                        "Injecting Qualcomm FLAC ExtendedACodec keys: bitWidth=$bitWidth, minBlock=$minBlockSize, maxBlock=$maxBlockSize, minFrame=$minFrameSize, maxFrame=$maxFrameSize"
                    )

                    mediaFormat.setInteger("bit-width", bitWidth)
                    mediaFormat.setInteger("bits-per-sample", bitWidth)
                    if (minBlockSize > 0) mediaFormat.setInteger("min-block-size", minBlockSize)
                    if (maxBlockSize > 0) mediaFormat.setInteger("max-block-size", maxBlockSize)
                    if (minFrameSize > 0) mediaFormat.setInteger("min-frame-size", minFrameSize)
                    if (maxFrameSize > 0) mediaFormat.setInteger("max-frame-size", maxFrameSize)
                }
            } else {
                val bitWidth = when (format.pcmEncoding) {
                    C.ENCODING_PCM_24BIT -> 24
                    C.ENCODING_PCM_32BIT -> 32
                    else -> 16
                }
                android.util.Log.i("AudioEngine", "Injecting Qualcomm FLAC ExtendedACodec fallback keys: bitWidth=$bitWidth")
                mediaFormat.setInteger("bit-width", bitWidth)
                mediaFormat.setInteger("bits-per-sample", bitWidth)
                mediaFormat.setInteger("min-block-size", 4096)
                mediaFormat.setInteger("max-block-size", 4096)
            }
        }
        return mediaFormat
    }
}
