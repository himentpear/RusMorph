package org.namchieh.rusmorph.wordcard.model

/**
 * 单词卡片展示/学习模式。
 * 同一份 Lexeme 数据驱动不同的学习认知任务。
 */
enum class CardMode(val labelZh: String, val descriptionZh: String) {
    RECOGNITION("俄汉认读", "俄语 → 中文"),
    RECALL("中文回忆", "中文 → 回忆俄语"),
    MORPHOLOGY("形态辨析", "识别词形、格位与变位"),
    STRESS("重音训练", "隐匿重音符号，翻面核验重音"),
    PRONUNCIATION("语音评测", "跟读比对与 Whisper 智能打分"),
}
