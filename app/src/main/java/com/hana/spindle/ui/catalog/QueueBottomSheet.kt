package com.hana.spindle.ui.catalog

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hana.spindle.SpindleApp
import com.hana.spindle.data.db.SongEntity
import com.hana.spindle.databinding.ItemQueueSongBinding
import com.hana.spindle.databinding.LayoutQueueSheetBinding
import com.hana.spindle.playback.AudioEngine
import com.hana.spindle.theme.CassetteTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

class QueueBottomSheet : BottomSheetDialogFragment() {

    private var _binding: LayoutQueueSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var audioEngine: AudioEngine
    private lateinit var queueAdapter: QueueAdapter
    private var itemTouchHelper: ItemTouchHelper? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutQueueSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as SpindleApp
        audioEngine = app.audioEngine

        val theme = app.themeManager.currentTheme.value
        val isEink = (theme.id == CassetteTheme.MONOCHROME_EINK.id)
        if (isEink) {
            binding.root.setBackgroundColor(Color.WHITE)
            binding.tvQueueSummary.setTextColor(Color.BLACK)
        }

        queueAdapter = QueueAdapter(
            onItemClicked = { index ->
                audioEngine.playQueueIndex(index)
            },
            onRemoveClicked = { index ->
                audioEngine.removeQueueItem(index)
            },
            onStartDrag = { holder ->
                itemTouchHelper?.startDrag(holder)
            },
            isEink = isEink
        )

        binding.rvQueueItems.layoutManager = LinearLayoutManager(requireContext())
        binding.rvQueueItems.adapter = queueAdapter

        setupTouchHelper()

        binding.btnClearUpcoming.setOnClickListener {
            audioEngine.clearUpcomingQueue()
        }

        observeQueue()
    }

    private fun setupTouchHelper() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                    audioEngine.moveQueueItem(fromPos, toPos)
                    queueAdapter.notifyItemMoved(fromPos, toPos)
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    audioEngine.removeQueueItem(pos)
                }
            }
        }
        itemTouchHelper = ItemTouchHelper(callback).apply {
            attachToRecyclerView(binding.rvQueueItems)
        }
    }

    private fun observeQueue() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    audioEngine.currentQueueFlow.collectLatest { queue ->
                        val activeIdx = audioEngine.currentQueueIndexFlow.value
                        updateQueueUI(queue, activeIdx)
                    }
                }
                launch {
                    audioEngine.currentQueueIndexFlow.collectLatest { activeIdx ->
                        val queue = audioEngine.currentQueueFlow.value
                        updateQueueUI(queue, activeIdx)
                    }
                }
            }
        }
    }

    private fun updateQueueUI(queue: List<SongEntity>, activeIndex: Int) {
        queueAdapter.submitQueue(queue, activeIndex)

        if (queue.isEmpty()) {
            binding.tvQueueEmpty.visibility = View.VISIBLE
            binding.rvQueueItems.visibility = View.GONE
            binding.tvQueueSummary.text = "0 Tracks • 00:00 Remaining"
        } else {
            binding.tvQueueEmpty.visibility = View.GONE
            binding.rvQueueItems.visibility = View.VISIBLE

            val upcoming = if (activeIndex in queue.indices) queue.drop(activeIndex) else queue
            val remainingMs = upcoming.sumOf { it.durationMs }
            val remainingFormatted = formatTime(remainingMs)
            val activeNum = if (activeIndex >= 0) activeIndex + 1 else 1
            binding.tvQueueSummary.text = "$activeNum of ${queue.size} Playing • $remainingFormatted Remaining"
        }
    }

    private fun formatTime(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class QueueAdapter(
        private val onItemClicked: (Int) -> Unit,
        private val onRemoveClicked: (Int) -> Unit,
        private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
        private val isEink: Boolean
    ) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

        private var items: List<SongEntity> = emptyList()
        private var activeIndex: Int = -1

        fun submitQueue(newItems: List<SongEntity>, newActiveIndex: Int) {
            items = newItems
            activeIndex = newActiveIndex
            notifyDataSetChanged()
        }

        class QueueViewHolder(val b: ItemQueueSongBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
            val b = ItemQueueSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return QueueViewHolder(b)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
            val song = items[position]
            val b = holder.b
            val isCurrent = position == activeIndex

            b.tvQueueIndex.text = String.format(Locale.US, "%02d", position + 1)
            b.tvQueueTitle.text = song.title
            val dur = String.format(
                Locale.US, "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(song.durationMs),
                TimeUnit.MILLISECONDS.toSeconds(song.durationMs) % 60
            )
            b.tvQueueArtistDuration.text = "${song.artist} • $dur"
            b.tvQueueFormat.text = song.fileFormat

            val activeColor = if (isEink) Color.BLACK else Color.parseColor("#F97316")
            val defaultColor = if (isEink) Color.BLACK else Color.parseColor("#FAFAF9")

            if (isCurrent) {
                b.tvQueuePlaying.visibility = View.VISIBLE
                b.tvQueuePlaying.setTextColor(activeColor)
                b.tvQueueTitle.setTextColor(activeColor)
                b.root.setBackgroundColor(if (isEink) Color.parseColor("#F0F0F0") else Color.parseColor("#1C1E2A"))
            } else {
                b.tvQueuePlaying.visibility = View.GONE
                b.tvQueueTitle.setTextColor(defaultColor)
                b.root.setBackgroundColor(Color.TRANSPARENT)
            }

            holder.itemView.setOnClickListener {
                onItemClicked(holder.bindingAdapterPosition)
            }

            b.btnQueueRemove.setOnClickListener {
                onRemoveClicked(holder.bindingAdapterPosition)
            }

            b.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(holder)
                }
                false
            }
        }
    }
}
