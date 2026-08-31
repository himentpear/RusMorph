package org.namchieh.rusmorph.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.namchieh.rusmorph.data.local.DataInitializer
import org.namchieh.rusmorph.data.local.EntryPartOfSpeechEntity
import org.namchieh.rusmorph.data.local.EntrySearchFormEntity
import org.namchieh.rusmorph.data.local.InitializationState
import org.namchieh.rusmorph.data.local.KnowledgeChunkEntity
import org.namchieh.rusmorph.data.local.LexiconEntryEntity
import org.namchieh.rusmorph.data.local.LexiconEntryWithDetails
import org.namchieh.rusmorph.data.repository.SearchDataSource
import org.namchieh.rusmorph.data.repository.SearchRepository
import org.namchieh.rusmorph.ui.components.visibleValue
import org.namchieh.rusmorph.ui.navigation.Routes
import org.namchieh.rusmorph.ui.navigation.destinationForInitialization
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class SearchUiStateTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun readyInitializationNavigatesToLearningHomeAndRoutesCarryOnlyIds() {
        assertEquals(Routes.Home, destinationForInitialization(InitializationState.Ready(false)))
        assertNull(destinationForInitialization(InitializationState.Initializing))
        assertEquals("word/entry-1", Routes.word("entry-1"))
        assertEquals("local-explanation/chunk-1", Routes.explanation("chunk-1"))
    }

    @Test
    fun searchStateHandlesWritingAccentNoResultAndEmptyRecommendations() = runTest(dispatcher) {
        val recent = details("recent", "друг", lastViewedAt = 2L)
        val recommended = details("recommended", "мочь", recommended = true)
        val source = FakeSearchDataSource(recent = listOf(recent), browse = listOf(recommended), entryCount = 2)
        val viewModel = SearchViewModel(
            SearchRepository(source),
            FakeInitializer(),
            SavedStateHandle(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        advanceTimeBy(301)
        runCurrent()
        assertEquals(listOf("recent"), viewModel.uiState.value.recentEntries.map { it.entry.id })
        assertEquals(listOf("recommended"), viewModel.uiState.value.browseEntries.map { it.entry.id })

        viewModel.setQuery("писать")
        advanceTimeBy(301)
        runCurrent()
        assertEquals("писа́ть", viewModel.uiState.value.results.single().entry.displayForm)

        viewModel.setQuery("автомоби́ль")
        advanceTimeBy(301)
        runCurrent()
        assertEquals("автомоби́ль", viewModel.uiState.value.results.single().entry.displayForm)

        viewModel.setQuery("несуществующее")
        advanceTimeBy(301)
        runCurrent()
        assertFalse(viewModel.uiState.value.isEmptyQuery)
        assertTrue(viewModel.uiState.value.results.isEmpty())
        assertFalse(viewModel.uiState.value.hasSearchError)
    }

    @Test
    fun emptyQueryBrowsesAndFiltersWithoutDefaultingLesson() = runTest(dispatcher) {
        val item = details("browse", "сотру́дник")
        val source = FakeSearchDataSource(browse = listOf(item), entryCount = 947)
        val viewModel = SearchViewModel(SearchRepository(source), FakeInitializer(), SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertNull(viewModel.controls.value.lesson)
        assertNull(viewModel.controls.value.partOfSpeech)
        advanceTimeBy(301); runCurrent()
        assertEquals(30, source.lastBrowseLimit)
        assertEquals(listOf("browse"), viewModel.uiState.value.browseEntries.map { it.entry.id })

        viewModel.setLesson(1)
        advanceTimeBy(301); runCurrent()
        assertEquals(1, source.lastBrowseLesson)
        assertEquals(50, source.lastBrowseLimit)

        viewModel.setLesson(null)
        viewModel.setPartOfSpeech("名词")
        advanceTimeBy(301); runCurrent()
        assertEquals("名词", source.lastBrowsePartOfSpeech)

        viewModel.setQuery("сотрудник")
        advanceTimeBy(301); runCurrent()
        assertTrue(source.searchCalls > 0)

        viewModel.resetFilters(); runCurrent()
        assertNull(viewModel.controls.value.lesson)
        assertNull(viewModel.controls.value.partOfSpeech)
        assertEquals("сотрудник", viewModel.controls.value.query)
    }

    @Test
    fun readyTransitionRefreshesAndEmptyBrowseWithNonemptyDatabaseIsDiagnostic() = runTest(dispatcher) {
        val initializer = ManualInitializer()
        val source = FakeSearchDataSource(entryCount = 947)
        val viewModel = SearchViewModel(SearchRepository(source), initializer, SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceTimeBy(301); runCurrent()
        assertEquals(0, source.browseCalls)
        initializer.ready()
        advanceTimeBy(301); runCurrent()
        assertEquals(1, source.browseCalls)
        assertTrue(viewModel.uiState.value.browseInconsistent)
    }

    @Test
    fun savedSearchControlsAreRestoredAndUpdatedThroughSavedStateHandle() = runTest(dispatcher) {
        val handle = SavedStateHandle(
            mapOf(
                "search_query" to "писать",
                "search_part_of_speech" to "动词",
                "search_lesson" to 18,
            ),
        )
        val viewModel = SearchViewModel(SearchRepository(FakeSearchDataSource()), FakeInitializer(), handle)
        runCurrent()

        assertEquals(SearchControlsState("писать", "动词", 18), viewModel.controls.value)
        viewModel.setQuery("автомобиль")
        viewModel.setPartOfSpeech("名词")
        viewModel.setLesson(2)
        runCurrent()

        assertEquals("автомобиль", handle["search_query"])
        assertEquals("名词", handle["search_part_of_speech"])
        assertEquals(2, handle["search_lesson"])
    }

    @Test
    fun placeholderValuesAreHidden() {
        assertNull(null.visibleValue())
        assertNull("".visibleValue())
        assertNull("  ".visibleValue())
        assertNull("8".visibleValue())
        assertEquals("с-ш", " с-ш ".visibleValue())
    }

    @Test
    fun wordDetailMarksViewedOnceAndExposesWritingAlternation() = runTest(dispatcher) {
        val source = FakeSearchDataSource(observed = details("write", "писа́ть", phonetic = "с-ш"))
        val viewModel = WordDetailViewModel(SearchRepository(source), "write")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()

        assertEquals(1, source.markViewedCalls)
        val content = viewModel.uiState.value as LoadableState.Content
        assertEquals("с-ш", content.value.phoneticAlternation)
    }
}

private class FakeInitializer : DataInitializer {
    private val mutableState = MutableStateFlow<InitializationState>(InitializationState.NotStarted)
    override val state = mutableState

    override suspend fun initialize() {
        mutableState.value = InitializationState.Ready(false)
    }
}

private class ManualInitializer : DataInitializer {
    private val mutableState = MutableStateFlow<InitializationState>(InitializationState.Initializing)
    override val state = mutableState
    override suspend fun initialize() = Unit
    fun ready() { mutableState.value = InitializationState.Ready(false) }
}

private class FakeSearchDataSource(
    private val recent: List<LexiconEntryWithDetails> = emptyList(),
    private val browse: List<LexiconEntryWithDetails> = emptyList(),
    private val entryCount: Int = if (browse.isEmpty() && recent.isEmpty()) 0 else browse.size + recent.size,
    private val observed: LexiconEntryWithDetails? = null,
) : SearchDataSource {
    var markViewedCalls = 0
    var browseCalls = 0
    var searchCalls = 0
    var lastBrowseLesson: Int? = null
    var lastBrowsePartOfSpeech: String? = null
    var lastBrowseLimit = 0

    override suspend fun search(
        normalizedQuery: String,
        rawQuery: String,
        partOfSpeech: String?,
        lesson: Int?,
        limit: Int,
    ): List<LexiconEntryWithDetails> {
        searchCalls += 1
        return when (normalizedQuery) {
        "писать" -> listOf(details("write", "писа́ть", phonetic = "с-ш"))
        "автомобиль" -> listOf(details("car", "автомоби́ль"))
        else -> emptyList()
        }
    }

    override suspend fun recentOrRecommended(limit: Int) = recent.take(limit)
    override suspend fun recentEntries(limit: Int) = recent.take(limit)
    override suspend fun browseEntries(partOfSpeech: String?, lesson: Int?, limit: Int): List<LexiconEntryWithDetails> {
        browseCalls += 1
        lastBrowsePartOfSpeech = partOfSpeech
        lastBrowseLesson = lesson
        lastBrowseLimit = limit
        return browse.take(limit)
    }
    override suspend fun getEntryCount() = entryCount
    override fun observeEntry(entryId: String): Flow<LexiconEntryWithDetails?> = flowOf(observed)
    override suspend fun markViewed(entryId: String, viewedAt: Long) { markViewedCalls += 1 }
    override fun observePartOfSpeechOptions(): Flow<List<String>> = flowOf(listOf("名词", "动词"))
    override fun observeLessonOptions(): Flow<List<Int>> = flowOf(listOf(1, 2, 18))
    override fun observeKnowledgeChunk(chunkId: String): Flow<KnowledgeChunkEntity?> = flowOf(null)
}

private fun details(
    id: String,
    displayForm: String,
    lastViewedAt: Long? = null,
    recommended: Boolean = false,
    phonetic: String? = null,
): LexiconEntryWithDetails {
    val normalized = displayForm.replace("\u0301", "")
    return LexiconEntryWithDetails(
        entry = LexiconEntryEntity(
            id = id,
            lesson = 1,
            sequence = 1,
            displayForm = displayForm,
            lemma = normalized,
            normalizedLemma = normalized,
            chineseMeaning = null,
            gender = null,
            declensionClass = null,
            endingType = null,
            pluralStressPattern = null,
            aspect = null,
            conjugationClass = null,
            phoneticAlternation = phonetic,
            sourceWorkbook = "test.xlsx",
            sourceSheet = "词表",
            sourceRow = 2,
            lastViewedAt = lastViewedAt,
            isRecommended = recommended,
        ),
        searchForms = listOf(EntrySearchFormEntity(id, normalized)),
        partsOfSpeech = listOf(EntryPartOfSpeechEntity(id, "动词")),
        sources = emptyList(),
        knowledgeChunks = emptyList(),
    )
}
