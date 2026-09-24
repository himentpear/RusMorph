package org.namchieh.rusmorph.data.repository

import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.namchieh.rusmorph.data.local.GrammarExamDao
import org.namchieh.rusmorph.data.local.GrammarMasteryEntity
import org.namchieh.rusmorph.data.local.QuestionAttemptEntity
import org.namchieh.rusmorph.data.local.QuestionEntity
import org.namchieh.rusmorph.data.local.QuestionLineageEntity
import org.namchieh.rusmorph.data.local.GrammarQuestionCrossRefEntity
import org.namchieh.rusmorph.data.local.RusMorphDatabase
import org.namchieh.rusmorph.data.remote.VariantGrammarContextDto
import org.namchieh.rusmorph.data.remote.VariantQuestionRequestDto
import org.namchieh.rusmorph.data.remote.VariantReferenceQuestionDto
import org.namchieh.rusmorph.domain.grammar.QuestionRunnerMode
import org.namchieh.rusmorph.domain.grammar.QuestionSubmission
import org.namchieh.rusmorph.domain.grammar.VariantGenerationOutcome

class ExamPracticeRepository(
    private val database: RusMorphDatabase,
    private val dao: GrammarExamDao = database.grammarExamDao(),
) {
    fun observeTem4Questions(): Flow<List<QuestionEntity>> = dao.observeTem4Questions()
    fun observeQuestion(questionId: String): Flow<QuestionEntity?> = dao.observeQuestion(questionId)
    fun observeAttempts(questionId: String): Flow<List<QuestionAttemptEntity>> = dao.observeAttempts(questionId)
    fun observeWrongQuestions(): Flow<List<QuestionEntity>> = dao.observeWrongQuestions()

    suspend fun getTem4Questions(): List<QuestionEntity> = dao.getTem4Questions()
    suspend fun getQuestion(questionId: String): QuestionEntity? = dao.getQuestion(questionId)
    suspend fun getAttempts(questionId: String): List<QuestionAttemptEntity> = dao.getAttempts(questionId)

    suspend fun getNextQuestion(currentQuestionId: String?, targetPointId: String? = null): QuestionEntity? {
        val questions = if (targetPointId == null) dao.getTem4Questions() else dao.getQuestionsForGrammarPoint(targetPointId)
        if (questions.isEmpty()) return null
        val currentIndex = questions.indexOfFirst { it.questionId == currentQuestionId }
        return questions[(currentIndex + 1).mod(questions.size)]
    }

    suspend fun submitAttempt(
        questionId: String,
        selectedAnswer: String,
        mode: QuestionRunnerMode,
        durationMs: Long? = null,
        now: Long = System.currentTimeMillis(),
    ): QuestionSubmission {
        val normalizedAnswer = selectedAnswer.trim().uppercase()
        require(normalizedAnswer in setOf("A", "B", "C", "D")) { "Answer must be A, B, C or D" }
        return database.withTransaction {
            val question = requireNotNull(dao.getQuestion(questionId)) { "Question not found: $questionId" }
            val correct = normalizedAnswer == question.answer.uppercase()
            dao.insertAttempt(
                QuestionAttemptEntity(
                    attemptId = UUID.randomUUID().toString(),
                    questionId = questionId,
                    selectedAnswer = normalizedAnswer,
                    correct = correct,
                    mode = mode.name,
                    durationMs = durationMs,
                    createdAt = now,
                ),
            )
            dao.getGrammarPointsForQuestion(questionId).forEach { point ->
                val evidence = dao.getAttemptEvidenceForGrammarPoint(point.pointId)
                val real = evidence.filter { it.sourceType == "TEM4_REAL" }
                val ai = evidence.filter { it.sourceType != "TEM4_REAL" }
                val realAccuracy = real.takeIf { it.isNotEmpty() }?.let { rows -> rows.count { it.correct }.toDouble() / rows.size }
                val aiAccuracy = ai.takeIf { it.isNotEmpty() }?.let { rows -> rows.count { it.correct }.toDouble() / rows.size }
                val mastery = when {
                    realAccuracy != null && aiAccuracy != null -> realAccuracy * 0.7 + aiAccuracy * 0.3
                    realAccuracy != null -> realAccuracy
                    aiAccuracy != null -> aiAccuracy
                    else -> 0.0
                }
                dao.upsertMastery(
                    GrammarMasteryEntity(
                        pointId = point.pointId,
                        mastery = mastery.coerceIn(0.0, 1.0),
                        realQuestionAttempts = real.size,
                        realQuestionCorrect = real.count { it.correct },
                        aiQuestionAttempts = ai.size,
                        aiQuestionCorrect = ai.count { it.correct },
                        lastReviewedAt = evidence.maxOfOrNull { it.createdAt },
                    ),
                )
            }
            QuestionSubmission(correct, question.answer.uppercase())
        }
    }

    suspend fun generateAndPersistVariant(
        sourceQuestionId: String,
        targetPointId: String,
        agentRepository: AgentRepository,
        now: Long = System.currentTimeMillis(),
    ): VariantGenerationOutcome {
        val source = dao.getQuestion(sourceQuestionId)
            ?: return VariantGenerationOutcome.Failure(org.namchieh.rusmorph.agent.AgentError.InvalidResponse)
        val point = dao.getGrammarPoint(targetPointId)
            ?: return VariantGenerationOutcome.Failure(org.namchieh.rusmorph.agent.AgentError.InvalidResponse)
        val request = VariantQuestionRequestDto(
            conversationId = UUID.randomUUID().toString(),
            sourceQuestionId = sourceQuestionId,
            targetGrammarPointId = targetPointId,
            grammar = VariantGrammarContextDto(point.titleZh, point.titleRu, point.explanation),
            referenceQuestion = VariantReferenceQuestionDto(
                year = source.examYearLabel ?: source.examYear?.toString().orEmpty(),
                stem = source.stem,
                options = mapOf("A" to source.optionA, "B" to source.optionB, "C" to source.optionC, "D" to source.optionD),
                correctAnswer = source.answer,
                analysis = source.analysis.orEmpty(),
            ),
        )
        return when (val generated = agentRepository.generateVariantQuestion(request)) {
            is VariantQuestionResult.Failure -> VariantGenerationOutcome.Failure(generated.error)
            is VariantQuestionResult.Success -> {
                val generationId = UUID.randomUUID().toString()
                val question = QuestionEntity(
                    questionId = "AI_VARIANT_$generationId",
                    sourceQuestionId = null,
                    sourceType = "AI_VARIANT",
                    examYear = null,
                    examYearLabel = null,
                    stem = generated.question.stem,
                    optionA = generated.question.options.getValue("A"),
                    optionB = generated.question.options.getValue("B"),
                    optionC = generated.question.options.getValue("C"),
                    optionD = generated.question.options.getValue("D"),
                    answer = generated.question.answer,
                    analysis = generated.question.analysis,
                    difficulty = source.difficulty,
                    createdAt = now,
                )
                database.withTransaction {
                    dao.upsertQuestion(question)
                    dao.upsertGrammarQuestionLink(
                        GrammarQuestionCrossRefEntity(
                            questionId = question.questionId,
                            pointId = targetPointId,
                            role = "PRIMARY",
                            weight = 1.0,
                            confidence = 1.0,
                            relationSource = "AI_SUGGESTED",
                            verified = false,
                        ),
                    )
                    dao.upsertLineage(
                        QuestionLineageEntity(
                            questionId = question.questionId,
                            derivedFromQuestionId = sourceQuestionId,
                            targetPointId = targetPointId,
                            generationId = generationId,
                            modelMetadata = "worker:/v1/grammar-variant",
                        ),
                    )
                }
                VariantGenerationOutcome.Success(question)
            }
        }
    }
}
