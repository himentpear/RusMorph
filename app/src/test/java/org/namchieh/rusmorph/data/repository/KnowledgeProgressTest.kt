package org.namchieh.rusmorph.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.namchieh.rusmorph.data.local.KnowledgeProgressDao
import org.namchieh.rusmorph.data.local.RusMorphDatabase
import org.namchieh.rusmorph.data.local.SentenceKnowledgeEntity
import org.namchieh.rusmorph.data.local.TextbookKnowledgeDao
import org.namchieh.rusmorph.domain.textbook.KnowledgeProgressStatus
import org.namchieh.rusmorph.domain.textbook.KnowledgeType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class KnowledgeProgressTest {
    private lateinit var database: RusMorphDatabase
    private lateinit var knowledgeDao: TextbookKnowledgeDao
    private lateinit var progressDao: KnowledgeProgressDao
    private lateinit var repository: TextbookKnowledgeRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val direct = Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, RusMorphDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(direct)
            .setTransactionExecutor(direct)
            .build()
        knowledgeDao = database.textbookKnowledgeDao()
        progressDao = database.knowledgeProgressDao()
        repository = TextbookKnowledgeRepository(knowledgeDao, progressDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun knowledge_not_in_ebbinghaus_review_by_default() = runTest {
        val knowledgeEntity = SentenceKnowledgeEntity(
            id = "test-phrase-1",
            sentenceId = "sentence-1",
            type = KnowledgeType.PHRASE.name,
            text = "С детства",
            label = "с + 第二格",
            explanation = "表示时间起点",
            example = "С детства я интересовался языками.",
            start = 0,
            end = 9,
            status = "OK",
            knowledgeVersion = 1,
            generatedBy = "human",
            reviewStatus = "OK",
        )
        knowledgeDao.upsertKnowledge(listOf(knowledgeEntity))

        // Ensure initially no progress exists
        val initialProgress = repository.getProgress("test-phrase-1")
        assertNull(initialProgress)

        // Ensure Ebbinghaus review table does not contain this knowledge
        val reviewItem = database.learningDao().getReviewItemById("test-phrase-1")
        assertNull(reviewItem)
    }

    @Test
    fun recordSeen_creates_and_increments_seen_progress_without_ebbinghaus() = runTest {
        val firstSeen = repository.recordSeen("test-phrase-1", "lesson-1")
        assertEquals(KnowledgeProgressStatus.SEEN, firstSeen.status)
        assertEquals(1, firstSeen.seenCount)
        assertEquals("lesson-1", firstSeen.lessonId)

        val secondSeen = repository.recordSeen("test-phrase-1", "lesson-1")
        assertEquals(KnowledgeProgressStatus.SEEN, secondSeen.status)
        assertEquals(2, secondSeen.seenCount)

        // Still no entry in Ebbinghaus
        val reviewItem = database.learningDao().getReviewItemById("test-phrase-1")
        assertNull(reviewItem)
    }

    @Test
    fun progressive_status_transitions_seen_understood_practiced_mastered() = runTest {
        val kId = "test-grammar-1"
        val lessonId = "lesson-1"

        // 1. Initial seen
        val step1 = repository.recordSeen(kId, lessonId)
        assertEquals(KnowledgeProgressStatus.SEEN, step1.status)
        assertNull(step1.understoodAt)
        assertEquals(0, step1.practicedCount)
        assertNull(step1.masteredAt)

        // 2. Mark understood
        val step2 = repository.updateStatus(kId, KnowledgeProgressStatus.UNDERSTOOD, lessonId)
        assertEquals(KnowledgeProgressStatus.UNDERSTOOD, step2.status)
        assertNotNull(step2.understoodAt)

        // 3. Mark practiced
        val step3 = repository.updateStatus(kId, KnowledgeProgressStatus.PRACTICED, lessonId)
        assertEquals(KnowledgeProgressStatus.PRACTICED, step3.status)
        assertEquals(1, step3.practicedCount)
        assertNotNull(step3.understoodAt)

        // 4. Mark practiced again (e.g. additional practice)
        val step3b = repository.updateStatus(kId, KnowledgeProgressStatus.PRACTICED, lessonId)
        assertEquals(2, step3b.practicedCount)

        // 5. Mark mastered
        val step4 = repository.updateStatus(kId, KnowledgeProgressStatus.MASTERED, lessonId)
        assertEquals(KnowledgeProgressStatus.MASTERED, step4.status)
        assertNotNull(step4.masteredAt)
        assertNotNull(step4.understoodAt)
        assertEquals(2, step4.practicedCount)
    }

    @Test
    fun batch_progress_for_lesson_queries_all_statuses() = runTest {
        repository.recordSeen("k1", "lesson-1")
        repository.updateStatus("k2", KnowledgeProgressStatus.UNDERSTOOD, "lesson-1")
        repository.updateStatus("k3", KnowledgeProgressStatus.MASTERED, "lesson-1")
        repository.updateStatus("other-lesson-k", KnowledgeProgressStatus.MASTERED, "lesson-2")

        val map = repository.getProgressForLesson("lesson-1")
        assertEquals(3, map.size)
        assertEquals(KnowledgeProgressStatus.SEEN, map["k1"]?.status)
        assertEquals(KnowledgeProgressStatus.UNDERSTOOD, map["k2"]?.status)
        assertEquals(KnowledgeProgressStatus.MASTERED, map["k3"]?.status)
        assertNull(map["other-lesson-k"])
    }

    @Test
    fun batch_knowledge_for_sentences_returns_ordered_results() = runTest {
        val s1k1 = SentenceKnowledgeEntity("k1", "s1", KnowledgeType.PHRASE.name, "С детства", "起点", null, null, 0, 9, "OK", 1, null, null)
        val s1k2 = SentenceKnowledgeEntity("k2", "s1", KnowledgeType.WORD.name, "языками", "语言", null, null, 26, 33, "OK", 1, null, null)
        val s2k1 = SentenceKnowledgeEntity("k3", "s2", KnowledgeType.GRAMMAR.name, "в + 6", "地点", null, null, 0, 5, "OK", 1, null, null)
        knowledgeDao.upsertKnowledge(listOf(s1k1, s1k2, s2k1))

        val results = repository.getKnowledgeForSentences(listOf("s1", "s2"))
        assertEquals(3, results.size)
        assertEquals(listOf("k1", "k2", "k3"), results.map { it.id })
    }
}
