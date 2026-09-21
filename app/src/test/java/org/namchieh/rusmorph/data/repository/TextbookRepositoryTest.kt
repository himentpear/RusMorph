package org.namchieh.rusmorph.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.namchieh.rusmorph.data.local.LessonSentenceEntity
import org.namchieh.rusmorph.data.local.SentenceKnowledgeEntity
import org.namchieh.rusmorph.data.local.TextbookBlockEntity
import org.namchieh.rusmorph.data.local.RusMorphDatabase
import org.namchieh.rusmorph.data.local.TextbookEntity
import org.namchieh.rusmorph.data.local.TextbookLessonEntity
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TextbookRepositoryTest {
    private lateinit var database: RusMorphDatabase
    private lateinit var repository: TextbookRepository
    private lateinit var knowledgeRepository: TextbookKnowledgeRepository

    @Before fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RusMorphDatabase::class.java).build()
        database.textbookDao().apply {
            upsertTextbooks(listOf(TextbookEntity("book", "大学俄语 1", "ru", "1", 1)))
            upsertLessons(listOf(TextbookLessonEntity("lesson", "book", 1, "Урок 1")))
            upsertBlocks(listOf(TextbookBlockEntity("block", "lesson", 1, "READING", null)))
            upsertSentences(listOf(LessonSentenceEntity("sentence", "lesson", "block", 1, "Э́то ма́ма.", "Э́то ма́ма.")))
        }
        database.textbookKnowledgeDao().upsertKnowledge(listOf(SentenceKnowledgeEntity(
            "knowledge", "sentence", "PATTERN", "Э́то + существительное", "指示结构", "用于指认人物或事物", null,
            0, 4, "OK", 1, "human", "OK",
        )))
        repository = TextbookRepository(database.textbookDao())
        knowledgeRepository = TextbookKnowledgeRepository(database.textbookKnowledgeDao())
    }

    @After fun tearDown() = database.close()

    @Test fun loads_lesson_sentence_and_knowledge_by_stable_id() = runTest {
        val content = repository.getLessonContent("lesson")
        assertNotNull(content)
        assertEquals("sentence", content!!.blocks.single().sentences.single().id)
        assertEquals("Э́то ма́ма.", content.blocks.single().sentences.single().sourceText)
        assertEquals("knowledge", knowledgeRepository.getSentenceKnowledge("sentence").single().id)
        assertEquals(1, knowledgeRepository.getPatterns("sentence").size)
        assertEquals(0, knowledgeRepository.getGrammar("sentence").size)
    }
}
