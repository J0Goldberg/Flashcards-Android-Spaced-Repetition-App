package com.flashcards.domain.algorithm

import org.junit.Assert.*
import org.junit.Test

class CbrAlgorithmTest {

    private val nowMs = System.currentTimeMillis()
    private val dayMs = 86_400_000L

    // ── Scheduling logic ─────────────────────────────────────────────────────

    @Test fun `rating 1 resets interval to 1 day`() {
        val result = CbrAlgorithm.schedule(1, currentInterval = 10, repetitions = 5, nextDueMs = nowMs)
        assertEquals(1, result.newInterval)
        assertEquals(0, result.newRepetitions)
    }

    @Test fun `rating 2 resets interval to 1 day`() {
        val result = CbrAlgorithm.schedule(2, currentInterval = 10, repetitions = 5, nextDueMs = nowMs)
        assertEquals(1, result.newInterval)
    }

    @Test fun `first successful rep gives interval of 1`() {
        val result = CbrAlgorithm.schedule(3, currentInterval = 1, repetitions = 0, nextDueMs = nowMs)
        assertEquals(1, result.newInterval)
        assertEquals(1, result.newRepetitions)
    }

    @Test fun `second rep gives interval of 3`() {
        val result = CbrAlgorithm.schedule(3, currentInterval = 1, repetitions = 1, nextDueMs = nowMs)
        assertEquals(3, result.newInterval)
        assertEquals(2, result.newRepetitions)
    }

    @Test fun `rating 5 grows interval faster than rating 3`() {
        val r3 = CbrAlgorithm.schedule(3, currentInterval = 6, repetitions = 2, nextDueMs = nowMs)
        val r5 = CbrAlgorithm.schedule(5, currentInterval = 6, repetitions = 2, nextDueMs = nowMs)
        assertTrue(r5.newInterval > r3.newInterval)
    }

    @Test fun `interval is capped at 180 days`() {
        val result = CbrAlgorithm.schedule(5, currentInterval = 150, repetitions = 10, nextDueMs = nowMs)
        assertTrue(result.newInterval <= 180)
    }

    @Test fun `staleness reduces interval`() {
        val overdue = nowMs - 10 * dayMs            // 10 days overdue
        val onTime  = nowMs                          // due right now

        val stale  = CbrAlgorithm.schedule(5, currentInterval = 20, repetitions = 5, nextDueMs = overdue)
        val fresh  = CbrAlgorithm.schedule(5, currentInterval = 20, repetitions = 5, nextDueMs = onTime)

        assertTrue(stale.newInterval < fresh.newInterval)
    }

    @Test fun `nextDueMs is in the future`() {
        val result = CbrAlgorithm.schedule(4, currentInterval = 5, repetitions = 3, nextDueMs = nowMs)
        assertTrue(result.nextDueMs > nowMs)
    }

    // ── isDue ────────────────────────────────────────────────────────────────

    @Test fun `isDue returns true for past timestamp`() {
        assertTrue(CbrAlgorithm.isDue(nowMs - dayMs))
    }

    @Test fun `isDue returns false for future timestamp`() {
        assertFalse(CbrAlgorithm.isDue(nowMs + dayMs))
    }

    // ── confidenceLabel ──────────────────────────────────────────────────────

    @Test fun `confidence labels match expected strings`() {
        assertEquals("Not at all", CbrAlgorithm.confidenceLabel(1))
        assertEquals("Barely",     CbrAlgorithm.confidenceLabel(2))
        assertEquals("Somewhat",   CbrAlgorithm.confidenceLabel(3))
        assertEquals("Mostly",     CbrAlgorithm.confidenceLabel(4))
        assertEquals("Perfectly",  CbrAlgorithm.confidenceLabel(5))
    }

    // ── Edge cases ───────────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `rating 0 throws`() {
        CbrAlgorithm.schedule(0, 1, 0, nowMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rating 6 throws`() {
        CbrAlgorithm.schedule(6, 1, 0, nowMs)
    }
}
