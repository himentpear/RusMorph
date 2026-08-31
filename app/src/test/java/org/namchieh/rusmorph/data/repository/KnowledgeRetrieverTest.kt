package org.namchieh.rusmorph.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.namchieh.rusmorph.data.local.EntryPartOfSpeechEntity
import org.namchieh.rusmorph.data.local.EntrySearchFormEntity
import org.namchieh.rusmorph.data.local.KnowledgeChunkEntity
import org.namchieh.rusmorph.data.local.LexiconEntryEntity
import org.namchieh.rusmorph.data.local.LexiconEntryWithDetails

class KnowledgeRetrieverTest {
    @Test
    fun missingAssociationsReturnEmptyWithoutThrowing() = runTest {
        val retriever = KnowledgeRetriever(SearchRepository(KnowledgeFakeDataSource(details())))

        val result = retriever.retrieve("entry", KnowledgeQuestionType.MORPHOLOGY, null)

        assertTrue(result.knowledgeChunks.isEmpty())
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun morphologyIsPrioritySortedAndLimitedToFour() = runTest {
        val chunks = listOf(
            chunk("other", "OTHER", "一般说明"),
            chunk("stem", "STEM_ALTERNATION", "词干交替"),
            chunk("athe", "ATHEMATIC_CONJUGATION", "特殊变位"),
            chunk("pal", "FIRST_PALATALIZATION", "腭化"),
            chunk("iot", "IOTATION", "j 音组"),
        )
        val retriever = KnowledgeRetriever(SearchRepository(KnowledgeFakeDataSource(details(chunks))))

        val result = retriever.retrieve("entry", KnowledgeQuestionType.MORPHOLOGY, null)

        assertEquals(4, result.knowledgeChunks.size)
        assertEquals(listOf("iot", "pal", "athe", "stem"), result.knowledgeChunks.map { it.id })
        assertEquals(4, result.evidence.size)
    }

    @Test
    fun etymologyRequiresExplicitHistoricalEvidence() = runTest {
        val chunks = listOf(
            chunk("background", "GENERAL_PROJECT_BACKGROUND", "项目的一般背景"),
            chunk("history", "OTHER", "古俄语形式在历史演变中保留下来"),
        )
        val retriever = KnowledgeRetriever(SearchRepository(KnowledgeFakeDataSource(details(chunks))))

        val result = retriever.retrieve("entry", KnowledgeQuestionType.ETYMOLOGY, null)

        assertEquals(listOf("history"), result.knowledgeChunks.map { it.id })
    }
}

private class KnowledgeFakeDataSource(
    private val observed: LexiconEntryWithDetails?,
) : SearchDataSource {
    override suspend fun search(normalizedQuery: String, rawQuery: String, partOfSpeech: String?, lesson: Int?, limit: Int) = emptyList<LexiconEntryWithDetails>()
    override suspend fun recentOrRecommended(limit: Int) = emptyList<LexiconEntryWithDetails>()
    override fun observeEntry(entryId: String): Flow<LexiconEntryWithDetails?> = flowOf(observed)
    override suspend fun markViewed(entryId: String, viewedAt: Long) = Unit
    override fun observePartOfSpeechOptions(): Flow<List<String>> = flowOf(emptyList())
    override fun observeLessonOptions(): Flow<List<Int>> = flowOf(emptyList())
    override fun observeKnowledgeChunk(chunkId: String): Flow<KnowledgeChunkEntity?> = flowOf(null)
}

private fun details(chunks: List<KnowledgeChunkEntity> = emptyList()) = LexiconEntryWithDetails(
    entry = LexiconEntryEntity(
        id = "entry", lesson = 1, sequence = 1, displayForm = "сло́во", lemma = "слово",
        normalizedLemma = "слово", chineseMeaning = "词", gender = "中性",
        declensionClass = "2", endingType = "о", pluralStressPattern = null,
        aspect = null, conjugationClass = null, phoneticAlternation = null,
        sourceWorkbook = "test.csv", sourceSheet = "词表", sourceRow = 2,
    ),
    searchForms = listOf(EntrySearchFormEntity("entry", "слово")),
    partsOfSpeech = listOf(EntryPartOfSpeechEntity("entry", "名词")),
    sources = emptyList(),
    knowledgeChunks = chunks,
)

private fun chunk(id: String, category: String, content: String) = KnowledgeChunkEntity(
    id = id,
    title = id,
    category = category,
    keywordsJson = "[]",
    content = content,
    examplesJson = "[]",
    sourceDocument = "test.docx",
    sectionPathJson = "[]",
)
