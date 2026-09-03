package org.namchieh.rusmorph.wordcard.model

/**
 * 核心词元数据模型（语言学事实）。
 * 涵盖原形、词性、发音、完整变格/变位网络、搭配、例句与学习提示。
 */
data class Lexeme(
    val id: String,
    val lemma: String,
    val displayForm: String,
    val language: String = "ru",
    val basic: BasicInfo,
    val pronunciation: PronunciationInfo = PronunciationInfo(),
    val morphology: MorphologyInfo = MorphologyInfo.Empty,
    val usage: UsageInfo = UsageInfo(),
    val learning: LearningInfo = LearningInfo(),
    val schemaVersion: String = "2.0",
) {
    data class BasicInfo(
        val partOfSpeech: String,
        val gender: Gender? = null,
        val animacy: Boolean? = null,
        val cefr: String? = null,
        val translationsZh: List<String> = emptyList(),
        val shortDefinitionZh: String = "",
    ) {
        val primaryTranslation: String
            get() = translationsZh.firstOrNull()?.ifBlank { shortDefinitionZh } ?: shortDefinitionZh
    }

    data class PronunciationInfo(
        val stressIndex: Int? = null,
        val stressPattern: String = "",
        val ipa: String? = null,
        val syllables: List<String> = emptyList(),
        val pronunciationNote: String = "",
    )

    sealed interface MorphologyInfo {
        object Empty : MorphologyInfo

        data class Noun(
            val declensionType: String = "",
            val stem: String = "",
            val stressPattern: String = "",
            val singular: CaseForms = CaseForms(),
            val plural: CaseForms = CaseForms(),
        ) : MorphologyInfo

        data class Verb(
            val aspect: Aspect = Aspect.IMPERFECTIVE,
            val aspectPair: String? = null,
            val conjugationType: String = "",
            val reflexive: Boolean = false,
            val present: PersonForms? = null,
            val future: PersonForms? = null,
            val past: PastForms? = null,
            val imperative: ImperativeForms? = null,
        ) : MorphologyInfo

        data class Adjective(
            val adjectiveType: String = "",
            val shortForms: ShortForms? = null,
            val agreementNominative: GenderForms = GenderForms(),
            val comparative: String? = null,
            val superlative: String? = null,
        ) : MorphologyInfo

        data class Generic(
            val summaryZh: String = "",
            val formsMap: Map<String, String> = emptyMap(),
        ) : MorphologyInfo
    }

    data class CaseForms(
        val nominative: String = "",
        val genitive: String = "",
        val dative: String = "",
        val accusative: String = "",
        val instrumental: String = "",
        val prepositional: String = "",
    ) {
        val isPopulated: Boolean
            get() = nominative.isNotBlank() || genitive.isNotBlank() || dative.isNotBlank()
    }

    data class PersonForms(
        val firstSingular: String = "",
        val secondSingular: String = "",
        val thirdSingular: String = "",
        val firstPlural: String = "",
        val secondPlural: String = "",
        val thirdPlural: String = "",
    ) {
        val isPopulated: Boolean
            get() = firstSingular.isNotBlank() || secondSingular.isNotBlank() || thirdSingular.isNotBlank()
    }

    data class PastForms(
        val masculine: String = "",
        val feminine: String = "",
        val neuter: String = "",
        val plural: String = "",
    )

    data class ImperativeForms(
        val singular: String = "",
        val plural: String = "",
    )

    data class ShortForms(
        val masculine: String = "",
        val feminine: String = "",
        val neuter: String = "",
        val plural: String = "",
    )

    data class GenderForms(
        val masculine: String = "",
        val feminine: String = "",
        val neuter: String = "",
        val plural: String = "",
    )

    data class UsageInfo(
        val exampleRu: String = "",
        val exampleZh: String = "",
        val collocations: List<Collocation> = emptyList(),
        val additionalExamples: List<ExamplePair> = emptyList(),
    ) {
        data class Collocation(val phraseRu: String, val translationZh: String)
        data class ExamplePair(val ru: String, val zh: String)
    }

    data class LearningInfo(
        val commonErrors: List<String> = emptyList(),
        val memoryHint: String = "",
        val recommendedFocus: List<String> = emptyList(),
    )
}
