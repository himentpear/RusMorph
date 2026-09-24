package org.namchieh.rusmorph.data.repository

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
import org.namchieh.rusmorph.data.local.RusMorphDatabase
import org.namchieh.rusmorph.data.local.seedGraph
import org.namchieh.rusmorph.data.remote.VariantQuestionResponseDto
import org.namchieh.rusmorph.domain.grammar.QuestionRunnerMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GrammarExamRepositoryTest {
    private lateinit var database: RusMorphDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RusMorphDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    @Test fun submitAttemptUpdatesMasteryFromRealEvidence() = runTest {
        seedGraph(database.dataImportDao())
        val repository = ExamPracticeRepository(database)
        assertEquals(true, repository.submitAttempt("Q1", "A", QuestionRunnerMode.PRACTICE, now = 10).correct)
        val mastery = database.grammarExamDao().observeGrammarMastery("G1").first()
        assertEquals(1.0, mastery!!.mastery, 0.0001)
        assertEquals(1, mastery.realQuestionAttempts)
        assertEquals(0, mastery.aiQuestionAttempts)
    }

    @Test fun validAiVariantPersistsQuestionLinkAndLineage() = runTest {
        seedGraph(database.dataImportDao())
        val response = VariantQuestionResponseDto(
            "Variant ___", mapOf("A" to "a", "B" to "b", "C" to "c", "D" to "d"), "C", "Analysis", "G1",
        )
        val repository = ExamPracticeRepository(database)
        val result = repository.generateAndPersistVariant(
            "Q1", "G1",
            FakeAgentRepository(variantResponse = VariantQuestionResult.Success(response)),
            now = 20,
        ) as org.namchieh.rusmorph.domain.grammar.VariantGenerationOutcome.Success
        assertEquals("AI_VARIANT", result.question.sourceType)
        assertNotNull(database.grammarExamDao().getLineage(result.question.questionId))
        assertEquals(listOf("G1"), database.grammarExamDao().getGrammarPointsForQuestion(result.question.questionId).map { it.pointId })
    }
}
