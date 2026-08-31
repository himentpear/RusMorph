package org.namchieh.rusmorph.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.namchieh.rusmorph.agent.AgentAvailability
import org.namchieh.rusmorph.agent.AgentContextBuilder
import org.namchieh.rusmorph.agent.AgentError
import org.namchieh.rusmorph.agent.AgentQuestionTemplateFactory
import org.namchieh.rusmorph.agent.AgentQuestionType
import org.namchieh.rusmorph.agent.AgentRequest
import org.namchieh.rusmorph.agent.AgentResponse
import org.namchieh.rusmorph.agent.AgentResult
import org.namchieh.rusmorph.data.repository.AgentRepository
import org.namchieh.rusmorph.data.repository.KnowledgeRetrievalResult
import org.namchieh.rusmorph.data.repository.KnowledgeRetriever

data class AgentPreparedData(
    val detail: WordDetailUiState,
    val retrieval: KnowledgeRetrievalResult,
    val questionType: AgentQuestionType,
    val defaultQuestion: String,
    val userInput: String,
    val availability: AgentAvailability,
) {
    val canSubmit: Boolean get() = availability == AgentAvailability.AVAILABLE &&
        (questionType != AgentQuestionType.CUSTOM || userInput.isNotBlank())
}

sealed interface AgentUiState {
    data object Preparing : AgentUiState
    data class Ready(val data: AgentPreparedData) : AgentUiState
    data class Sending(val data: AgentPreparedData) : AgentUiState
    data class Success(val data: AgentPreparedData, val response: AgentResponse) : AgentUiState
    data class Error(val data: AgentPreparedData, val error: AgentError) : AgentUiState
    data object NotFound : AgentUiState
}

class AgentViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val retriever: KnowledgeRetriever,
    private val contextBuilder: AgentContextBuilder,
    private val repository: AgentRepository,
) : ViewModel() {
    private val entryId: String = checkNotNull(savedStateHandle["entryId"])
    private val questionType = runCatching {
        AgentQuestionType.valueOf(savedStateHandle.get<String>("questionType") ?: "CUSTOM")
    }.getOrDefault(AgentQuestionType.CUSTOM)
    private val conversationId = savedStateHandle.get<String>("conversationId")
        ?: UUID.randomUUID().toString().also { savedStateHandle["conversationId"] = it }
    private val _uiState = MutableStateFlow<AgentUiState>(AgentUiState.Preparing)
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()
    private var requestJob: Job? = null
    private var lastRequest: AgentRequest? = null

    init {
        viewModelScope.launch {
            val retrieval = retriever.retrieve(entryId, questionType, savedStateHandle["agent_input"])
            val detail = retrieval.entry
            _uiState.value = if (detail == null) AgentUiState.NotFound else AgentUiState.Ready(
                AgentPreparedData(
                    detail, retrieval, questionType,
                    AgentQuestionTemplateFactory.create(questionType, detail.displayForm),
                    savedStateHandle["agent_input"] ?: "",
                    repository.availability,
                ),
            )
        }
    }

    fun updateInput(value: String) {
        val limited = value.take(AgentContextBuilder.MAX_QUESTION_LENGTH)
        savedStateHandle["agent_input"] = limited
        _uiState.value = when (val state = _uiState.value) {
            is AgentUiState.Ready -> AgentUiState.Ready(state.data.copy(userInput = limited))
            is AgentUiState.Success -> state.copy(data = state.data.copy(userInput = limited))
            is AgentUiState.Error -> state.copy(data = state.data.copy(userInput = limited))
            else -> _uiState.value
        }
    }

    fun submit() {
        val data = when (val state = _uiState.value) {
            is AgentUiState.Ready -> state.data
            is AgentUiState.Success -> state.data
            is AgentUiState.Error -> state.data
            else -> return
        }
        if (!data.canSubmit) return
        val snapshot = contextBuilder.build(
            data.detail, data.retrieval, data.questionType, data.userInput,
            conversationId = conversationId,
        )
        lastRequest = snapshot
        send(snapshot, data)
    }

    fun retry() {
        val request = lastRequest ?: return
        val data = (uiState.value as? AgentUiState.Error)?.data ?: return
        send(request, data)
    }

    fun cancel() {
        requestJob?.cancel()
        val data = (uiState.value as? AgentUiState.Sending)?.data ?: return
        _uiState.value = AgentUiState.Ready(data)
    }

    private fun send(request: AgentRequest, data: AgentPreparedData) {
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            _uiState.value = AgentUiState.Sending(data)
            try {
                _uiState.value = when (val result = repository.ask(request)) {
                    is AgentResult.Success -> AgentUiState.Success(data, result.response)
                    is AgentResult.Failure -> AgentUiState.Error(data, result.error)
                }
            } catch (_: CancellationException) {
                // Cancellation is user control, not an error state.
            }
        }
    }
}
