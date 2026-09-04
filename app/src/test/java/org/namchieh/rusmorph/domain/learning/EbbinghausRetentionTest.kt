package org.namchieh.rusmorph.domain.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EbbinghausRetentionTest {

    @Test
    fun retentionIsOneWhenNoPreviousReview() {
        val retention = EbbinghausRetention.calculateWordRetention(lastReviewAt = null)
        assertEquals(1.0f, retention, 0.001f)
    }

    @Test
    fun retentionReachesNinetyPercentAtStabilityInterval() {
        val intervalDays = 2.0
        val easeFactor = 2.0
        val stabilityDays = intervalDays * (easeFactor / 2.0)
        val stabilityMillis = (stabilityDays * 86_400_000.0).toLong()

        val lastReviewedAt = 1_000_000_000L
        val nowMs = lastReviewedAt + stabilityMillis

        val retention = EbbinghausRetention.calculateWordRetention(
            lastReviewAt = lastReviewedAt,
            intervalDays = intervalDays,
            easeFactor = easeFactor,
            nowMs = nowMs,
        )
        assertEquals(0.90f, retention, 0.01f)
    }

    @Test
    fun retentionDecaysFurtherWhenOverdue() {
        val intervalDays = 1.0
        val lastReviewedAt = 1_000_000_000L
        val dueAt = lastReviewedAt + 86_400_000L
        val oneDayOverdue = dueAt + 86_400_000L

        val retentionOverdue = EbbinghausRetention.calculateWordRetention(
            lastReviewAt = lastReviewedAt,
            intervalDays = intervalDays,
            easeFactor = 2.0,
            nowMs = oneDayOverdue,
        )
        assertTrue(retentionOverdue < 0.90f)
        assertTrue(retentionOverdue >= 0.0f)
    }

    @Test
    fun retentionBadgesMatchRetentionLevels() {
        assertTrue(EbbinghausRetention.getRetentionBadge(0.95f).second.contains("巩固期"))
        assertTrue(EbbinghausRetention.getRetentionBadge(0.80f).second.contains("记忆期"))
        assertTrue(EbbinghausRetention.getRetentionBadge(0.65f).second.contains("临界期"))
        assertTrue(EbbinghausRetention.getRetentionBadge(0.40f).second.contains("遗忘期"))
    }

    @Test
    fun averageRetentionWithEmptyListDefaultsToEightyFivePercent() {
        val avg = EbbinghausRetention.calculateAverageRetention(emptyList())
        assertEquals(0.85f, avg, 0.001f)
    }

    @Test
    fun averageRetentionComputesMeanAcrossItems() {
        val now = 1_000_000_000L
        val itemFresh = WordRetentionRecord(
            entryId = "1",
            lastReviewAt = now,
            intervalDays = 1.0,
            easeFactor = 2.0,
        )
        val itemDue = WordRetentionRecord(
            entryId = "2",
            lastReviewAt = now - 86_400_000L,
            intervalDays = 1.0,
            easeFactor = 2.0,
        )

        val avg = EbbinghausRetention.calculateAverageRetention(listOf(itemFresh, itemDue), nowMs = now)
        // itemFresh = 1.0, itemDue = 0.90 -> mean = 0.95
        assertEquals(0.95f, avg, 0.02f)
    }
}
