package org.namchieh.rusmorph.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.namchieh.rusmorph.data.local.EntryPartOfSpeechEntity
import org.namchieh.rusmorph.data.local.EntrySearchFormEntity
import org.namchieh.rusmorph.data.local.EntrySourceEntity
import org.namchieh.rusmorph.data.local.KnowledgeChunkEntity
import org.namchieh.rusmorph.data.local.LexiconEntryEntity
import org.namchieh.rusmorph.data.local.LexiconEntryWithDetails

class SearchRepositoryTest {
    @Test
    fun normalizesAccentsAndPassesFiltersToFakeDataSource() = runTest {
        val fake = FakeSearchDataSource()
        val repository = SearchRepository(fake)

        repository.search("  автомоби́ль  ", partOfSpeech = "名词", lesson = 18)

        assertEquals("автомобиль", fake.lastNormalizedQuery)
        assertEquals("автомоби́ль", fake.lastRawQuery)
        assertEquals("名词", fake.lastPartOfSpeech)
        assertEquals(18, fake.lastLesson)
    }

    @Test
    fun emptyInputUsesBrowseInsteadOfRecommendations() = runTest {
        val fake = FakeSearchDataSource(recent = listOf(details("recent")))
        val repository = SearchRepository(fake)

        val result = repository.search("   ")

        assertEquals(listOf("recent"), result.map { it.entry.id })
        assertTrue(fake.browseCalled)
        assertFalse(fake.searchCalled)
    }

    @Test
    fun wordDetailContainsAllLocalFieldsKnowledgeAndSources() = runTest {
        val item = details("write", withDetail = true)
        val repository = SearchRepository(FakeSearchDataSource(observed = item))

        val detail = repository.observeWordDetail("write").first()!!

        assertEquals("писа́ть", detail.displayForm)
        assertEquals("写", detail.chineseMeaning)
        assertEquals(listOf("动词"), detail.partsOfSpeech)
        assertEquals("未完成体", detail.aspect)
        assertEquals("с-ш", detail.phoneticAlternation)
        assertEquals("IOTATION", detail.relatedKnowledge.single().category)
        assertEquals("test.xlsx", detail.sources.single().workbook)
    }
}

private class FakeSearchDataSource(
    private val results: List<LexiconEntryWithDetails> = emptyList(),
    private val recent: List<LexiconEntryWithDetails> = emptyList(),
    private val observed: LexiconEntryWithDetails? = null,
) : SearchDataSource {
    var searchCalled = false
    var recentCalled = false
    var browseCalled = false
    var lastNormalizedQuery: String? = null
    var lastRawQuery: String? = null
    var lastPartOfSpeech: String? = null
    var lastLesson: Int? = null

    override suspend fun search(
        normalizedQuery: String,
        rawQuery: String,
        partOfSpeech: String?,
        lesson: Int?,
        limit: Int,
    ): List<LexiconEntryWithDetails> {
        searchCalled = true
        lastNormalizedQuery = normalizedQuery
        lastRawQuery = rawQuery
        lastPartOfSpeech = partOfSpeech
        lastLesson = lesson
        return results
    }

    override suspend fun recentOrRecommended(limit: Int): List<LexiconEntryWithDetails> {
        recentCalled = true
        return recent
    }

    override suspend fun browseEntries(partOfSpeech: String?, lesson: Int?, limit: Int): List<LexiconEntryWithDetails> {
        browseCalled = true
        lastPartOfSpeech = partOfSpeech
        lastLesson = lesson
        return recent.take(limit)
    }

    override fun observeEntry(entryId: String): Flow<LexiconEntryWithDetails?> = flowOf(observed)

    override suspend fun markViewed(entryId: String, viewedAt: Long) = Unit

    override fun observePartOfSpeechOptions(): Flow<List<String>> = flowOf(emptyList())

    override fun observeLessonOptions(): Flow<List<Int>> = flowOf(emptyList())

    override fun observeKnowledgeChunk(chunkId: String): Flow<KnowledgeChunkEntity?> = flowOf(null)
}

private fun details(id: String, withDetail: Boolean = false): LexiconEntryWithDetails {
    val entry = LexiconEntryEntity(
        id = id,
        lesson = 1,
        sequence = 1,
        displayForm = if (withDetail) "писа́ть" else id,
        lemma = if (withDetail) "писать" else id,
        normalizedLemma = if (withDetail) "писать" else id,
        chineseMeaning = if (withDetail) "写" else null,
        gender = null,
        declensionClass = null,
        endingType = null,
        pluralStressPattern = null,
        aspect = if (withDetail) "未完成体" else null,
        conjugationClass = if (withDetail) "I" else null,
        phoneticAlternation = if (withDetail) "с-ш" else null,
        sourceWorkbook = "test.xlsx",
        sourceSheet = "词表",
        sourceRow = 2,
        isRecommended = true,
    )
    return LexiconEntryWithDetails(
        entry = entry,
        searchForms = listOf(EntrySearchFormEntity(id, entry.normalizedLemma)),
        partsOfSpeech = if (withDetail) listOf(EntryPartOfSpeechEntity(id, "动词")) else emptyList(),
        sources = if (withDetail) listOf(EntrySourceEntity(id, "test.xlsx", "词表", 2)) else emptyList(),
        knowledgeChunks = if (withDetail) {
            listOf(
                KnowledgeChunkEntity(
                    id = "iot",
                    title = "j-音组变化",
                    category = "IOTATION",
                    keywordsJson = "[]",
                    content = "писать → пишу",
                    examplesJson = "[]",
                    sourceDocument = "knowledge.docx",
                    sectionPathJson = "[]",
                ),
            )
        } else {
            emptyList()
        },
    )
}
