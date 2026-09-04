package org.namchieh.rusmorph.wordcard.model

/**
 * 单词学习复习状态。
 * 严格与 Lexeme 语言事实数据隔离保存。
 */
data class ReviewState(
    val lexemeId: String,
    val userId: String = "local_user",
    val mastery: Int = 0, // 0 - 100 掌握度百分比
    val reviewCount: Int = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val lastReviewAt: Long? = null,
    val nextReviewAt: Long? = null,
    val intervalDays: Double = 0.0,
    val easeFactor: Double = 2.5,
    val lastResult: ReviewResult? = null,
    val isInReviewQueue: Boolean = false,
    val weakPoints: WeakPoints = WeakPoints(),
    val pronunciation: PronunciationRecord = PronunciationRecord(),
) {
    data class WeakPoints(
        val meaning: Boolean = false,
        val stress: Boolean = false,
        val pronunciation: Boolean = false,
        val declension: Boolean = false,
        val conjugation: Boolean = false,
    )

    data class PronunciationRecord(
        val bestScore: Double? = null,
        val lastScore: Double? = null,
    )

    val isDue: Boolean
        get() {
            val due = nextReviewAt ?: return true
            return System.currentTimeMillis() >= due
        }

    val isEnrolled: Boolean
        get() = isInReviewQueue || reviewCount > 0

    val ebbinghausRetention: Float
        get() = org.namchieh.rusmorph.domain.learning.EbbinghausRetention.calculateWordRetention(
            lastReviewAt = lastReviewAt,
            intervalDays = if (intervalDays > 0.0) intervalDays else 1.0,
            easeFactor = easeFactor,
        )

    val retentionBadge: Pair<String, String>
        get() = org.namchieh.rusmorph.domain.learning.EbbinghausRetention.getRetentionBadge(ebbinghausRetention)
}
