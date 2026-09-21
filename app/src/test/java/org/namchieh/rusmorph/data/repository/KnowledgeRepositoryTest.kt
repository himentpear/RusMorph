package org.namchieh.rusmorph.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.Executor
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.namchieh.rusmorph.data.local.RusMorphDatabase
import org.namchieh.rusmorph.data.local.TextbookAssetImporter
import org.namchieh.rusmorph.domain.textbook.KnowledgeType
import org.namchieh.rusmorph.test.openKnowledgeFixture
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KnowledgeRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: RusMorphDatabase
    private lateinit var repository: TextbookKnowledgeRepository

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val direct = Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, RusMorphDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor(direct).setTransactionExecutor(direct).build()
        repository = TextbookKnowledgeRepository(database.textbookKnowledgeDao())
    }

    @After fun tearDown() = database.close()

    @Test fun repository_reads_imported_knowledge_and_preserves_empty_sentences() = runTest {
        TextbookAssetImporter(context, database, "knowledge_pipeline") { path ->
            openKnowledgeFixture(path)
        }.importIfNeeded()

        val first = repository.getSentenceKnowledge("ur2-lesson-1-b1-s1")
        assertEquals(setOf(KnowledgeType.PHRASE, KnowledgeType.PATTERN, KnowledgeType.WORD), first.map { it.type }.toSet())
        assertEquals("human", first.first { it.type == KnowledgeType.PHRASE }.generatedBy)
        assertEquals(1, repository.getGrammar("ur2-lesson-1-b2-s1").size)
        assertEquals(1, repository.getPatterns("ur2-lesson-1-b1-s1").size)
        assertEquals(2, repository.getPhrases("ur2-lesson-1-b2-s3").size)
        assertTrue(repository.getSentenceKnowledge("ur2-lesson-1-b2-s4").isEmpty())
    }
}
