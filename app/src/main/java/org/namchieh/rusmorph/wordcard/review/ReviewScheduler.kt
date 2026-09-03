package org.namchieh.rusmorph.wordcard.review

import org.namchieh.rusmorph.wordcard.model.ReviewResult
import org.namchieh.rusmorph.wordcard.model.ReviewState

/**
 * 单词卡片 SRS (间隔重复) 调度算法。
 * 基于改良 SM-2 算法，计算下一次复习间隔与掌握度。
 */
object ReviewScheduler {

    fun computeNextState(
        current: ReviewState,
        result: ReviewResult,
        nowMs: Long = System.currentTimeMillis(),
    ): ReviewState {
        val count = current.reviewCount + 1
        var correct = current.correctCount
        var wrong = current.wrongCount
        var ease = current.easeFactor
        var intervalDays: Double
        var mastery = current.mastery

        when (result) {
            ReviewResult.AGAIN -> {
                wrong += 1
                ease = (ease - 0.2).coerceAtLeast(1.3)
                intervalDays = 0.04 // 约 1 小时后再次复习
                mastery = (mastery - 20).coerceAtLeast(0)
            }
            ReviewResult.HARD -> {
                correct += 1
                ease = (ease - 0.15).coerceAtLeast(1.3)
                intervalDays = if (current.intervalDays <= 0.1) 0.5 else current.intervalDays * 1.2
                mastery = (mastery + 5).coerceAtMost(100)
            }
            ReviewResult.GOOD -> {
                correct += 1
                intervalDays = when {
                    current.intervalDays <= 0.1 -> 1.0
                    current.intervalDays <= 1.0 -> 3.0
                    else -> current.intervalDays * ease
                }
                mastery = (mastery + 15).coerceAtMost(100)
            }
            ReviewResult.EASY -> {
                correct += 1
                ease = (ease + 0.15).coerceAtMost(3.0)
                intervalDays = when {
                    current.intervalDays <= 0.1 -> 2.0
                    current.intervalDays <= 1.0 -> 4.0
                    else -> current.intervalDays * ease * 1.3
                }
                mastery = (mastery + 25).coerceAtMost(100)
            }
        }

        val nextReviewAt = nowMs + (intervalDays * 86_400_000L).toLong()

        return current.copy(
            mastery = mastery,
            reviewCount = count,
            correctCount = correct,
            wrongCount = wrong,
            lastReviewAt = nowMs,
            nextReviewAt = nextReviewAt,
            intervalDays = intervalDays,
            easeFactor = ease,
            lastResult = result,
        )
    }
}
