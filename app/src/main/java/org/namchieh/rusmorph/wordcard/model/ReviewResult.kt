package org.namchieh.rusmorph.wordcard.model

/**
 * 单词复习结果枚举（对应 SRS 评估等级）。
 */
enum class ReviewResult(val labelZh: String, val ratingValue: Int) {
    AGAIN("未掌握", 1),
    HARD("模糊", 2),
    GOOD("已掌握", 3),
    EASY("极熟练", 4),
}
