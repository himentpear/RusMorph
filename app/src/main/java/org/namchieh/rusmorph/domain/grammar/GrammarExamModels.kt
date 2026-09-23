package org.namchieh.rusmorph.domain.grammar

import org.namchieh.rusmorph.data.local.GrammarPointEntity
import org.namchieh.rusmorph.data.local.QuestionEntity
import org.namchieh.rusmorph.agent.AgentError

enum class QuestionSourceType { TEM4_REAL, AI_VARIANT, AI_FREE }
enum class QuestionRunnerMode { LEARNING, PRACTICE, SIMULATION, AI_VARIANT }
enum class QuestionEntryContextType { GRAMMAR, TEM4, WRONG_QUESTIONS, RANDOM, AI_VARIANT }

data class QuestionRunnerContext(
    val entryContext: QuestionEntryContextType,
    val targetGrammarPointId: String? = null,
)

data class GrammarPointOverview(
    val point: GrammarPointEntity,
    val mastery: Double,
    val realQuestionCount: Int,
    val completedQuestionCount: Int,
)

data class GrammarQuestion(
    val question: QuestionEntity,
    val grammarPoints: List<GrammarPointEntity>,
)

data class QuestionSubmission(
    val correct: Boolean,
    val correctAnswer: String,
)

sealed interface VariantGenerationOutcome {
    data class Success(val question: QuestionEntity) : VariantGenerationOutcome
    data class Failure(val error: AgentError) : VariantGenerationOutcome
}
