package org.namchieh.rusmorph.data.remote

data class RouteRequestDto(
    val requestId: String,
    val conversationId: String,
    val command: String,
    val context: RouteContextDto,
)
data class RouteContextDto(
    val currentEntryId: String?,
    val currentCardIds: List<String>,
    val currentDeckId: String?,
    val locale: String = "zh-CN",
    val localSearchMiss: Boolean = false,
    val deckLimit: Int = 50,
)
data class RouteResponseDto(val requestId: String, val plan: AgentCommandPlanDto)
data class AgentCommandPlanDto(
    val intent: String,
    val outputMode: String,
    val interpretedQuery: InterpretedQueryDto,
    val retrieval: RetrievalPlanDto,
    val agents: List<String>,
    val needsClarification: Boolean,
    val clarificationQuestion: String?,
)
data class InterpretedQueryDto(
    val raw: String,
    val words: List<String>,
    val meaning: String?,
    val lessonIds: List<Int>,
    val partsOfSpeech: List<String>,
    val morphologyFilters: MorphologyFiltersDto?,
    val topic: String?,
    val referenceTarget: String?,
)
data class MorphologyFiltersDto(
    val genders: List<String>,
    val declensionClasses: List<String>,
    val endingTypes: List<String>,
    val aspects: List<String>,
    val conjugationClasses: List<String>,
    val phoneticAlternations: List<String>,
    val hasPhoneticAlternation: Boolean?,
    val hasPluralStressPattern: Boolean?,
)
data class RetrievalPlanDto(val fuzzy: Boolean, val limit: Int, val requiresLocalSearch: Boolean)

data class ComposeRequestDto(
    val requestId: String,
    val conversationId: String,
    val command: String,
    val plan: AgentCommandPlanDto,
    val localResults: List<LocalLexiconResultDto>,
    val knowledgeContext: List<ComposeKnowledgeDto> = emptyList(),
)
data class LocalLexiconResultDto(
    val entryId: String,
    val displayForm: String,
    val normalizedWord: String,
    val meaningZh: String?,
    val partsOfSpeech: List<String>,
    val lesson: Int?,
    val matchedForm: String?,
    val localFields: LocalFieldsDto,
    val annotations: Map<String, List<String>> = emptyMap(),
)
data class LocalFieldsDto(
    val gender: String?, val declensionClass: String?, val endingType: String?,
    val pluralStressPattern: String?, val aspect: String?, val conjugationClass: String?,
    val phoneticAlternation: String?,
)
data class ComposeKnowledgeDto(
    val chunkId: String,
    val title: String,
    val category: String,
    val content: String,
    val sourceDocument: String,
)
data class ComposeResponseDto(
    val requestId: String,
    val outputMode: String,
    val cards: List<WordCardDto>,
    val deck: CardDeckDto?,
    val plainAnswer: String?,
    val clarification: String?,
    val warnings: List<WarningDto>,
    val grounding: GroundingDto,
    val thinkingSummary: String?,
)
data class WordCardDto(
    val cardId: String,
    val entryId: String,
    val word: WordDto,
    val meanings: List<MeaningDto>,
    val morphology: MorphologyDto,
    val etymology: EtymologyDto?,
    val generatedSentences: List<SentenceDto>,
    val sourceExamples: List<SentenceDto>,
    val distinctions: List<String>,
    val commonErrors: List<String>,
    val evidence: List<EvidenceDto>,
    val warnings: List<WarningDto>,
    val localState: LocalCardStateDto,
)
data class WordDto(val display: String, val normalized: String, val matchedForm: String?, val stressNote: String?)
data class MeaningDto(val textZh: String, val partOfSpeech: String?)
data class MorphologyDto(
    val gender: String?, val declensionClass: String?, val endingType: String?,
    val pluralStressPattern: String?, val aspect: String?, val conjugationClass: String?,
    val phoneticAlternation: String?, val explanation: String?,
)
data class EtymologyDto(val summary: String?, val confidence: String, val sourceType: String)
data class SentenceDto(val russian: String, val chinese: String?, val evidenceType: String)
data class EvidenceDto(val type: String, val title: String, val field: String?, val excerpt: String?, val sourceDocument: String?)
data class WarningDto(val code: String, val message: String)
data class LocalCardStateDto(val favorite: Boolean, val archiveIds: List<String>, val reviewStatus: String)
data class CardDeckDto(val deckId: String, val title: String, val description: String?, val cardIds: List<String>)
data class GroundingDto(val usedLocalLexicon: Boolean, val usedLocalKnowledge: Boolean, val usedModelKnowledge: Boolean)
data class AgentErrorEnvelopeDto(val error: AgentErrorDto)
data class AgentErrorDto(val code: String, val message: String, val requestId: String, val retryable: Boolean)
