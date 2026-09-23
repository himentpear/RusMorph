package org.namchieh.rusmorph.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GrammarExamDaoTest {
    private lateinit var database: RusMorphDatabase
    private lateinit var dao: GrammarExamDao

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RusMorphDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = database.grammarExamDao()
    }

    @After fun tearDown() = database.close()

    @Test fun grammarAndQuestionQueriesSupportMultipleLinksAndAttempts() = runTest {
        seedGraph(database.dataImportDao())
        assertEquals(listOf("Q1", "Q2"), dao.getQuestionsForGrammarPoint("G1").map { it.questionId })
        assertEquals(listOf("G1", "G2"), dao.getGrammarPointsForQuestion("Q1").map { it.pointId })
        dao.insertAttempt(QuestionAttemptEntity("A1", "Q1", "B", false, "PRACTICE", 100, 1))
        assertEquals(1, dao.getAttempts("Q1").size)
        assertEquals(listOf("Q1"), dao.observeWrongQuestions().first().map { it.questionId })
        assertNotNull(dao.getAttemptEvidenceForGrammarPoint("G2").single())
    }
}

internal suspend fun seedGraph(importDao: DataImportDao) {
    importDao.upsertGrammarPoints(listOf(
        GrammarPointEntity("G1", "语法1", "Grammar 1", "Explanation", null, null, null, null, 1, 1),
        GrammarPointEntity("G2", "语法2", "Grammar 2", "Explanation", null, null, null, null, 2, 1),
    ))
    importDao.upsertQuestions(listOf(
        QuestionEntity("Q1", 1, "TEM4_REAL", 2024, "2024", "Stem 1", "a", "b", "c", "d", "A", "Analysis", null, null),
        QuestionEntity("Q2", 2, "TEM4_REAL", 2024, "2024", "Stem 2", "a", "b", "c", "d", "B", "Analysis", null, null),
    ))
    importDao.upsertGrammarQuestionLinks(listOf(
        GrammarQuestionCrossRefEntity("Q1", "G1", "UNVERIFIED", 1.0, 0.6, "SOURCE_SPREADSHEET", false),
        GrammarQuestionCrossRefEntity("Q1", "G2", "UNVERIFIED", 1.0, 0.6, "SOURCE_SPREADSHEET", false),
        GrammarQuestionCrossRefEntity("Q2", "G1", "UNVERIFIED", 1.0, 0.6, "SOURCE_SPREADSHEET", false),
    ))
}
