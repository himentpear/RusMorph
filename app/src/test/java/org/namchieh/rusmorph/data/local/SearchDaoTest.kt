package org.namchieh.rusmorph.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.Executor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.namchieh.rusmorph.data.repository.normalizeRussianForSearch
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SearchDaoTest {
    private val directExecutor = Executor { command -> command.run() }
    private lateinit var database: RusMorphDatabase
    private lateinit var dao: SearchDao

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RusMorphDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        dao = database.searchDao()
        val entries = listOf(
            entry("write", "писа́ть", "писать", "写", 1, "动词", phonetic = "с-ш"),
            entry("car", "автомоби́ль", "автомобиль", "汽车", 2, "名词", gender = "阳性"),
            entry("friend", "друг", "друг", "朋友", 2, "名词", gender = "阳性", phonetic = "г-ж"),
            entry("want", "хоте́ть", "хотеть", "想要", 3, "动词", conjugation = "特殊变位法"),
            entry("can", "мочь", "мочь", "能够", 3, "动词", phonetic = "г-ж"),
            entry("love", "люби́ть", "любить", "爱", 3, "动词", phonetic = "б-бл"),
            entry("eat", "есть", "есть", "吃", 4, "动词"),
            entry("six", "шесть", "шесть", "六", 4, "数词"),
            entry("friendship", "дружба", "дружба", "友谊", 2, "名词", gender = "阴性", phonetic = "ж-жб"),
            entry("other", "друго́й", "другой", "另一个", 2, "形容词"),
        )
        dao.insertEntriesForTest(entries.map { it.first })
        dao.insertSearchFormsForTest(entries.flatMap { it.second })
        dao.insertPartsOfSpeechForTest(entries.map { it.third })
        dao.insertPartsOfSpeechForTest(listOf(EntryPartOfSpeechEntity("other", "代词")))
        database.dataImportDao().insertAnnotations(
            listOf(
                EntryAnnotationEntity("friend", "语音交替", "г-ж", "г-ж"),
                EntryAnnotationEntity("friend", "补充注释", "特殊交替", "特殊交替"),
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun searchesRequiredRussianWordsAndRecordedForms() = runBlocking {
        listOf("писать", "автомобиль", "друг", "хотеть", "мочь", "любить").forEach { query ->
            val result = search(query)
            assertTrue("Expected result for $query", result.isNotEmpty())
            assertEquals(query, result.first().entry.normalizedLemma)
        }
        assertEquals("write", search("пишу").first().entry.id)
        assertTrue(search("несуществующее").isEmpty())
    }

    @Test
    fun accentChinesePrefixRankingAndFiltersWork() = runBlocking {
        assertEquals("car", search("автомоби́ль").first().entry.id)
        assertEquals("car", search("автомобиль").first().entry.id)
        assertEquals("car", search("汽车").first().entry.id)
        assertEquals("friend", search("дру").first().entry.id)
        assertEquals("friend", search("друг").first().entry.id)

        val filtered = dao.search("д", "д", "名词", 2, 50)
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { item -> item.entry.lesson == 2 })
        assertTrue(filtered.all { item -> item.partsOfSpeech.any { it.partOfSpeech == "名词" } })
    }

    @Test
    fun estDoesNotMatchShestBySubstring() = runBlocking {
        val result = search("есть")
        assertEquals(listOf("eat"), result.map { it.entry.id })
        assertFalse(result.any { it.entry.normalizedLemma == "шесть" })
    }

    @Test
    fun searchesStructuredAndFreeformAnnotations() = runBlocking {
        assertEquals("friend", search("г-ж").first().entry.id)
        assertEquals("friend", search("特殊交替").first().entry.id)
        val details = dao.observeEntry("friend").first()!!
        assertTrue(details.annotations.any { it.fieldName == "补充注释" && it.value == "特殊交替" })
    }

    @Test
    fun associatedKnowledgeIsReturnedAndMissingAssociationIsEmpty() = runBlocking {
        val knowledge = KnowledgeChunkEntity(
            id = "iot", title = "j 音组", category = "IOTATION", keywordsJson = "[]",
            content = "明确说明", examplesJson = "[]", sourceDocument = "test.docx",
            sectionPathJson = "[]",
        )
        val importDao = database.dataImportDao()
        importDao.insertKnowledgeChunks(listOf(knowledge))
        importDao.insertEntryKnowledgeCrossRefs(listOf(EntryKnowledgeCrossRef("write", "iot")))

        assertEquals(listOf("iot"), dao.observeEntry("write").first()!!.knowledgeChunks.map { it.id })
        assertTrue(dao.observeEntry("car").first()!!.knowledgeChunks.isEmpty())
    }

    @Test
    fun browseIsOrderedFilteredLimitedAndNeverDuplicatesMultiPartEntries() = runBlocking {
        val all = dao.browseEntries(null, null, 30)
        assertEquals(10, all.size)
        assertEquals(all.size, all.map { it.entry.id }.distinct().size)
        assertEquals("write", all.first().entry.id)

        val lessonTwo = dao.browseEntries(null, 2, 50)
        assertTrue(lessonTwo.isNotEmpty())
        assertTrue(lessonTwo.all { it.entry.lesson == 2 })

        val pronouns = dao.browseEntries("代词", null, 50)
        assertEquals(listOf("other"), pronouns.map { it.entry.id })
        assertEquals(10, dao.entryCount())
    }

    @Test
    fun morphologyFiltersAreIntersectedInsideRoom() = runBlocking {
        val result = dao.browseEntriesByMorphology(
            partOfSpeech = "名词",
            lesson = null,
            genders = listOf("阳性"),
            genderCount = 1,
            declensionClasses = emptyList(),
            declensionClassCount = 0,
            endingTypes = emptyList(),
            endingTypeCount = 0,
            aspects = emptyList(),
            aspectCount = 0,
            conjugationClasses = emptyList(),
            conjugationClassCount = 0,
            phoneticAlternations = emptyList(),
            phoneticAlternationCount = 0,
            hasPhoneticAlternation = true,
            hasPluralStressPattern = null,
            limit = 20,
        )
        assertEquals(listOf("friend"), result.map { it.entry.id })
    }

    private suspend fun search(query: String) = dao.search(
        normalizedQuery = normalizeRussianForSearch(query),
        rawQuery = query.trim(),
        partOfSpeech = null,
        lesson = null,
        limit = 50,
    )

    @Test
    fun courseEntriesPrioritizedOverUniversalEntriesWhenRankTies() = runBlocking {
        val universal = LexiconEntryEntity(
            id = "lex_or_friend_extended",
            lesson = null,
            sequence = null,
            displayForm = "друг",
            lemma = "друг",
            normalizedLemma = "друг",
            chineseMeaning = "[英] friend",
            gender = "阳性",
            declensionClass = null,
            endingType = null,
            pluralStressPattern = null,
            aspect = null,
            conjugationClass = null,
            phoneticAlternation = null,
            sourceWorkbook = "openrussian.org",
            sourceSheet = "nouns",
            sourceRow = 1,
        )
        dao.insertEntriesForTest(listOf(universal))
        dao.insertSearchFormsForTest(listOf(EntrySearchFormEntity(universal.id, "друг")))
        dao.insertPartsOfSpeechForTest(listOf(EntryPartOfSpeechEntity(universal.id, "名词")))

        val result = search("друг")
        assertTrue(result.size >= 2)
        assertEquals("friend", result[0].entry.id)
        assertEquals(2, result[0].entry.lesson)
        assertEquals("lex_or_friend_extended", result[1].entry.id)
        assertEquals(null, result[1].entry.lesson)
    }

    private fun entry(
        id: String,
        display: String,
        lemma: String,
        chinese: String,
        lesson: Int,
        partOfSpeech: String,
        gender: String? = null,
        phonetic: String? = null,
        conjugation: String? = null,
    ): Triple<LexiconEntryEntity, List<EntrySearchFormEntity>, EntryPartOfSpeechEntity> {
        val entity = LexiconEntryEntity(
            id = id,
            lesson = lesson,
            sequence = lesson,
            displayForm = display,
            lemma = lemma,
            normalizedLemma = lemma,
            chineseMeaning = chinese,
            gender = gender,
            declensionClass = null,
            endingType = null,
            pluralStressPattern = null,
            aspect = null,
            conjugationClass = conjugation,
            phoneticAlternation = phonetic,
            sourceWorkbook = "test.xlsx",
            sourceSheet = "词表",
            sourceRow = lesson,
        )
        val forms = buildList {
            add(EntrySearchFormEntity(id, lemma))
            if (id == "write") add(EntrySearchFormEntity(id, "пишу"))
        }
        return Triple(entity, forms, EntryPartOfSpeechEntity(id, partOfSpeech))
    }
}
