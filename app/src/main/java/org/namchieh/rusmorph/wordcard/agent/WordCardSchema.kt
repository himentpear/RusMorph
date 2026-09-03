package org.namchieh.rusmorph.wordcard.agent

import com.google.gson.annotations.SerializedName

/**
 * Agent 固定输出 Schema v2.0 DTO
 * 对应重构 Prompt 第三、四章定义的数据契约。
 */
data class WordCardSchemaDto(
    @SerializedName("schema_version") val schemaVersion: String = "2.0",
    @SerializedName("lexeme") val lexeme: LexemeDto? = null,
    @SerializedName("form") val form: FormDto? = null,
    @SerializedName("agent_meta") val agentMeta: AgentMetaDto? = null,
) {
    data class LexemeDto(
        @SerializedName("lemma") val lemma: String? = null,
        @SerializedName("display_form") val displayForm: String? = null,
        @SerializedName("language") val language: String = "ru",
        @SerializedName("basic") val basic: BasicDto? = null,
        @SerializedName("pronunciation") val pronunciation: PronunciationDto? = null,
        @SerializedName("morphology") val morphology: com.google.gson.JsonElement? = null,
        @SerializedName("usage") val usage: UsageDto? = null,
        @SerializedName("learning") val learning: LearningDto? = null,
    )

    data class BasicDto(
        @SerializedName("part_of_speech") val partOfSpeech: String? = null,
        @SerializedName("gender") val gender: String? = null,
        @SerializedName("animacy") val animacy: Boolean? = null,
        @SerializedName("cefr") val cefr: String? = null,
        @SerializedName("translations_zh") val translationsZh: List<String>? = null,
        @SerializedName("short_definition_zh") val shortDefinitionZh: String? = null,
    )

    data class PronunciationDto(
        @SerializedName("stress_index") val stressIndex: Int? = null,
        @SerializedName("stress_pattern") val stressPattern: String? = null,
        @SerializedName("ipa") val ipa: String? = null,
        @SerializedName("syllables") val syllables: List<String>? = null,
        @SerializedName("pronunciation_note") val pronunciationNote: String? = null,
    )

    data class NounMorphologyDto(
        @SerializedName("declension_type") val declensionType: String? = null,
        @SerializedName("stem") val stem: String? = null,
        @SerializedName("stress_pattern") val stressPattern: String? = null,
        @SerializedName("declension") val declension: NounDeclensionDto? = null,
    )

    data class NounDeclensionDto(
        @SerializedName("singular") val singular: CaseFormsDto? = null,
        @SerializedName("plural") val plural: CaseFormsDto? = null,
    )

    data class CaseFormsDto(
        @SerializedName("nominative") val nominative: String? = null,
        @SerializedName("genitive") val genitive: String? = null,
        @SerializedName("dative") val dative: String? = null,
        @SerializedName("accusative") val accusative: String? = null,
        @SerializedName("instrumental") val instrumental: String? = null,
        @SerializedName("prepositional") val prepositional: String? = null,
    )

    data class VerbMorphologyDto(
        @SerializedName("aspect") val aspect: String? = null,
        @SerializedName("aspect_pair") val aspectPair: String? = null,
        @SerializedName("conjugation_type") val conjugationType: String? = null,
        @SerializedName("reflexive") val reflexive: Boolean? = null,
        @SerializedName("present") val present: VerbPersonsDto? = null,
        @SerializedName("future") val future: VerbPersonsDto? = null,
        @SerializedName("past") val past: VerbPastDto? = null,
        @SerializedName("imperative") val imperative: VerbImperativeDto? = null,
    )

    data class VerbPersonsDto(
        @SerializedName("1sg") val firstSingular: String? = null,
        @SerializedName("2sg") val secondSingular: String? = null,
        @SerializedName("3sg") val thirdSingular: String? = null,
        @SerializedName("1pl") val firstPlural: String? = null,
        @SerializedName("2pl") val secondPlural: String? = null,
        @SerializedName("3pl") val thirdPlural: String? = null,
    )

    data class VerbPastDto(
        @SerializedName("masculine") val masculine: String? = null,
        @SerializedName("feminine") val feminine: String? = null,
        @SerializedName("neuter") val neuter: String? = null,
        @SerializedName("plural") val plural: String? = null,
    )

    data class VerbImperativeDto(
        @SerializedName("singular") val singular: String? = null,
        @SerializedName("plural") val plural: String? = null,
    )

    data class AdjectiveMorphologyDto(
        @SerializedName("adjective_type") val adjectiveType: String? = null,
        @SerializedName("short_forms") val shortForms: AdjectiveFormsDto? = null,
        @SerializedName("agreement") val agreement: AdjectiveAgreementDto? = null,
        @SerializedName("comparison") val comparison: AdjectiveComparisonDto? = null,
    )

    data class AdjectiveFormsDto(
        @SerializedName("masculine") val masculine: String? = null,
        @SerializedName("feminine") val feminine: String? = null,
        @SerializedName("neuter") val neuter: String? = null,
        @SerializedName("plural") val plural: String? = null,
    )

    data class AdjectiveAgreementDto(
        @SerializedName("nominative") val nominative: AdjectiveFormsDto? = null,
    )

    data class AdjectiveComparisonDto(
        @SerializedName("comparative") val comparative: String? = null,
        @SerializedName("superlative") val superlative: String? = null,
    )

    data class UsageDto(
        @SerializedName("example_ru") val exampleRu: String? = null,
        @SerializedName("example_zh") val exampleZh: String? = null,
        @SerializedName("collocations") val collocations: List<CollocationDto>? = null,
    )

    data class CollocationDto(
        @SerializedName("phrase_ru") val phraseRu: String? = null,
        @SerializedName("translation_zh") val translationZh: String? = null,
    )

    data class LearningDto(
        @SerializedName("common_errors") val commonErrors: List<String>? = null,
        @SerializedName("memory_hint") val memoryHint: String? = null,
        @SerializedName("recommended_focus") val recommendedFocus: List<String>? = null,
    )

    data class FormDto(
        @SerializedName("input_form") val inputForm: String? = null,
        @SerializedName("display_form") val displayForm: String? = null,
        @SerializedName("is_lemma") val isLemma: Boolean? = null,
        @SerializedName("analysis") val analysis: FormAnalysisDto? = null,
        @SerializedName("analyses") val analyses: List<FormAnalysisDto>? = null,
        @SerializedName("explanation_zh") val explanationZh: String? = null,
    )

    data class FormAnalysisDto(
        @SerializedName("case") val case: String? = null,
        @SerializedName("number") val number: String? = null,
        @SerializedName("gender") val gender: String? = null,
        @SerializedName("person") val person: String? = null,
        @SerializedName("tense") val tense: String? = null,
        @SerializedName("mood") val mood: String? = null,
        @SerializedName("aspect") val aspect: String? = null,
        @SerializedName("note_zh") val noteZh: String? = null,
    )

    data class AgentMetaDto(
        @SerializedName("confidence") val confidence: Double? = null,
        @SerializedName("needs_review") val needsReview: Boolean? = null,
        @SerializedName("uncertain_fields") val uncertainFields: List<String>? = null,
    )
}
