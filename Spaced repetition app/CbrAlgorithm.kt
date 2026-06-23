package com.flashcards.domain.algorithm

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Confidence-Based Repetition (CBR) algorithm.
 *
 * Inspired by Brainscape's approach:
 *  - Rating 1-2 → reset interval to 1 day (relearning)
 *  - Rating 3-5 → grow interval by a factor driven by confidence
 *  - Staleness penalty if card is overdue by >2 days
 *  - Interval capped at 180 days
 */
object CbrAlgorithm {

    private const val MAX_INTERVAL_DAYS = 180
    private const val STALE_THRESHOLD_DAYS = 2
    private const val STALE_PENALTY = 0.85

    data class ScheduleResult(
        val newInterval: Int,       // days until next review
        val newRepetitions: Int,    // total successful repetitions
        val nextDueMs: Long         // epoch ms for next review
    )

    /**
     * @param rating        Confidence rating 1–5
     * @param currentInterval  Current interval in days
     * @param repetitions   Number of successful repetitions so far
     * @param nextDueMs     Scheduled due timestamp (ms). Used to detect staleness.
     */
    fun schedule(
        rating: Int,
        currentInterval: Int,
        repetitions: Int,
        nextDueMs: Long
    ): ScheduleResult {
        require(rating in 1..5) { "Rating must be 1–5, got $rating" }

        val nowMs = System.currentTimeMillis()
        val dayMs = 86_400_000L

        var interval: Int
        var newRepetitions: Int

        if (rating <= 2) {
            // Failed — relearn from scratch
            interval = 1
            newRepetitions = 0
        } else {
            // Passed — grow interval
            interval = when (repetitions) {
                0 -> 1
                1 -> 3
                else -> {
                    val growthFactor = 1.5 + (rating - 3) * 0.5   // 1.5 @ 3, 2.0 @ 4, 2.5 @ 5
                    round(currentInterval * growthFactor).toInt()
                }
            }
            newRepetitions = repetitions + 1
        }

        // Staleness penalty: if overdue by more than threshold, shrink the interval
        val overdueDays = max(0L, (nowMs - nextDueMs) / dayMs)
        if (overdueDays > STALE_THRESHOLD_DAYS) {
            interval = max(1, (interval * STALE_PENALTY).toInt())
        }

        interval = min(interval, MAX_INTERVAL_DAYS)

        return ScheduleResult(
            newInterval = interval,
            newRepetitions = newRepetitions,
            nextDueMs = nowMs + interval * dayMs
        )
    }

    /** Returns true if the card is due for review right now. */
    fun isDue(nextDueMs: Long): Boolean = System.currentTimeMillis() >= nextDueMs

    /** Human-readable label for a confidence rating. */
    fun confidenceLabel(rating: Int): String = when (rating) {
        1 -> "Not at all"
        2 -> "Barely"
        3 -> "Somewhat"
        4 -> "Mostly"
        5 -> "Perfectly"
        else -> "—"
    }
}
