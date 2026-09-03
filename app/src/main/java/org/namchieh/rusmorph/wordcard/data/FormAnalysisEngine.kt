package org.namchieh.rusmorph.wordcard.data

import org.namchieh.rusmorph.wordcard.model.*

/**
 * 俄语形态反查引擎与多义形态分析器。
 * 支持从变形（如 студентами, окна, пошёл, людей）反查词元原形并推导所有可能的格位/时态解释。
 */
object FormAnalysisEngine {

    data class CandidateResult(
        val lemma: String,
        val analyses: List<FormAnalysis>,
        val explanationZh: String = "",
    )

    // 已知高频不规则/异根词形表映射
    private val IRREGULAR_FORMS: Map<String, List<Pair<String, FormAnalysis>>> = mapOf(
        "пошёл" to listOf("пойти" to FormAnalysis(tense = Tense.PAST, gender = Gender.MASCULINE, number = GrammaticalNumber.SINGULAR, aspect = Aspect.PERFECTIVE)),
        "пошла" to listOf("пойти" to FormAnalysis(tense = Tense.PAST, gender = Gender.FEMININE, number = GrammaticalNumber.SINGULAR, aspect = Aspect.PERFECTIVE)),
        "пошло" to listOf("пойти" to FormAnalysis(tense = Tense.PAST, gender = Gender.NEUTER, number = GrammaticalNumber.SINGULAR, aspect = Aspect.PERFECTIVE)),
        "пошли" to listOf("пойти" to FormAnalysis(tense = Tense.PAST, number = GrammaticalNumber.PLURAL, aspect = Aspect.PERFECTIVE)),
        "шёл" to listOf("идти" to FormAnalysis(tense = Tense.PAST, gender = Gender.MASCULINE, number = GrammaticalNumber.SINGULAR, aspect = Aspect.IMPERFECTIVE)),
        "шла" to listOf("идти" to FormAnalysis(tense = Tense.PAST, gender = Gender.FEMININE, number = GrammaticalNumber.SINGULAR, aspect = Aspect.IMPERFECTIVE)),
        "шло" to listOf("идти" to FormAnalysis(tense = Tense.PAST, gender = Gender.NEUTER, number = GrammaticalNumber.SINGULAR, aspect = Aspect.IMPERFECTIVE)),
        "шли" to listOf("идти" to FormAnalysis(tense = Tense.PAST, number = GrammaticalNumber.PLURAL, aspect = Aspect.IMPERFECTIVE)),
        "иду" to listOf("идти" to FormAnalysis(tense = Tense.PRESENT, person = GrammaticalPerson.FIRST, number = GrammaticalNumber.SINGULAR, aspect = Aspect.IMPERFECTIVE)),
        "идёшь" to listOf("идти" to FormAnalysis(tense = Tense.PRESENT, person = GrammaticalPerson.SECOND, number = GrammaticalNumber.SINGULAR, aspect = Aspect.IMPERFECTIVE)),
        "идёт" to listOf("идти" to FormAnalysis(tense = Tense.PRESENT, person = GrammaticalPerson.THIRD, number = GrammaticalNumber.SINGULAR, aspect = Aspect.IMPERFECTIVE)),
        "идём" to listOf("идти" to FormAnalysis(tense = Tense.PRESENT, person = GrammaticalPerson.FIRST, number = GrammaticalNumber.PLURAL, aspect = Aspect.IMPERFECTIVE)),
        "идёте" to listOf("идти" to FormAnalysis(tense = Tense.PRESENT, person = GrammaticalPerson.SECOND, number = GrammaticalNumber.PLURAL, aspect = Aspect.IMPERFECTIVE)),
        "идут" to listOf("идти" to FormAnalysis(tense = Tense.PRESENT, person = GrammaticalPerson.THIRD, number = GrammaticalNumber.PLURAL, aspect = Aspect.IMPERFECTIVE)),
        "людей" to listOf("человек" to FormAnalysis(grammaticalCase = GrammaticalCase.GENITIVE, number = GrammaticalNumber.PLURAL), "человек" to FormAnalysis(grammaticalCase = GrammaticalCase.ACCUSATIVE, number = GrammaticalNumber.PLURAL)),
        "люди" to listOf("человек" to FormAnalysis(grammaticalCase = GrammaticalCase.NOMINATIVE, number = GrammaticalNumber.PLURAL)),
        "людям" to listOf("человек" to FormAnalysis(grammaticalCase = GrammaticalCase.DATIVE, number = GrammaticalNumber.PLURAL)),
        "людьми" to listOf("человек" to FormAnalysis(grammaticalCase = GrammaticalCase.INSTRUMENTAL, number = GrammaticalNumber.PLURAL)),
        "людях" to listOf("человек" to FormAnalysis(grammaticalCase = GrammaticalCase.PREPOSITIONAL, number = GrammaticalNumber.PLURAL)),
        "окна" to listOf(
            "окно" to FormAnalysis(grammaticalCase = GrammaticalCase.GENITIVE, number = GrammaticalNumber.SINGULAR, gender = Gender.NEUTER),
            "окно" to FormAnalysis(grammaticalCase = GrammaticalCase.NOMINATIVE, number = GrammaticalNumber.PLURAL, gender = Gender.NEUTER),
            "окно" to FormAnalysis(grammaticalCase = GrammaticalCase.ACCUSATIVE, number = GrammaticalNumber.PLURAL, gender = Gender.NEUTER),
        ),
        "студентами" to listOf(
            "студент" to FormAnalysis(grammaticalCase = GrammaticalCase.INSTRUMENTAL, number = GrammaticalNumber.PLURAL, gender = Gender.MASCULINE)
        ),
        "студента" to listOf(
            "студент" to FormAnalysis(grammaticalCase = GrammaticalCase.GENITIVE, number = GrammaticalNumber.SINGULAR, gender = Gender.MASCULINE),
            "студент" to FormAnalysis(grammaticalCase = GrammaticalCase.ACCUSATIVE, number = GrammaticalNumber.SINGULAR, gender = Gender.MASCULINE)
        ),
        "студенту" to listOf(
            "студент" to FormAnalysis(grammaticalCase = GrammaticalCase.DATIVE, number = GrammaticalNumber.SINGULAR, gender = Gender.MASCULINE)
        ),
        "студентом" to listOf(
            "студент" to FormAnalysis(grammaticalCase = GrammaticalCase.INSTRUMENTAL, number = GrammaticalNumber.SINGULAR, gender = Gender.MASCULINE)
        ),
        "студенте" to listOf(
            "студент" to FormAnalysis(grammaticalCase = GrammaticalCase.PREPOSITIONAL, number = GrammaticalNumber.SINGULAR, gender = Gender.MASCULINE)
        ),
        "студенты" to listOf(
            "студент" to FormAnalysis(grammaticalCase = GrammaticalCase.NOMINATIVE, number = GrammaticalNumber.PLURAL, gender = Gender.MASCULINE)
        ),
        "студентов" to listOf(
            "студент" to FormAnalysis(grammaticalCase = GrammaticalCase.GENITIVE, number = GrammaticalNumber.PLURAL, gender = Gender.MASCULINE),
            "студент" to FormAnalysis(grammaticalCase = GrammaticalCase.ACCUSATIVE, number = GrammaticalNumber.PLURAL, gender = Gender.MASCULINE)
        ),
    )

    /**
     * 分析输入词形，返回候选原形与语法分析列表
     */
    fun analyze(surfaceForm: String): List<CandidateResult> {
        val clean = surfaceForm.replace("́", "").trim().lowercase()
        if (clean.isBlank()) return emptyList()

        // 1. 检查已知特殊形态表
        val known = IRREGULAR_FORMS[clean]
        if (!known.isNullOrEmpty()) {
            val grouped = known.groupBy({ it.first }, { it.second })
            return grouped.map { (lemma, analyses) ->
                CandidateResult(
                    lemma = lemma,
                    analyses = analyses,
                    explanationZh = analyses.joinToString(" / ") { it.formatShortDescription() },
                )
            }
        }

        // 2. 基于俄语通用屈折词尾的规则推导
        val derived = deriveRegularCandidates(clean)
        return derived
    }

    private fun deriveRegularCandidates(word: String): List<CandidateResult> {
        val candidates = mutableListOf<CandidateResult>()

        // 2.1 复数工具格 -ами / -ями
        if (word.endsWith("ами") && word.length > 4) {
            val stem = word.removeSuffix("ами")
            val lemma = stem // 阳性辅音结尾，如 студент -> студентами
            candidates.add(
                CandidateResult(
                    lemma = lemma,
                    analyses = listOf(FormAnalysis(grammaticalCase = GrammaticalCase.INSTRUMENTAL, number = GrammaticalNumber.PLURAL)),
                    explanationZh = "工具格 · 复数"
                )
            )
            // 阴性 -а 结尾，如 комната -> комнатами
            candidates.add(
                CandidateResult(
                    lemma = stem + "а",
                    analyses = listOf(FormAnalysis(grammaticalCase = GrammaticalCase.INSTRUMENTAL, number = GrammaticalNumber.PLURAL)),
                    explanationZh = "工具格 · 复数"
                )
            )
        } else if (word.endsWith("ями") && word.length > 4) {
            val stem = word.removeSuffix("ями")
            candidates.add(
                CandidateResult(
                    lemma = stem + "я",
                    analyses = listOf(FormAnalysis(grammaticalCase = GrammaticalCase.INSTRUMENTAL, number = GrammaticalNumber.PLURAL)),
                    explanationZh = "工具格 · 复数"
                )
            )
        }

        // 2.2 复数与格 -ам / -ям
        if (word.endsWith("ам") && word.length > 3) {
            val stem = word.removeSuffix("ам")
            candidates.add(CandidateResult(lemma = stem, analyses = listOf(FormAnalysis(grammaticalCase = GrammaticalCase.DATIVE, number = GrammaticalNumber.PLURAL)), explanationZh = "与格 · 复数"))
            candidates.add(CandidateResult(lemma = stem + "а", analyses = listOf(FormAnalysis(grammaticalCase = GrammaticalCase.DATIVE, number = GrammaticalNumber.PLURAL)), explanationZh = "与格 · 复数"))
        }

        // 2.3 复数前置格 -ах / -ях
        if (word.endsWith("ах") && word.length > 3) {
            val stem = word.removeSuffix("ах")
            candidates.add(CandidateResult(lemma = stem, analyses = listOf(FormAnalysis(grammaticalCase = GrammaticalCase.PREPOSITIONAL, number = GrammaticalNumber.PLURAL)), explanationZh = "前置格 · 复数"))
            candidates.add(CandidateResult(lemma = stem + "а", analyses = listOf(FormAnalysis(grammaticalCase = GrammaticalCase.PREPOSITIONAL, number = GrammaticalNumber.PLURAL)), explanationZh = "前置格 · 复数"))
        }

        // 2.4 阳性生格/宾格 -а / -я (同时也是中性词复数主格，如 окна -> окно)
        if (word.endsWith("а") && word.length > 2) {
            val stem = word.removeSuffix("а")
            candidates.add(
                CandidateResult(
                    lemma = stem + "о",
                    analyses = listOf(
                        FormAnalysis(grammaticalCase = GrammaticalCase.GENITIVE, number = GrammaticalNumber.SINGULAR, gender = Gender.NEUTER),
                        FormAnalysis(grammaticalCase = GrammaticalCase.NOMINATIVE, number = GrammaticalNumber.PLURAL, gender = Gender.NEUTER),
                        FormAnalysis(grammaticalCase = GrammaticalCase.ACCUSATIVE, number = GrammaticalNumber.PLURAL, gender = Gender.NEUTER),
                    ),
                    explanationZh = "生格 · 单数 或 主格/宾格 · 复数"
                )
            )
            candidates.add(
                CandidateResult(
                    lemma = stem,
                    analyses = listOf(FormAnalysis(grammaticalCase = GrammaticalCase.GENITIVE, number = GrammaticalNumber.SINGULAR, gender = Gender.MASCULINE)),
                    explanationZh = "生格 · 单数"
                )
            )
        }

        // 2.5 动词过去时 -л, -ла, -ло, -ли
        if (word.endsWith("ла") && word.length > 3) {
            val stem = word.removeSuffix("ла")
            candidates.add(CandidateResult(lemma = stem + "ть", analyses = listOf(FormAnalysis(tense = Tense.PAST, gender = Gender.FEMININE, number = GrammaticalNumber.SINGULAR)), explanationZh = "过去时 · 阴性单数"))
        } else if (word.endsWith("ли") && word.length > 3) {
            val stem = word.removeSuffix("ли")
            candidates.add(CandidateResult(lemma = stem + "ть", analyses = listOf(FormAnalysis(tense = Tense.PAST, number = GrammaticalNumber.PLURAL)), explanationZh = "过去时 · 复数"))
        }

        return candidates.distinctBy { it.lemma }
    }
}
