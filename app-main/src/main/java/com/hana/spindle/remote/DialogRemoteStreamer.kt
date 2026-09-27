package com.hana.spindle.remote

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hana.spindle.databinding.DialogRemoteStreamerBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * BottomSheetDialogFragment providing DLNA / UPnP and Volumio remote transport
 * streamer discovery, device pairing, and transport control.
 */
class DialogRemoteStreamer(
    private val remoteManager: RemoteTransportManager
) : BottomSheetDialogFragment() {

    private var _binding: DialogRemoteStreamerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogRemoteStreamerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCloseStreamer.setOnClickListener {
            dismiss()
        }

        binding.btnScanStreamers.setOnClickListener {
            remoteManager.startDiscovery()
        }

        binding.btnDisconnectRemote.setOnClickListener {
            remoteManager.disconnect()
        }

        binding.btnRemotePlayPause.setOnClickListener {
            remoteManager.togglePlayPause()
        }

        binding.btnRemoteStop.setOnClickListener {
            remoteManager.stop()
        }

        binding.btnRemotePrev.setOnClickListener {
            remoteManager.previous()
        }

        binding.btnRemoteNext.setOnClickListener {
            remoteManager.next()
        }

        binding.seekRemoteVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    remoteManager.setVolume(progress)
                }
                binding.tvRemoteVolumeLabel.text = "REMOTE VOLUME: $progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Observe scanning state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                remoteManager.isScanning.collectLatest { scanning ->
                    binding.btnScanStreamers.isEnabled = !scanning
                    binding.btnScanStreamers.text = if (scanning) "SCANNING..." else "⟳ SCAN SUBNET"
                }
            }
        }

        // Observe active device connection
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                remoteManager.activeDevice.collectLatest { active ->
                    if (active != null) {
                        binding.cardActiveRemote.visibility = View.VISIBLE
                        binding.cardLocalAudioRoute.visibility = View.GONE
                        binding.tvActiveRemoteName.text = active.name
                        binding.tvActiveRemoteDetails.text = active.displaySubtitle
                        binding.tvActiveRemoteBadge.text = "● ${active.typeBadge} ACTIVE"
                    } else {
                        binding.cardActiveRemote.visibility = View.GONE
                        binding.cardLocalAudioRoute.visibility = View.VISIBLE
                    }
                    updateDiscoveredDevicesList(remoteManager.discoveredDevices.value, active)
                }
            }
        }

        // Observe transport playback telemetry
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                remoteManager.transportStatus.collectLatest { status ->
                    val hasTitle = status.title.isNotBlank()
                    binding.tvRemoteNowPlayingTitle.text = if (hasTitle) status.title else "Remote Streamer Ready"
                    binding.tvRemoteNowPlayingArtist.text = if (status.artist.isNotBlank()) {
                        val albumPart = if (status.album.isNotBlank()) " • ${status.album}" else ""
                        "${status.artist}$albumPart"
                    } else {
                        "Ready for transport commands"
                    }

                    binding.btnRemotePlayPause.text = if (status.isPlaying) "⏸ PAUSE" else "▶ PLAY"

                    val posStr = UpnpSoapClient.formatMsToTimeString(status.positionMs)
                    val durStr = UpnpSoapClient.formatMsToTimeString(status.durationMs)
                    binding.tvRemotePosition.text = posStr
                    binding.tvRemoteDuration.text = durStr

                    if (status.durationMs > 0) {
                        val pct = ((status.positionMs.toDouble() / status.durationMs) * 100).toInt().coerceIn(0, 100)
                        binding.progressRemoteTransport.progress = pct
                    } else {
                        binding.progressRemoteTransport.progress = 0
                    }

                    binding.seekRemoteVolume.progress = status.volume
                    binding.tvRemoteVolumeLabel.text = "REMOTE VOLUME: ${status.volume}%"
                }
            }
        }

        // Observe discovered devices
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                remoteManager.discoveredDevices.collectLatest { devices ->
                    updateDiscoveredDevicesList(devices, remoteManager.activeDevice.value)
                }
            }
        }

        // Trigger discovery on initial open if empty
        if (remoteManager.discoveredDevices.value.isEmpty()) {
            remoteManager.startDiscovery()
        }
    }

    private fun updateDiscoveredDevicesList(devices: List<RemoteDevice>, activeDevice: RemoteDevice?) {
        val container = binding.containerStreamersList
        container.removeAllViews()

        if (devices.isEmpty()) {
            val emptyTv = TextView(requireContext()).apply {
                text = "No UPnP/DLNA renderers found on local Wi-Fi.\nEnsure the streamer is powered on and connected to the same network."
                setTextColor(Color.parseColor("#71717A"))
                textSize = 11.5f
                setPadding(0, 16, 0, 16)
            }
            container.addView(emptyTv)
            return
        }

        for (device in devices) {
            val isCurrentActive = activeDevice?.id == device.id
            val row = createDeviceRow(device, isCurrentActive)
            container.addView(row)
        }
    }

    private fun createDeviceRow(device: RemoteDevice, isCurrentActive: Boolean): View {
        val connectAction = View.OnClickListener {
            if (isCurrentActive) {
                remoteManager.disconnect()
            } else {
                remoteManager.connectDevice(device)
            }
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(if (isCurrentActive) Color.parseColor("#1B2A22") else Color.parseColor("#15171C"))
            setPadding(20, 16, 20, 16)
            isClickable = true
            isFocusable = true
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 10)
            }
            layoutParams = params
            setOnClickListener(connectAction)
        }

        // Left info
        val left = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvName = TextView(requireContext()).apply {
            text = device.name
            setTextColor(Color.parseColor("#E4E4E7"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }

        val tvSub = TextView(requireContext()).apply {
            text = device.displaySubtitle
            setTextColor(Color.parseColor("#71717A"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
        }

        left.addView(tvName)
        left.addView(tvSub)

        // Right button
        val btnAction = TextView(requireContext()).apply {
            text = if (isCurrentActive) "ACTIVE" else "CONNECT"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(if (isCurrentActive) Color.parseColor("#00E676") else Color.parseColor("#38BDF8"))
            setBackgroundColor(if (isCurrentActive) Color.parseColor("#162E20") else Color.parseColor("#1A2B38"))
            setPadding(28, 12, 28, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener(connectAction)
        }

        row.addView(left)
        row.addView(btnAction)

        return row
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
