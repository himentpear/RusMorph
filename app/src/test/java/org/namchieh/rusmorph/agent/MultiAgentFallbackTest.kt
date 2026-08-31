package org.namchieh.rusmorph.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.namchieh.rusmorph.data.local.*
import org.namchieh.rusmorph.data.repository.*

class MultiAgentFallbackTest {
    @Test fun routerFailureStillReturnsLocalWordCard() = runTest {
        val coordinator = MultiAgentCoordinator(OfflineRepository, SearchRepository(FallbackSource(entry())))
        val result = coordinator.execute("автомобиль", AgentSessionContext()) as MultiAgentResult.Completed
        assertEquals("автомоби́ль", result.response.card?.word)
        assertNull(result.response.card?.etymology)
        assertTrue(result.response.card?.warnings.isNullOrEmpty())
    }

    @Test fun fallbackPreservesLocalMorphologyAndSource() = runTest {
        val coordinator = MultiAgentCoordinator(OfflineRepository, SearchRepository(FallbackSource(entry())))
        val card = (coordinator.execute("汽车", AgentSessionContext()) as MultiAgentResult.Completed).response.card!!
        assertTrue(card.morphology.orEmpty().contains("阳性"))
        assertTrue(card.evidence.any { it.title == "本地来源" })
        assertTrue(card.generatedSentences.isEmpty())
    }

    @Test fun localMissIsReportedToRouterBeforeAiQueryExpansion() = runTest {
        val repository = CapturingRepository()
        val coordinator = MultiAgentCoordinator(repository, SearchRepository(EmptySource))
        coordinator.execute("песат", AgentSessionContext())
        assertTrue(repository.routedSession?.localSearchMiss == true)
    }

    @Test fun directLookupUsesConfiguredDeckLimit() = runTest {
        val source = LimitCapturingSource(entry())
        val coordinator = MultiAgentCoordinator(OfflineRepository, SearchRepository(source))
        coordinator.execute("автомобиль", AgentSessionContext(deckLimit = 150))
        assertEquals(150, source.lastLimit)
    }

    private fun entry(): LexiconEntryWithDetails {
        val entity = LexiconEntryEntity("auto", 18, 18, "автомоби́ль", "автомобиль", "автомобиль", "汽车", "阳性", "2", "ь", null, null, null, null, "book.csv", "词表", 123)
        return LexiconEntryWithDetails(entity, listOf(EntrySearchFormEntity("auto", "автомобиль")), listOf(EntryPartOfSpeechEntity("auto", "名词")), listOf(EntrySourceEntity("auto", "book.csv", "词表", 123)), emptyList())
    }
}

private class LimitCapturingSource(
    private val item: LexiconEntryWithDetails,
) : SearchDataSource {
    var lastLimit: Int? = null
    override suspend fun search(
        normalizedQuery: String,
        rawQuery: String,
        partOfSpeech: String?,
        lesson: Int?,
        limit: Int,
    ): List<LexiconEntryWithDetails> {
        lastLimit = limit
        return listOf(item)
    }
    override suspend fun recentOrRecommended(limit: Int) = listOf(item)
    override fun observeEntry(entryId: String): Flow<LexiconEntryWithDetails?> = flowOf(item)
    override suspend fun markViewed(entryId: String, viewedAt: Long) = Unit
    override fun observePartOfSpeechOptions(): Flow<List<String>> = flowOf(emptyList())
    override fun observeLessonOptions(): Flow<List<Int>> = flowOf(emptyList())
    override fun observeKnowledgeChunk(chunkId: String): Flow<KnowledgeChunkEntity?> = flowOf(null)
}

private class CapturingRepository : AgentRepository {
    override val availability = AgentAvailability.AVAILABLE
    var routedSession: AgentSessionContext? = null
    override suspend fun ask(request: AgentRequest) = AgentResult.Failure(AgentError.NoNetwork)
    override suspend fun routeCommand(command: String, session: AgentSessionContext): MultiAgentResult {
        routedSession = session
        return MultiAgentResult.Failure(AgentError.NoNetwork)
    }
}

private object EmptySource : SearchDataSource {
    override suspend fun search(normalizedQuery: String, rawQuery: String, partOfSpeech: String?, lesson: Int?, limit: Int) = emptyList<LexiconEntryWithDetails>()
    override suspend fun recentOrRecommended(limit: Int) = emptyList<LexiconEntryWithDetails>()
    override fun observeEntry(entryId: String): Flow<LexiconEntryWithDetails?> = flowOf(null)
    override suspend fun markViewed(entryId: String, viewedAt: Long) = Unit
    override fun observePartOfSpeechOptions(): Flow<List<String>> = flowOf(emptyList())
    override fun observeLessonOptions(): Flow<List<Int>> = flowOf(emptyList())
    override fun observeKnowledgeChunk(chunkId: String): Flow<KnowledgeChunkEntity?> = flowOf(null)
}

private object OfflineRepository : AgentRepository {
    override val availability = AgentAvailability.OFFLINE
    override suspend fun ask(request: AgentRequest) = AgentResult.Failure(AgentError.NoNetwork)
    override suspend fun routeCommand(command: String, session: AgentSessionContext) = MultiAgentResult.Failure(AgentError.NoNetwork)
}

private class FallbackSource(private val item: LexiconEntryWithDetails) : SearchDataSource {
    override suspend fun search(normalizedQuery: String, rawQuery: String, partOfSpeech: String?, lesson: Int?, limit: Int) = listOf(item)
    override suspend fun recentOrRecommended(limit: Int) = listOf(item)
    override suspend fun browseEntries(partOfSpeech: String?, lesson: Int?, limit: Int) = listOf(item)
    override suspend fun fuzzyCandidates(partOfSpeech: String?, lesson: Int?, limit: Int) = emptyList<FuzzyCandidateRow>()
    override suspend fun entriesByIds(entryIds: List<String>) = listOf(item)
    override fun observeEntry(entryId: String): Flow<LexiconEntryWithDetails?> = flowOf(item)
    override suspend fun markViewed(entryId: String, viewedAt: Long) = Unit
    override fun observePartOfSpeechOptions(): Flow<List<String>> = flowOf(listOf("名词"))
    override fun observeLessonOptions(): Flow<List<Int>> = flowOf(listOf(18))
    override fun observeKnowledgeChunk(chunkId: String): Flow<KnowledgeChunkEntity?> = flowOf(null)
}
