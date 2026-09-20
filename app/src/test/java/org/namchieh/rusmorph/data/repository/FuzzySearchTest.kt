package org.namchieh.rusmorph.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.namchieh.rusmorph.data.local.*

class FuzzySearchTest {
    @Test fun exact_results_remain_the_default_before_fallback() = runTest {
        val item = details("employee", "сотру́дник", "сотрудник")
        val repository = SearchRepository(FuzzySource(item))
        val result = repository.searchWithFuzzyFallback("сотрудник")
        assertEquals(false, result.isFuzzyMatch)
        assertEquals("employee", result.entries.single().entry.id)
    }

    @Test fun oneAndTwoCharacterTyposResolveLocally() = runTest {
        val item = details("employee", "сотру́дник", "сотрудник")
        val repository = SearchRepository(FuzzySource(item))
        assertEquals("employee", repository.fuzzySearch("сотрудникк").single().entry.id)
        assertEquals("employee", repository.fuzzySearch("сотруник").single().entry.id)
    }

    @Test fun yoAndYeRecallEachOtherWithoutChangingDisplayForm() = runTest {
        val item = details("all", "всё", "всё")
        val repository = SearchRepository(FuzzySource(item))
        assertEquals("всё", repository.fuzzySearch("все").single().entry.displayForm)
    }

    @Test fun editDistanceIsBounded() {
        assertEquals(1, editDistanceAtMost("слово", "слова", 2))
        assertEquals(3, editDistanceAtMost("слово", "далеко", 2))
    }
}

private class FuzzySource(private val item: LexiconEntryWithDetails) : SearchDataSource {
    override suspend fun search(normalizedQuery: String, rawQuery: String, partOfSpeech: String?, lesson: Int?, limit: Int): List<LexiconEntryWithDetails> =
        if (normalizedQuery.replace('е', 'ё') == item.entry.normalizedLemma.replace('е', 'ё')) listOf(item) else emptyList()
    override suspend fun recentOrRecommended(limit: Int) = emptyList<LexiconEntryWithDetails>()
    override suspend fun fuzzyCandidates(partOfSpeech: String?, lesson: Int?, limit: Int) = listOf(FuzzyCandidateRow(item.entry.id, item.entry.normalizedLemma, item.searchForms.first().normalizedSearchForm, item.entry.chineseMeaning))
    override suspend fun entriesByIds(entryIds: List<String>) = listOf(item).filter { it.entry.id in entryIds }
    override fun observeEntry(entryId: String): Flow<LexiconEntryWithDetails?> = flowOf(item)
    override suspend fun markViewed(entryId: String, viewedAt: Long) = Unit
    override fun observePartOfSpeechOptions(): Flow<List<String>> = flowOf(emptyList())
    override fun observeLessonOptions(): Flow<List<Int>> = flowOf(emptyList())
    override fun observeKnowledgeChunk(chunkId: String): Flow<KnowledgeChunkEntity?> = flowOf(null)
}

private fun details(id: String, display: String, normalized: String): LexiconEntryWithDetails {
    val entry = LexiconEntryEntity(id, 1, 1, display, normalized, normalized, null, null, null, null, null, null, null, null, "test", "test", 1)
    return LexiconEntryWithDetails(entry, listOf(EntrySearchFormEntity(id, normalized)), emptyList(), emptyList(), emptyList())
}
