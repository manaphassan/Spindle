package com.hana.spindle.data

import com.hana.spindle.data.db.TrackEntity
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Calculates physical Side A and Side B allocations for vintage cassettes.
 * Balances running duration across Side A and Side B, providing A01/B01 track numbering.
 */
object MixtapePartition {

    data class PartitionResult(
        val sideA: List<TrackEntity>,
        val sideB: List<TrackEntity>,
        val sideADurationMs: Long,
        val sideBDurationMs: Long,
        val splitIndex: Int
    )

    fun partition(tracks: List<TrackEntity>): PartitionResult {
        if (tracks.isEmpty()) {
            return PartitionResult(emptyList(), emptyList(), 0L, 0L, 0)
        }
        if (tracks.size == 1) {
            return PartitionResult(tracks, emptyList(), tracks[0].durationMs, 0L, 1)
        }

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

        val sideA = tracks.take(splitIndex)
        val sideB = tracks.drop(splitIndex)
        val sideADur = sideA.sumOf { it.durationMs }
        val sideBDur = sideB.sumOf { it.durationMs }

        return PartitionResult(sideA, sideB, sideADur, sideBDur, splitIndex)
    }
}
