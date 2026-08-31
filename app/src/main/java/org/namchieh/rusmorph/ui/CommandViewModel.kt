package org.namchieh.rusmorph.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.namchieh.rusmorph.agent.*
import org.namchieh.rusmorph.data.repository.ArchiveDeletionResult
import org.namchieh.rusmorph.data.repository.LocalLibraryRepository
import org.namchieh.rusmorph.data.settings.AppSettings

sealed interface CommandUiState {
    data object Idle : CommandUiState
    data object Sending : CommandUiState
    data class Content(val response: MultiAgentResponse, val localMessage: String? = null) : CommandUiState
    data class Error(val error: AgentError) : CommandUiState
}

class CommandViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val coordinator: MultiAgentCoordinator,
    private val library: LocalLibraryRepository,
    private val appSettings: AppSettings,
) : ViewModel() {
    private val _state = MutableStateFlow<CommandUiState>(CommandUiState.Idle)
    val state: StateFlow<CommandUiState> = _state.asStateFlow()
    val command = savedStateHandle.getStateFlow("agent_command", "")
    private var job: Job? = null
    private var currentCards: List<WordCard> = emptyList()
    private var currentDeck: CardDeck? = null
    private var lastResponse: MultiAgentResponse? = null
    private var pendingArchiveDeletion: String? = null

    init {
        savedStateHandle.get<String>("command")?.takeIf { it.isNotBlank() }?.let {
            savedStateHandle["agent_command"] = it.take(1_000)
            if (savedStateHandle.get<Boolean>("command_auto_submitted") != true) {
                savedStateHandle["command_auto_submitted"] = true
                submit()
            }
        }
    }

    fun setCommand(value: String) { savedStateHandle["agent_command"] = value.take(1_000) }

    fun submit() {
        val text = command.value.trim()
        if (text.isEmpty() || job?.isActive == true) return
        job = viewModelScope.launch {
            _state.value = CommandUiState.Sending
            try {
                when (val result = coordinator.execute(text, session())) {
                    is MultiAgentResult.Completed -> handleResponse(result.response)
                    is MultiAgentResult.Failure -> _state.value = CommandUiState.Error(result.error)
                    is MultiAgentResult.Routed -> _state.value = CommandUiState.Error(AgentError.InvalidResponse)
                }
            } catch (_: CancellationException) { _state.value = CommandUiState.Idle }
        }
    }

    fun cancel() { job?.cancel() }

    fun saveCard(card: WordCard) = viewModelScope.launch {
        library.saveCard(card)
        _state.value = CommandUiState.Content(currentResponse(), "已收藏到本地")
    }

    fun saveDeck(deck: CardDeck) = viewModelScope.launch {
        library.saveDeck(deck, command.value)
        _state.value = CommandUiState.Content(currentResponse(), "卡组已保存到本地")
    }

    fun confirmArchiveDeletion() = viewModelScope.launch {
        val name = pendingArchiveDeletion ?: return@launch
        library.deleteArchive(name, confirmed = true)
        pendingArchiveDeletion = null
        _state.value = CommandUiState.Content(currentResponse(), "归档已删除")
    }

    private suspend fun handleResponse(response: MultiAgentResponse) {
        response.card?.let { currentCards = listOf(it); currentDeck = null }
        response.deck?.let { currentDeck = it; currentCards = it.cards }
        savedStateHandle["current_entry_id"] = currentCards.firstOrNull()?.entryId
        savedStateHandle["current_deck_id"] = currentDeck?.id
        savedStateHandle["visible_card_ids"] = ArrayList(currentCards.map { it.id })
        val action = response.localAction
        val message = when (action?.type) {
            LocalActionType.FAVORITE -> selectedCard(action)?.let { library.saveCard(it); "已收藏到本地" } ?: "缺少可收藏的当前卡片"
            LocalActionType.ARCHIVE -> selectedCard(action)?.let { card ->
                val name = action.archiveName
                if (name.isNullOrBlank()) "请说明归档名称" else { library.addCardToArchive(card, name); "已加入归档“$name”" }
            } ?: "缺少可归档的当前卡片"
            LocalActionType.SAVE_DECK -> currentDeck?.let { library.saveDeck(it, command.value); "卡组已保存到本地" } ?: "当前没有卡组"
            LocalActionType.DELETE_ARCHIVE -> {
                val name = action.archiveName
                if (name.isNullOrBlank()) "请说明要删除的归档" else when (val deletion = library.deleteArchive(name, false)) {
                    is ArchiveDeletionResult.ConfirmationRequired -> { pendingArchiveDeletion = name; "删除归档需要确认，其中有 ${deletion.cardCount} 张卡片" }
                    ArchiveDeletionResult.NotFound -> "没有找到该归档"
                    ArchiveDeletionResult.Deleted -> "归档已删除"
                }
            }
            null -> null
        }
        if (response.card != null || response.deck != null || response.clarification != null || lastResponse == null) {
            lastResponse = response
        }
        _state.value = CommandUiState.Content(lastResponse ?: response, message)
    }

    private fun selectedCard(action: LocalAction): WordCard? =
        action.cardId?.let { id -> currentCards.firstOrNull { it.id == id } }
            ?: action.cardIndex?.let(currentCards::getOrNull)
            ?: currentCards.firstOrNull()

    private fun session() = AgentSessionContext(
        currentEntryId = savedStateHandle["current_entry_id"],
        currentDeckId = savedStateHandle["current_deck_id"],
        visibleCardIds = savedStateHandle.get<ArrayList<String>>("visible_card_ids")?.toList().orEmpty(),
        lastSelectedIndex = savedStateHandle["last_selected_index"],
        customQuestion = command.value,
        deckLimit = appSettings.deckLimit.value,
    )

    private fun currentResponse(): MultiAgentResponse =
        lastResponse ?: MultiAgentResponse("COMPLETE", AgentCommandPlan(AgentIntent.UNKNOWN, AgentOutputMode.TEXT))
}
