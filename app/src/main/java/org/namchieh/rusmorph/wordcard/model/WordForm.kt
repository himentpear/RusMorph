package org.namchieh.rusmorph.wordcard.model

/**
 * 具体词形对象。
 * 记录用户搜索或学习时当前呈现的变格/变位词形及其语法分析。
 */
data class WordForm(
    val inputForm: String,
    val displayForm: String,
    val isLemma: Boolean,
    val analyses: List<FormAnalysis> = emptyList(),
    val explanationZh: String = "",
) {
    val primaryAnalysis: FormAnalysis? get() = analyses.firstOrNull()
}

/**
 * 词形语法形态分析。支持多义形并存。
 */
data class FormAnalysis(
    val grammaticalCase: GrammaticalCase? = null,
    val number: GrammaticalNumber? = null,
    val gender: Gender? = null,
    val person: GrammaticalPerson? = null,
    val tense: Tense? = null,
    val mood: Mood? = null,
    val aspect: Aspect? = null,
    val noteZh: String? = null,
) {
    fun formatShortDescription(): String = buildList {
        grammaticalCase?.let { add(it.labelZh) }
        number?.let { add(it.labelZh) }
        gender?.let { add(it.labelZh) }
        person?.let { add(it.labelZh) }
        tense?.let { add(it.labelZh) }
        aspect?.let { add(it.labelZh) }
        mood?.let { add(it.labelZh) }
    }.joinToString(" · ").ifBlank { noteZh ?: "原形" }
}

enum class GrammaticalCase(val codeRu: String, val labelZh: String) {
    NOMINATIVE("И.", "主格"),
    GENITIVE("Р.", "生格"),
    DATIVE("Д.", "与格"),
    ACCUSATIVE("В.", "宾格"),
    INSTRUMENTAL("Т.", "工具格"),
    PREPOSITIONAL("П.", "前置格"),
}

enum class GrammaticalNumber(val labelZh: String) {
    SINGULAR("单数"),
    PLURAL("复数"),
}

enum class Gender(val labelZh: String) {
    MASCULINE("阳性"),
    FEMININE("阴性"),
    NEUTER("中性"),
}

enum class GrammaticalPerson(val labelZh: String) {
    FIRST("第一人称"),
    SECOND("第二人称"),
    THIRD("第三人称"),
}

enum class Tense(val labelZh: String) {
    PRESENT("现在时"),
    PAST("过去时"),
    FUTURE("将来时"),
}

enum class Mood(val labelZh: String) {
    INDICATIVE("陈述语气"),
    IMPERATIVE("祈使语气"),
    CONDITIONAL("假定语气"),
}

enum class Aspect(val labelZh: String) {
    IMPERFECTIVE("未完成体"),
    PERFECTIVE("完成体"),
}
