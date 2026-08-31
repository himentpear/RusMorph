package org.namchieh.rusmorph.agent

enum class AgentQuestionType { ETYMOLOGY, DERIVATION, MORPHOLOGY, CUSTOM }

enum class AgentAvailability { AVAILABLE, NOT_CONFIGURED, OFFLINE, TEMPORARILY_UNAVAILABLE }

data class AgentRequest(
    val requestId: String,
    val conversationId: String,
    val entryId: String,
    val word: String,
    val normalizedWord: String,
    val questionType: AgentQuestionType,
    val question: String,
    val entryContext: AgentEntryContext,
    val knowledgeContext: List<AgentKnowledgeContext>,
    val client: AgentClientMetadata,
    val learningContext: AgentLearningContext? = null,
)

data class AgentLearningContext(
    val type: String,
    val sourceId: String? = null,
    val courseId: String? = null,
    val lessonId: String? = null,
    val label: String? = null,
    val excerpt: String? = null,
)

data class AgentEntryContext(
    val meaningZh: String? = null,
    val partsOfSpeech: List<String> = emptyList(),
    val lesson: Int? = null,
    val gender: String? = null,
    val declensionClass: String? = null,
    val endingType: String? = null,
    val pluralStressPattern: String? = null,
    val aspect: String? = null,
    val conjugationClass: String? = null,
    val phoneticAlternation: String? = null,
    val searchForms: List<String> = emptyList(),
    val source: AgentSource? = null,
)

data class AgentSource(val sheet: String, val row: Int)
data class AgentKnowledgeContext(
    val chunkId: String,
    val title: String,
    val category: String,
    val content: String,
    val sourceDocument: String,
    val sectionPath: List<String> = emptyList(),
)
data class AgentClientMetadata(val appVersion: String, val locale: String = "zh-CN", val platform: String = "android")

data class AgentResponse(
    val requestId: String,
    val conversationId: String,
    val answer: String,
    val answerSections: List<AgentAnswerSection> = emptyList(),
    val evidence: List<AgentEvidence> = emptyList(),
    val warnings: List<String> = emptyList(),
    val grounding: AgentGrounding? = null,
)
data class AgentAnswerSection(val title: String, val content: String)
data class AgentEvidence(
    val type: String,
    val title: String,
    val field: String? = null,
    val excerpt: String? = null,
    val sourceDocument: String? = null,
    val sectionPath: List<String> = emptyList(),
)
data class AgentGrounding(
    val hasLexiconEvidence: Boolean = false,
    val hasKnowledgeEvidence: Boolean = false,
    val usedGeneralModelKnowledge: Boolean = false,
)

sealed interface AgentError {
    data object NoNetwork : AgentError
    data object Timeout : AgentError
    data object RateLimited : AgentError
    data object UnauthorizedProxy : AgentError
    data object ServiceNotConfigured : AgentError
    data object AuthenticationFailed : AgentError
    data object BalanceInsufficient : AgentError
    data object ProviderBusy : AgentError
    data object ServerError : AgentError
    data object InvalidResponse : AgentError
    data object Cancelled : AgentError
    data object Unknown : AgentError
}

sealed interface AgentResult {
    data class Success(val response: AgentResponse) : AgentResult
    data class Failure(val error: AgentError) : AgentResult
}
