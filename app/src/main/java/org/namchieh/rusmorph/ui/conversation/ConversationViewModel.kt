package org.namchieh.rusmorph.ui.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.namchieh.rusmorph.data.repository.ConversationCorrection
import org.namchieh.rusmorph.data.repository.ConversationEvent
import org.namchieh.rusmorph.data.repository.ConversationHistory
import org.namchieh.rusmorph.data.repository.ConversationRepository
import org.namchieh.rusmorph.data.repository.ConversationRequest
import java.util.UUID

data class ConversationMessage(
    val id: String,
    val role: String,
    val text: String,
    val timestamp: Long,
    val correction: ConversationCorrection? = null,
)

data class ConversationUiState(
    val messages: List<ConversationMessage> = emptyList(),
    val input: String = "",
    val isListening: Boolean = false,
    val isSending: Boolean = false,
    val streamingText: String = "",
    val errorMessage: String? = null,
    val scenario: String = "free",
    val speechNonce: Int = 0,
)

class ConversationViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: ConversationRepository,
) : ViewModel() {
    private val gson = Gson()
    private val sessionId: String = savedStateHandle["sessionId"] ?: UUID.randomUUID().toString().also { savedStateHandle["sessionId"] = it }
    private val restoredMessages: List<ConversationMessage> = runCatching {
        val json: String = savedStateHandle["messages"] ?: "[]"
        gson.fromJson<List<ConversationMessage>>(json, object : TypeToken<List<ConversationMessage>>() {}.type) ?: emptyList()
    }.getOrDefault(emptyList())
    private val _state = MutableStateFlow(ConversationUiState(
        messages = restoredMessages,
        input = savedStateHandle["input"] ?: "",
        scenario = savedStateHandle["scenario"] ?: "free",
    ))
    val state: StateFlow<ConversationUiState> = _state

    fun setInput(value: String) {
        savedStateHandle["input"] = value
        _state.update { it.copy(input = value) }
    }

    fun setScenario(value: String) {
        if (_state.value.isSending || _state.value.messages.any { it.role == "user" }) return
        savedStateHandle["scenario"] = value
        _state.update { it.copy(scenario = value) }
        setMessages(if (value == "cafe") listOf(ConversationMessage(
            UUID.randomUUID().toString(), "assistant", "Здравствуйте! Что будете заказывать?", System.currentTimeMillis(),
        )) else emptyList())
    }

    fun setListening(value: Boolean) { _state.update { it.copy(isListening = value) } }
    fun showError(message: String) { _state.update { it.copy(errorMessage = message, isListening = false) } }
    fun clearError() { _state.update { it.copy(errorMessage = null) } }

    fun send() {
        val message = _state.value.input.trim()
        if (message.isEmpty() || _state.value.isSending) return
        val retry = _state.value.errorMessage != null && _state.value.messages.lastOrNull()?.let { it.role == "user" && it.text == message } == true
        val history = (if (retry) _state.value.messages.dropLast(1) else _state.value.messages).takeLast(10)
            .map { ConversationHistory(it.role, it.text) }
        if (!retry) {
            val userMessage = ConversationMessage(UUID.randomUUID().toString(), "user", message, System.currentTimeMillis())
            setMessages(_state.value.messages + userMessage)
        }
        setInput("")
        _state.update { it.copy(isSending = true, streamingText = "", errorMessage = null) }
        viewModelScope.launch {
            var reply = ""
            var correction: ConversationCorrection? = null
            try {
                repository.converse(ConversationRequest(sessionId, _state.value.scenario, message, history)).collect { event ->
                    when (event) {
                        is ConversationEvent.Token -> {
                            reply += event.text
                            _state.update { it.copy(streamingText = reply) }
                        }
                        is ConversationEvent.Correction -> correction = event.value
                        ConversationEvent.Done -> Unit
                    }
                }
                if (reply.isBlank()) throw IllegalStateException("AI 没有返回回复，请重试")
                setMessages(_state.value.messages + ConversationMessage(UUID.randomUUID().toString(), "assistant", reply, System.currentTimeMillis(), correction))
                _state.update { it.copy(isSending = false, streamingText = "", speechNonce = it.speechNonce + 1) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(isSending = false, streamingText = "", errorMessage = e.message ?: "网络连接失败，请重试", input = message) }
                savedStateHandle["input"] = message
            }
        }
    }

    private fun setMessages(messages: List<ConversationMessage>) {
        savedStateHandle["messages"] = gson.toJson(messages)
        _state.update { it.copy(messages = messages) }
    }
}
