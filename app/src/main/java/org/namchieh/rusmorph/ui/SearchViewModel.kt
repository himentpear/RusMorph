package org.namchieh.rusmorph.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import org.namchieh.rusmorph.data.local.DataInitializer
import org.namchieh.rusmorph.data.local.InitializationState
import org.namchieh.rusmorph.data.local.LexiconEntryWithDetails
import org.namchieh.rusmorph.data.repository.SearchRepository
import org.namchieh.rusmorph.BuildConfig
import java.io.File
import org.namchieh.rusmorph.data.repository.SpeechRepository

private const val QUERY_KEY = "search_query"
private const val PART_OF_SPEECH_KEY = "search_part_of_speech"
private const val LESSON_KEY = "search_lesson"

data class SearchControlsState(
    val query: String = "",
    val partOfSpeech: String? = null,
    val lesson: Int? = null,
)

enum class VoiceInputStatus { Idle, Recording, Processing, Success, LowConfidence, Error }

data class VoiceInputState(
    val status: VoiceInputStatus = VoiceInputStatus.Idle,
    val candidate: String? = null,
    val confidence: Double? = null,
    val message: String? = null,
)

data class SearchUiState(
    val controls: SearchControlsState = SearchControlsState(),
    val results: List<LexiconEntryWithDetails> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearchError: Boolean = false,
    val initializationState: InitializationState = InitializationState.NotStarted,
    val partOfSpeechOptions: List<String> = emptyList(),
    val lessonOptions: List<Int> = emptyList(),
    val recentEntries: List<LexiconEntryWithDetails> = emptyList(),
    val browseEntries: List<LexiconEntryWithDetails> = emptyList(),
    val databaseEntryCount: Int = 0,
    val browseInconsistent: Boolean = false,
    val redirectedFrom: String? = null,
    val redirectedTo: String? = null,
    val isFuzzyMatch: Boolean = false,
) {
    val isEmptyQuery: Boolean get() = controls.query.isBlank()
    val hasActiveFilters: Boolean get() = controls.partOfSpeech != null || controls.lesson != null
}

data class WordDetailUiState(
    val id: String,
    val lesson: Int?,
    val sequence: Int?,
    val displayForm: String,
    val normalizedLemma: String = "",
    val searchForms: List<String> = emptyList(),
    val chineseMeaning: String?,
    val partsOfSpeech: List<String>,
    val gender: String?,
    val declensionClass: String?,
    val endingType: String?,
    val pluralStressPattern: String?,
    val aspect: String?,
    val conjugationClass: String?,
    val phoneticAlternation: String?,
    val annotations: List<Annotation> = emptyList(),
    val relatedKnowledge: List<KnowledgeExplanation>,
    val sources: List<Source>,
) {
    val inflection: InflectionData? get() = parseInflectionJson(annotations.firstOrNull { it.fieldName == "inflection_data" }?.value)

    data class KnowledgeExplanation(
        val id: String,
        val title: String,
        val category: String,
        val content: String,
        val sourceDocument: String,
        val sectionPath: List<String> = emptyList(),
    )

    data class Source(val workbook: String, val sheet: String, val row: Int)
    data class Annotation(val fieldName: String, val value: String)
}

sealed interface InflectionData {
    data class Noun(
        val partner: String?,
        val singular: Map<String, String>,
        val plural: Map<String, String>,
    ) : InflectionData

    data class Verb(
        val aspect: String?,
        val partner: String?,
        val presentOrFuture: Map<String, String>,
        val past: Map<String, String>,
        val imperative: Map<String, String>,
    ) : InflectionData

    data class Adjective(
        val comparative: String?,
        val superlative: String?,
        val shortForms: Map<String, String>,
        val cases: Map<String, Map<String, String>>,
    ) : InflectionData
}

fun parseInflectionJson(raw: String?): InflectionData? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val obj = org.json.JSONObject(raw)
        when (obj.optString("type")) {
            "noun" -> {
                val sgObj = obj.optJSONObject("sg")
                val plObj = obj.optJSONObject("pl")
                val sg = listOf("nom", "gen", "dat", "acc", "inst", "prep").associateWith { sgObj?.optString(it).orEmpty() }
                val pl = listOf("nom", "gen", "dat", "acc", "inst", "prep").associateWith { plObj?.optString(it).orEmpty() }
                InflectionData.Noun(
                    partner = obj.optString("partner").takeIf { it.isNotBlank() },
                    singular = sg,
                    plural = pl,
                )
            }
            "verb" -> {
                val pfObj = obj.optJSONObject("presfut")
                val pastObj = obj.optJSONObject("past")
                val impObj = obj.optJSONObject("imperative")
                val presfut = listOf("sg1", "sg2", "sg3", "pl1", "pl2", "pl3").associateWith { pfObj?.optString(it).orEmpty() }
                val past = listOf("m", "f", "n", "pl").associateWith { pastObj?.optString(it).orEmpty() }
                val imp = listOf("sg", "pl").associateWith { impObj?.optString(it).orEmpty() }
                InflectionData.Verb(
                    aspect = obj.optString("aspect").takeIf { it.isNotBlank() },
                    partner = obj.optString("partner").takeIf { it.isNotBlank() },
                    presentOrFuture = presfut,
                    past = past,
                    imperative = imp,
                )
            }
            "adjective" -> {
                val shortObj = obj.optJSONObject("short")
                val casesObj = obj.optJSONObject("cases")
                val shortForms = listOf("m", "f", "n", "pl").associateWith { shortObj?.optString(it).orEmpty() }
                val cases = listOf("m", "f", "n", "pl").associateWith { genderKey ->
                    val gObj = casesObj?.optJSONObject(genderKey)
                    listOf("nom", "gen", "dat", "acc", "inst", "prep").associateWith { gObj?.optString(it).orEmpty() }
                }
                InflectionData.Adjective(
                    comparative = obj.optString("comparative").takeIf { it.isNotBlank() },
                    superlative = obj.optString("superlative").takeIf { it.isNotBlank() },
                    shortForms = shortForms,
                    cases = cases,
                )
            }
            else -> null
        }
    }.getOrNull()
}

data class LocalExplanationUiState(
    val id: String,
    val title: String,
    val category: String,
    val content: String,
    val examples: List<String>,
    val keywords: List<String>,
    val sourceDocument: String,
    val sectionPath: List<String>,
)

sealed interface LoadableState<out T> {
    data object Loading : LoadableState<Nothing>
    data class Content<T>(val value: T) : LoadableState<T>
    data object NotFound : LoadableState<Nothing>
    data object Error : LoadableState<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val repository: SearchRepository,
    private val initializer: DataInitializer,
    private val savedStateHandle: SavedStateHandle,
    private val speechRepository: SpeechRepository? = null,
) : ViewModel() {
    private val query = savedStateHandle.getStateFlow(QUERY_KEY, "")
    private val partOfSpeech = savedStateHandle.getStateFlow<String?>(PART_OF_SPEECH_KEY, null)
    private val lesson = savedStateHandle.getStateFlow<Int?>(LESSON_KEY, null)
    private val retryGeneration = MutableStateFlow(0)
    private val _voiceInput = MutableStateFlow(VoiceInputState())
    val voiceInput: StateFlow<VoiceInputState> = _voiceInput

    val controls: StateFlow<SearchControlsState> = combine(
        query,
        partOfSpeech,
        lesson,
        ::SearchControlsState,
    ).stateIn(viewModelScope, SharingStarted.Eagerly, SearchControlsState())

    private val searchResults = combine(
        controls,
        initializer.state,
        retryGeneration,
    ) { request, initialization, _ -> SearchTrigger(request, initialization) }
        .debounce(300)
        .transformLatest { trigger ->
            val request = trigger.controls
            if (trigger.initialization !is InitializationState.Ready) {
                emit(SearchResult(request, isLoading = trigger.initialization is InitializationState.Initializing))
                return@transformLatest
            }
            emit(SearchResult(request, isLoading = true))
            try {
                val count = repository.getEntryCount()
                val result = when {
                    request.query.isNotBlank() -> {
                        val response = repository.searchWithFuzzy(request.query, request.partOfSpeech, request.lesson, 50)
                        SearchResult(
                            controls = request,
                            results = response.entries,
                            databaseEntryCount = count,
                            redirectedFrom = response.redirectedFrom,
                            redirectedTo = response.redirectedTo,
                            isFuzzyMatch = response.isFuzzyMatch,
                        )
                    }
                    request.partOfSpeech != null || request.lesson != null -> SearchResult(
                        controls = request,
                        browseEntries = repository.browseEntries(request.partOfSpeech, request.lesson, 50),
                        databaseEntryCount = count,
                    )
                    else -> SearchResult(
                        controls = request,
                        recentEntries = repository.recentEntries(10),
                        browseEntries = repository.browseEntries(limit = 30),
                        databaseEntryCount = count,
                    )
                }.withBrowseDiagnostic()
                logSearchDiagnostic(result)
                emit(result)
            } catch (_: Exception) {
                emit(SearchResult(request, hasError = true))
            }
        }

    private val filterOptions = combine(
        repository.observePartOfSpeechOptions(),
        repository.observeLessonOptions(),
    ) { speech, lessons -> FilterOptions(speech, lessons) }

    val uiState: StateFlow<SearchUiState> = combine(
        searchResults,
        initializer.state,
        filterOptions,
    ) { search, initialization, options ->
        SearchUiState(
            controls = search.controls,
            results = search.results,
            isLoading = search.isLoading,
            hasSearchError = search.hasError,
            initializationState = initialization,
            partOfSpeechOptions = options.partsOfSpeech,
            lessonOptions = options.lessons,
            recentEntries = search.recentEntries,
            browseEntries = search.browseEntries,
            databaseEntryCount = search.databaseEntryCount,
            browseInconsistent = search.browseInconsistent,
            redirectedFrom = search.redirectedFrom,
            redirectedTo = search.redirectedTo,
            isFuzzyMatch = search.isFuzzyMatch,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    init {
        viewModelScope.launch { initializer.initialize() }
    }

    fun setQuery(value: String) { savedStateHandle[QUERY_KEY] = value }
    fun setPartOfSpeech(value: String?) { savedStateHandle[PART_OF_SPEECH_KEY] = value }
    fun setLesson(value: Int?) { savedStateHandle[LESSON_KEY] = value }
    fun resetFilters() {
        setPartOfSpeech(null)
        setLesson(null)
    }
    fun retrySearch() { retryGeneration.value += 1 }

    fun beginVoiceRecording() {
        _voiceInput.value = VoiceInputState(VoiceInputStatus.Recording, message = "正在录音")
    }

    fun cancelVoiceRecording() {
        _voiceInput.value = VoiceInputState()
    }

    fun transcribeVoice(file: File) {
        val repository = speechRepository
        if (repository == null || !repository.isConfigured) {
            file.delete()
            _voiceInput.value = VoiceInputState(
                VoiceInputStatus.Error, message = "语音服务尚未配置"
            )
            return
        }
        _voiceInput.value = VoiceInputState(VoiceInputStatus.Processing, message = "正在识别俄语")
        viewModelScope.launch {
            try {
                val result = repository.transcribe(file)
                when {
                    result.confidence >= 0.85 && result.language == "ru" -> {
                        setQuery(result.normalized_transcript)
                        _voiceInput.value = VoiceInputState(
                            VoiceInputStatus.Success, result.normalized_transcript,
                            result.confidence, "识别完成"
                        )
                    }
                    else -> _voiceInput.value = VoiceInputState(
                        VoiceInputStatus.LowConfidence,
                        result.normalized_transcript,
                        result.confidence,
                        result.warnings.firstOrNull() ?: "请确认识别文本后再搜索",
                    )
                }
            } catch (exception: Exception) {
                _voiceInput.value = VoiceInputState(
                    VoiceInputStatus.Error, message = exception.message ?: "语音识别失败"
                )
            } finally {
                file.delete()
            }
        }
    }

    fun confirmVoiceCandidate() {
        _voiceInput.value.candidate?.takeIf { it.isNotBlank() }?.let(::setQuery)
        _voiceInput.value = _voiceInput.value.copy(status = VoiceInputStatus.Success)
    }
}

class WordDetailViewModel(
    repository: SearchRepository,
    entryId: String,
) : ViewModel() {
    val uiState: StateFlow<LoadableState<WordDetailUiState>> = repository
        .observeWordDetail(entryId)
        .toLoadableState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadableState.Loading)

    init {
        viewModelScope.launch { repository.markViewed(entryId) }
    }
}

class LocalExplanationViewModel(
    repository: SearchRepository,
    chunkId: String,
) : ViewModel() {
    val uiState: StateFlow<LoadableState<LocalExplanationUiState>> = repository
        .observeLocalExplanation(chunkId)
        .toLoadableState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadableState.Loading)
}

private fun <T : Any> Flow<T?>.toLoadableState(): Flow<LoadableState<T>> =
    map<T?, LoadableState<T>> { value ->
        if (value == null) LoadableState.NotFound else LoadableState.Content(value)
    }.catch { emit(LoadableState.Error) }

private data class SearchResult(
    val controls: SearchControlsState,
    val results: List<LexiconEntryWithDetails> = emptyList(),
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val recentEntries: List<LexiconEntryWithDetails> = emptyList(),
    val browseEntries: List<LexiconEntryWithDetails> = emptyList(),
    val databaseEntryCount: Int = 0,
    val browseInconsistent: Boolean = false,
    val redirectedFrom: String? = null,
    val redirectedTo: String? = null,
    val isFuzzyMatch: Boolean = false,
)

private data class SearchTrigger(
    val controls: SearchControlsState,
    val initialization: InitializationState,
)

private fun SearchResult.withBrowseDiagnostic(): SearchResult = copy(
    browseInconsistent = controls.query.isBlank() &&
        databaseEntryCount > 0 && browseEntries.isEmpty() &&
        controls.partOfSpeech == null && controls.lesson == null,
)

private fun logSearchDiagnostic(result: SearchResult) {
    if (!BuildConfig.DEBUG) return
    Log.d(
        "RusMorphSearch",
        "RusMorph database entries: ${result.databaseEntryCount}; " +
            "Query: \"${result.controls.query}\"; " +
            "Part of speech: ${result.controls.partOfSpeech}; " +
            "Lesson: ${result.controls.lesson}; " +
            "Search result count: ${result.results.size}; " +
            "Empty-query result count: ${result.browseEntries.size}",
    )
}

private data class FilterOptions(
    val partsOfSpeech: List<String>,
    val lessons: List<Int>,
)
