package org.namchieh.rusmorph.agent

enum class AgentOutputMode { WORD_CARD, CARD_DECK, COMPARISON, CLARIFICATION, TEXT }
enum class AgentIntent { LOOKUP, LESSON_DECK, PART_OF_SPEECH_DECK, EXAMPLE, ETYMOLOGY, MORPHOLOGY, COMPARE, SAVE_CARD, SAVE_DECK, ARCHIVE_CARD, DELETE_ARCHIVE, SIMILAR, GENERAL, UNKNOWN }
enum class EvidenceType { LEXICON_FIELD, LOCAL_KNOWLEDGE, MODEL_KNOWLEDGE, MODEL_GENERATED_EXAMPLE, SOURCE_EXAMPLE }

data class LexiconQuery(
    val text: String? = null,
    val lesson: Int? = null,
    val lessons: List<Int> = emptyList(),
    val partOfSpeech: String? = null,
    val morphologyFilters: MorphologyFilters = MorphologyFilters(),
    val maxResults: Int = 50,
    val allowFuzzy: Boolean = true,
)

data class MorphologyFilters(
    val genders: List<String> = emptyList(),
    val declensionClasses: List<String> = emptyList(),
    val endingTypes: List<String> = emptyList(),
    val aspects: List<String> = emptyList(),
    val conjugationClasses: List<String> = emptyList(),
    val phoneticAlternations: List<String> = emptyList(),
    val hasPhoneticAlternation: Boolean? = null,
    val hasPluralStressPattern: Boolean? = null,
) {
    val isEmpty: Boolean
        get() = genders.isEmpty() &&
            declensionClasses.isEmpty() &&
            endingTypes.isEmpty() &&
            aspects.isEmpty() &&
            conjugationClasses.isEmpty() &&
            phoneticAlternations.isEmpty() &&
            hasPhoneticAlternation == null &&
            hasPluralStressPattern == null
}

data class AgentCommandPlan(
    val intent: AgentIntent,
    val outputMode: AgentOutputMode,
    val queries: List<LexiconQuery> = emptyList(),
    val selectedCardIndexes: List<Int> = emptyList(),
    val requiresLocalRetrieval: Boolean = false,
    val requiresConfirmation: Boolean = false,
    val clarification: String? = null,
)

data class CardEvidence(
    val type: EvidenceType,
    val title: String,
    val field: String? = null,
    val excerpt: String? = null,
    val sourceDocument: String? = null,
)
data class AgentWarning(val code: String, val message: String)
data class GeneratedSentence(val russian: String, val chinese: String? = null, val evidenceType: EvidenceType)
data class WordCard(
    val id: String,
    val entryId: String,
    val word: String,
    val normalizedWord: String,
    val matchedForm: String? = null,
    val meanings: List<String> = emptyList(),
    val partsOfSpeech: List<String> = emptyList(),
    val stress: String? = null,
    val morphology: String? = null,
    val etymology: String? = null,
    val generatedSentences: List<GeneratedSentence> = emptyList(),
    val sourceExamples: List<GeneratedSentence> = emptyList(),
    val distinctions: List<String> = emptyList(),
    val commonErrors: List<String> = emptyList(),
    val evidence: List<CardEvidence> = emptyList(),
    val warnings: List<AgentWarning> = emptyList(),
    val favorite: Boolean = false,
    val archiveIds: List<String> = emptyList(),
    val reviewStatus: String? = null,
)
data class CardDeck(val id: String, val title: String, val cards: List<WordCard>, val warnings: List<AgentWarning> = emptyList())
data class ComparisonMatrix(val entryIds: List<String>, val dimensions: Map<String, List<String?>>, val evidence: List<CardEvidence> = emptyList())

enum class LocalActionType { FAVORITE, ARCHIVE, SAVE_DECK, DELETE_ARCHIVE }
data class LocalAction(
    val type: LocalActionType,
    val cardId: String? = null,
    val cardIndex: Int? = null,
    val archiveName: String? = null,
    val requiresConfirmation: Boolean = false,
)

data class AgentSessionContext(
    val currentEntryId: String? = null,
    val currentDeckId: String? = null,
    val visibleCardIds: List<String> = emptyList(),
    val lastSelectedIndex: Int? = null,
    val customQuestion: String = "",
    val localSearchMiss: Boolean = false,
    val deckLimit: Int = 50,
)

data class RouteCommandRequest(val command: String, val session: AgentSessionContext)
data class RouteCommandResponse(val plan: AgentCommandPlan)
data class LocalLexiconEntry(
    val entryId: String,
    val word: String,
    val normalizedWord: String,
    val matchedForm: String? = null,
    val meanings: List<String> = emptyList(),
    val partsOfSpeech: List<String> = emptyList(),
    val lesson: Int? = null,
    val gender: String? = null,
    val declensionClass: String? = null,
    val endingType: String? = null,
    val pluralStressPattern: String? = null,
    val aspect: String? = null,
    val conjugationClass: String? = null,
    val phoneticAlternation: String? = null,
    val annotations: Map<String, List<String>> = emptyMap(),
    val sourceExamples: List<SourceSentence> = emptyList(),
    val sources: List<String> = emptyList(),
)
data class SourceSentence(val russian: String, val chinese: String? = null)
data class OrchestrateRequest(
    val command: String,
    val session: AgentSessionContext,
    val plan: AgentCommandPlan,
    val localEntries: List<LocalLexiconEntry>,
)
data class MultiAgentResponse(
    val phase: String,
    val plan: AgentCommandPlan,
    val card: WordCard? = null,
    val deck: CardDeck? = null,
    val comparison: ComparisonMatrix? = null,
    val localAction: LocalAction? = null,
    val clarification: String? = null,
    val warnings: List<AgentWarning> = emptyList(),
    val plainAnswer: String? = null,
    val thinkingSummary: String? = null,
)

sealed interface MultiAgentResult {
    data class Routed(val plan: AgentCommandPlan) : MultiAgentResult
    data class Completed(val response: MultiAgentResponse) : MultiAgentResult
    data class Failure(val error: AgentError) : MultiAgentResult
}
