package org.namchieh.rusmorph.ui.grammar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.namchieh.rusmorph.data.local.GrammarMasteryEntity
import org.namchieh.rusmorph.data.local.GrammarPointEntity
import org.namchieh.rusmorph.data.local.QuestionEntity
import org.namchieh.rusmorph.data.repository.AgentRepository
import org.namchieh.rusmorph.data.repository.ExamPracticeRepository
import org.namchieh.rusmorph.data.repository.GrammarRepository
import org.namchieh.rusmorph.domain.grammar.GrammarPointOverview
import org.namchieh.rusmorph.domain.grammar.QuestionEntryContextType
import org.namchieh.rusmorph.domain.grammar.QuestionRunnerMode
import org.namchieh.rusmorph.domain.grammar.VariantGenerationOutcome

class GrammarHomeViewModel(repository: GrammarRepository) : ViewModel() {
    val points: StateFlow<List<GrammarPointOverview>> = repository.observeGrammarPoints()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

data class GrammarDetailState(
    val point: GrammarPointEntity? = null,
    val questions: List<QuestionEntity> = emptyList(),
    val mastery: GrammarMasteryEntity? = null,
)

class GrammarDetailViewModel(repository: GrammarRepository, pointId: String) : ViewModel() {
    val state: StateFlow<GrammarDetailState> = combine(
        repository.observeGrammarPoint(pointId),
        repository.observeQuestionsForGrammarPoint(pointId),
        repository.observeGrammarMastery(pointId),
    ) { point, questions, mastery -> GrammarDetailState(point, questions, mastery) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GrammarDetailState())
}

class Tem4PracticeViewModel(repository: ExamPracticeRepository, grammarRepository: GrammarRepository) : ViewModel() {
    val questions: StateFlow<List<QuestionEntity>> = repository.observeTem4Questions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val wrongQuestions: StateFlow<List<QuestionEntity>> = repository.observeWrongQuestions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val grammarPoints: StateFlow<List<GrammarPointOverview>> = grammarRepository.observeGrammarPoints()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

data class QuestionRunnerUiState(
    val question: QuestionEntity? = null,
    val grammarPoints: List<GrammarPointEntity> = emptyList(),
    val selectedAnswer: String? = null,
    val submitted: Boolean = false,
    val correct: Boolean? = null,
    val showExplanation: Boolean = false,
    val peekPointId: String? = null,
    val generatingVariant: Boolean = false,
    val variantError: String? = null,
    val generatedQuestionId: String? = null,
)

class QuestionRunnerViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val examRepository: ExamPracticeRepository,
    private val grammarRepository: GrammarRepository,
    private val agentRepository: AgentRepository,
    val questionId: String,
    val mode: QuestionRunnerMode,
    val entryContext: QuestionEntryContextType,
    val targetGrammarPointId: String?,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        QuestionRunnerUiState(
            selectedAnswer = savedStateHandle[KEY_SELECTED],
            submitted = savedStateHandle[KEY_SUBMITTED] ?: false,
            correct = savedStateHandle[KEY_CORRECT],
            showExplanation = savedStateHandle[KEY_EXPLANATION] ?: false,
            peekPointId = savedStateHandle[KEY_PEEK],
        ),
    )
    val state: StateFlow<QuestionRunnerUiState> = mutableState

    init {
        viewModelScope.launch {
            val question = examRepository.getQuestion(questionId)
            val points = grammarRepository.getGrammarPointsForQuestion(questionId)
            mutableState.value = mutableState.value.copy(question = question, grammarPoints = points)
        }
    }

    fun selectAnswer(answer: String) {
        if (mutableState.value.submitted) return
        savedStateHandle[KEY_SELECTED] = answer
        mutableState.value = mutableState.value.copy(selectedAnswer = answer)
    }

    fun submit() {
        val selected = mutableState.value.selectedAnswer ?: return
        if (mutableState.value.submitted) return
        viewModelScope.launch {
            val result = examRepository.submitAttempt(questionId, selected, mode)
            savedStateHandle[KEY_SUBMITTED] = true
            savedStateHandle[KEY_CORRECT] = result.correct
            savedStateHandle[KEY_EXPLANATION] = mode != QuestionRunnerMode.SIMULATION
            mutableState.value = mutableState.value.copy(
                submitted = true,
                correct = result.correct,
                showExplanation = mode != QuestionRunnerMode.SIMULATION,
            )
        }
    }

    fun openGrammarPeek(pointId: String) {
        if (!canRevealLearningTools()) return
        savedStateHandle[KEY_PEEK] = pointId
        mutableState.value = mutableState.value.copy(peekPointId = pointId)
    }

    fun closeGrammarPeek() {
        savedStateHandle[KEY_PEEK] = null
        mutableState.value = mutableState.value.copy(peekPointId = null)
    }

    fun generateVariant(pointId: String) {
        if (!canRevealLearningTools() || mutableState.value.generatingVariant) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(generatingVariant = true, variantError = null)
            mutableState.value = when (val result = examRepository.generateAndPersistVariant(questionId, pointId, agentRepository)) {
                is VariantGenerationOutcome.Success -> mutableState.value.copy(
                    generatingVariant = false,
                    generatedQuestionId = result.question.questionId,
                )
                is VariantGenerationOutcome.Failure -> mutableState.value.copy(
                    generatingVariant = false,
                    variantError = when (result.error) {
                        org.namchieh.rusmorph.agent.AgentError.NoNetwork,
                        org.namchieh.rusmorph.agent.AgentError.ServiceNotConfigured -> "AI 变式当前不可用，真题与语法内容仍可离线使用。"
                        else -> "AI 返回未通过验证，请重试。"
                    },
                )
            }
        }
    }

    fun consumeGeneratedQuestion() {
        mutableState.value = mutableState.value.copy(generatedQuestionId = null)
    }

    private fun canRevealLearningTools(): Boolean =
        mutableState.value.submitted && mode != QuestionRunnerMode.SIMULATION

    private companion object {
        const val KEY_SELECTED = "question_selected_answer"
        const val KEY_SUBMITTED = "question_submitted"
        const val KEY_CORRECT = "question_correct"
        const val KEY_EXPLANATION = "question_show_explanation"
        const val KEY_PEEK = "question_peek_point"
    }
}
