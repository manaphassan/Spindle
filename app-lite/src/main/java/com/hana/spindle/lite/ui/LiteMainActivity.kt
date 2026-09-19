package com.hana.spindle.lite.ui

import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.hana.spindle.lite.R
import com.hana.spindle.lite.audio.LiteAudioEngine
import com.hana.spindle.lite.audio.PlaybackListener
import com.hana.spindle.lite.audio.PlaybackState
import com.hana.spindle.lite.databinding.ActivityLiteMainBinding
import com.hana.spindle.lite.db.LiteDbHelper
import com.hana.spindle.lite.db.LiteMediaScanner
import com.hana.spindle.lite.db.Track
import com.hana.spindle.lite.receiver.HardwareButtonReceiver
import com.hana.spindle.lite.receiver.NoisyAudioReceiver
import com.hana.spindle.lite.util.DeviceNameFormatter

/**
 * Main Activity for Spindle Lite.
 * Optimized for legacy single-core ARMv7 devices with 512MB RAM and HVGA displays.
 */
class LiteMainActivity : AppCompatActivity(), PlaybackListener {

    private lateinit var binding: ActivityLiteMainBinding
    private lateinit var audioEngine: LiteAudioEngine
    private lateinit var dbHelper: LiteDbHelper
    private lateinit var mediaScanner: LiteMediaScanner
    private lateinit var trackAdapter: LiteTrackAdapter

    private var currentPlaylist: List<Track> = emptyList()
    private var noisyReceiver: NoisyAudioReceiver? = null

    companion object {
        private const val PERMISSION_REQUEST_STORAGE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiteMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = LiteDbHelper.getInstance(this)
        audioEngine = LiteAudioEngine(this).apply {
            listener = this@LiteMainActivity
        }
        mediaScanner = LiteMediaScanner(this)

        setupUI()
        checkPermissionsAndLoad()
        setupHardwareButtonHooks()
    }

    private fun setupUI() {
        // Dynamic Hardware Nameplate with Tap-to-Engrave
        val prefs = getSharedPreferences("spindle_lite_prefs", MODE_PRIVATE)
        var nameplateIdx = prefs.getInt("pref_nameplate_idx", 0)

        fun refreshNameplate() {
            binding.tvDeviceNameplate.text = DeviceNameFormatter.getFormattedNameplate(nameplateIdx)
        }
        refreshNameplate()

        binding.tvDeviceNameplate.setOnClickListener {
            nameplateIdx = (nameplateIdx + 1) % DeviceNameFormatter.NAMEPLATE_PRESETS.size
            prefs.edit().putInt("pref_nameplate_idx", nameplateIdx).apply()
            refreshNameplate()
            Toast.makeText(this, "Engraving: ${binding.tvDeviceNameplate.text}", Toast.LENGTH_SHORT).show()
        }

        // Track List RecyclerView
        trackAdapter = LiteTrackAdapter(emptyList()) { position, track ->
            audioEngine.setPlaylist(currentPlaylist, position)
            binding.drawerLayout.visibility = View.GONE
        }
        binding.rvTrackList.apply {
            layoutManager = LinearLayoutManager(this@LiteMainActivity)
            adapter = trackAdapter
            setHasFixedSize(true)
        }

        // Transport Controls
        binding.btnPlay.setOnClickListener {
            audioEngine.togglePlayPause()
        }

        binding.btnNext.setOnClickListener {
            audioEngine.next()
        }

        binding.btnPrev.setOnClickListener {
            audioEngine.previous()
        }

        binding.btnEject.setOnClickListener {
            toggleDrawer()
        }

        binding.btnCloseDrawer.setOnClickListener {
            binding.drawerLayout.visibility = View.GONE
        }

        binding.btnScan.setOnClickListener {
            performMediaScan()
        }
    }

    private fun toggleDrawer() {
        if (binding.drawerLayout.visibility == View.VISIBLE) {
            binding.drawerLayout.visibility = View.GONE
        } else {
            binding.drawerLayout.visibility = View.VISIBLE
            refreshTrackList()
        }
    }

    private fun checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST_STORAGE
                )
                return
            }
        }
        loadLibrary()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_STORAGE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadLibrary()
        }
    }

    private fun loadLibrary() {
        val tracks = dbHelper.getAllTracks()
        if (tracks.isEmpty()) {
            performMediaScan()
        } else {
            updateLibrary(tracks)
        }
    }

    private fun performMediaScan() {
        binding.tvVaultStatus.text = getString(R.string.scanning)
        mediaScanner.startScan(object : LiteMediaScanner.ScanCallback {
            override fun onScanProgress(foundCount: Int) {
                runOnUiThread {
                    binding.tvVaultStatus.text = "Indexing: $foundCount tracks found..."
                }
            }

            override fun onScanComplete(totalTracks: Int) {
                runOnUiThread {
                    refreshTrackList()
                    binding.tvVaultStatus.text = "Vault Ready: $totalTracks tracks indexed"
                }
            }
        })
    }

    private fun refreshTrackList() {
        val tracks = dbHelper.getAllTracks()
        updateLibrary(tracks)
    }

    private fun updateLibrary(tracks: List<Track>) {
        this.currentPlaylist = tracks
        trackAdapter.updateTracks(tracks)
        binding.tvVaultStatus.text = "Vault: ${tracks.size} tracks"
        if (audioEngine.currentTrack == null && tracks.isNotEmpty()) {
            val firstTrack = tracks[0]
            binding.tvTrackTitle.text = firstTrack.title
            binding.tvTrackArtist.text = firstTrack.artist
            binding.tvFormatBadge.text = firstTrack.formatBadge
            binding.tvTimeReadout.text = "00:00 / ${firstTrack.formattedDuration}"
            binding.deckView.setCassetteLabel(firstTrack.tapeBiasType)
        }
    }

    // --- PlaybackListener Implementation ---

    override fun onPlaybackStateChanged(state: PlaybackState) {
        val track = state.currentTrack
        if (track != null) {
            binding.tvTrackTitle.text = track.title
            binding.tvTrackArtist.text = track.artist
            binding.tvFormatBadge.text = track.formatBadge
            binding.tvTimeReadout.text = "${state.formattedPosition} / ${state.formattedDuration}"
            binding.deckView.setCassetteLabel(track.tapeBiasType)
        }

        binding.btnPlay.setImageResource(
            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )

        binding.deckView.setPlaybackState(state.isPlaying, state.progressFraction)
    }

    override fun onTrackCompleted(track: Track?) {
        // Automatic gapless transition handled by LiteAudioEngine
    }

    override fun onPlaybackError(errorMessage: String) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }

    // --- Hardware Button Interception ---

    private fun setupHardwareButtonHooks() {
        HardwareButtonReceiver.buttonListener = { event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_CAMERA,
                    KeyEvent.KEYCODE_HEADSETHOOK,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        audioEngine.togglePlayPause()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        audioEngine.next()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        audioEngine.previous()
                        true
                    }
                    else -> false
                }
            } else false
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_CAMERA -> {
                audioEngine.togglePlayPause()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                audioEngine.togglePlayPause()
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                audioEngine.next()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                audioEngine.previous()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onStart() {
        super.onStart()
        noisyReceiver = NoisyAudioReceiver {
            audioEngine.pause()
        }
        registerReceiver(
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        )
    }

    override fun onStop() {
        super.onStop()
        noisyReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (ignored: Exception) {}
            noisyReceiver = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.release()
        LiteBitmapCache.clear()
        HardwareButtonReceiver.buttonListener = null
    }
}
