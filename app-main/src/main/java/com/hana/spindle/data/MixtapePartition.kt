package com.hana.spindle.data

import com.hana.spindle.data.db.TrackEntity
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Calculates physical Side A and Side B allocations for vintage cassettes.
 * Balances running duration across Side A and Side B, providing A01/B01 track numbering.
 * Supports standard tape lengths (C-46, C-60, C-90, C-120), custom user split points,
 * and tape overflow detection.
 */
object MixtapePartition {

    enum class TapeCapacity(val label: String, val totalMinutes: Int) {
        C46("C-46", 46),
        C60("C-60", 60),
        C90("C-90", 90),
        C120("C-120", 120);

        val sideMinutes: Int get() = totalMinutes / 2
        val maxSideDurationMs: Long get() = sideMinutes * 60_000L

        companion object {
            val DEFAULT = C90

            fun fromMinutes(minutes: Int): TapeCapacity {
                return values().firstOrNull { it.totalMinutes == minutes } ?: DEFAULT
            }

            fun fromLabel(label: String): TapeCapacity {
                val clean = label.trim().replace(" ", "").uppercase()
                return values().firstOrNull {
                    it.label.replace("-", "").equals(clean.replace("-", ""), ignoreCase = true)
                } ?: DEFAULT
            }
        }
    }

    data class PartitionResult(
        val sideA: List<TrackEntity>,
        val sideB: List<TrackEntity>,
        val sideADurationMs: Long,
        val sideBDurationMs: Long,
        val splitIndex: Int,
        val capacity: TapeCapacity = TapeCapacity.DEFAULT
    ) {
        val maxSideDurationMs: Long get() = capacity.maxSideDurationMs
        val isSideAOverCapacity: Boolean get() = sideADurationMs > maxSideDurationMs
        val isSideBOverCapacity: Boolean get() = sideBDurationMs > maxSideDurationMs
        val isOverCapacity: Boolean get() = isSideAOverCapacity || isSideBOverCapacity
        val sideAOvertimeMs: Long get() = (sideADurationMs - maxSideDurationMs).coerceAtLeast(0L)
        val sideBOvertimeMs: Long get() = (sideBDurationMs - maxSideDurationMs).coerceAtLeast(0L)
    }

    /**
     * Calculates the optimal auto-balanced split index between Side A and Side B.
     */
    fun calculateAutoSplitIndex(tracks: List<TrackEntity>): Int {
        if (tracks.size <= 1) return tracks.size

        val totalDurationMs = tracks.sumOf { it.durationMs }
        val targetHalfMs = totalDurationMs / 2L

        var accumulatedMs = 0L
        var splitIndex = ceil(tracks.size / 2.0).toInt()

        if (totalDurationMs > 0L) {
            for (i in tracks.indices) {
                accumulatedMs += tracks[i].durationMs
                if (accumulatedMs >= targetHalfMs) {
                    val diffWithCurrent = abs(accumulatedMs - targetHalfMs)
                    val diffWithoutCurrent = abs((accumulatedMs - tracks[i].durationMs) - targetHalfMs)
                    splitIndex = if (diffWithCurrent < diffWithoutCurrent && i < tracks.size - 1) {
                        i + 1
                    } else {
                        maxOf(1, i)
                    }
                    break
                }
            }
        }
        return splitIndex.coerceIn(1, tracks.size - 1)
    }

    fun partition(
        tracks: List<TrackEntity>,
        capacity: TapeCapacity = TapeCapacity.DEFAULT,
        customSplitIndex: Int = -1
    ): PartitionResult {
        if (tracks.isEmpty()) {
            return PartitionResult(emptyList(), emptyList(), 0L, 0L, 0, capacity)
        }
        if (tracks.size == 1) {
            return PartitionResult(tracks, emptyList(), tracks[0].durationMs, 0L, 1, capacity)
        }

        val splitIndex = if (customSplitIndex in 1 until tracks.size) {
            customSplitIndex
        } else {
            calculateAutoSplitIndex(tracks)
        }

        val sideA = tracks.take(splitIndex)
        val sideB = tracks.drop(splitIndex)
        val sideADur = sideA.sumOf { it.durationMs }
        val sideBDur = sideB.sumOf { it.durationMs }

        return PartitionResult(sideA, sideB, sideADur, sideBDur, splitIndex, capacity)
    }
}
