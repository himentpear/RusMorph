package org.namchieh.rusmorph.domain.learning

import kotlin.math.max
import kotlin.math.pow

/**
 * 艾宾浩斯遗忘曲线与记忆稳定性模型 (基于 FSRS / 经典认知科学标定)。
 *
 * 核心模型：
 * 留存率 R(t) = (0.9)^(Δt / S)
 * 其中：
 * - Δt: 距离上一次复习经过的时间（天数）
 * - S: 记忆稳定性（Stability，天数），即预测记忆留存率降到 90% 所需的时间
 */
object EbbinghausRetention {

    /**
     * 计算单张卡片/词条当前的即时记忆留存率 R ∈ [0.0, 1.0]。
     *
     * @param lastReviewAt 上次复习时间戳（毫秒）。若为 null，则视为新收纳但尚未复习的词条。
     * @param intervalDays 当前卡片 SRS 算法推荐的间隔天数。
     * @param easeFactor 难度系数。
     * @param nowMs 当前时间戳。
     */
    fun calculateWordRetention(
        lastReviewAt: Long?,
        intervalDays: Double = 1.0,
        easeFactor: Double = 2.5,
        nowMs: Long = System.currentTimeMillis(),
    ): Float {
        if (lastReviewAt == null) {
            // 新加入但尚未开始第一轮复习的词汇，设为 100% 初始认知或基准状态
            return 1.0f
        }
        val elapsedMs = maxOf(0L, nowMs - lastReviewAt)
        val elapsedDays = elapsedMs / 86_400_000.0

        // 记忆稳定性 S (以天为单位)，最低不低于 0.2 天（约 5 小时），并受掌握难度系数调节
        val stabilityDays = max(0.2, intervalDays * (easeFactor / 2.0).coerceIn(0.6, 2.0))

        // R = 0.9^(Δt / S)
        val retention = 0.9.pow(elapsedDays / stabilityDays)
        return retention.toFloat().coerceIn(0.05f, 1.0f)
    }

    /**
     * 计算整批词条（例如某本教材全量生词）的平均记忆留存率。
     *
     * @param items 词条复习记录列表
     * @param defaultWhenEmpty 当没有任何词条收纳时返回的基准值（默认 0.85f）
     */
    fun calculateAverageRetention(
        items: List<WordRetentionRecord>,
        defaultWhenEmpty: Float = 0.85f,
        nowMs: Long = System.currentTimeMillis(),
    ): Float {
        if (items.isEmpty()) return defaultWhenEmpty
        val sum = items.sumOf { item ->
            calculateWordRetention(item.lastReviewAt, item.intervalDays, item.easeFactor, nowMs).toDouble()
        }
        return (sum / items.size).toFloat().coerceIn(0.0f, 1.0f)
    }

    /**
     * 留存率文字评价与徽章标签
     */
    fun getRetentionBadge(retention: Float): Pair<String, String> {
        val pct = (retention * 100).toInt()
        return when {
            pct >= 90 -> "$pct%" to "巩固期 · 极佳"
            pct >= 75 -> "$pct%" to "记忆期 · 良好"
            pct >= 60 -> "$pct%" to "临界期 · 待复习"
            else -> "$pct%" to "遗忘期 · 需攻坚"
        }
    }
}

data class WordRetentionRecord(
    val entryId: String,
    val lastReviewAt: Long?,
    val intervalDays: Double = 1.0,
    val easeFactor: Double = 2.5,
    val lessonId: String? = null,
)
