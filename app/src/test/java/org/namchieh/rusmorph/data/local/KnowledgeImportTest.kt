package org.namchieh.rusmorph.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.Executor
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.namchieh.rusmorph.test.openKnowledgeFixture
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KnowledgeImportTest {
    private lateinit var context: Context
    private lateinit var database: RusMorphDatabase

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val direct = Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, RusMorphDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor(direct).setTransactionExecutor(direct).build()
    }

    @After fun tearDown() = database.close()

    @Test fun generated_assets_import_into_room_with_exact_type_counts() = runTest {
        TextbookAssetImporter(context, database, "knowledge_pipeline") { path ->
            openKnowledgeFixture(path)
        }.importIfNeeded()

        val textbookDao = database.textbookDao()
        val knowledgeDao = database.textbookKnowledgeDao()
        val sentences = textbookDao.blocks("ur2-lesson-1").flatMap { textbookDao.sentences(it.id) }
        val imported = sentences.flatMap { knowledgeDao.knowledge(it.id) }

        assertEquals(5, imported.count { it.type == "PHRASE" })
        assertEquals(3, imported.count { it.type == "GRAMMAR" })
        assertEquals(2, imported.count { it.type == "PATTERN" })
        assertEquals(2, imported.count { it.type == "WORD" })
        assertEquals(0, knowledgeDao.knowledge("ur2-lesson-1-b2-s4").size)
    }
}
