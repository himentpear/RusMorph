package org.namchieh.rusmorph.ui.grammar

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.namchieh.rusmorph.data.local.RusMorphDatabase
import org.namchieh.rusmorph.data.repository.ExamPracticeRepository
import org.namchieh.rusmorph.data.repository.FakeAgentRepository
import org.namchieh.rusmorph.data.repository.GrammarRepository
import org.namchieh.rusmorph.domain.grammar.QuestionEntryContextType
import org.namchieh.rusmorph.domain.grammar.QuestionRunnerMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuestionRunnerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var database: RusMorphDatabase

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RusMorphDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test fun answerAndPeekStateSurviveRecreation() {
        val handle = SavedStateHandle(mapOf("question_selected_answer" to "B", "question_submitted" to true, "question_correct" to false, "question_show_explanation" to true))
        val first = viewModel(handle, QuestionRunnerMode.PRACTICE)
        first.openGrammarPeek("G1")
        val recreated = viewModel(handle, QuestionRunnerMode.PRACTICE)
        assertEquals("B", recreated.state.value.selectedAnswer)
        assertEquals("G1", recreated.state.value.peekPointId)
        assertEquals(true, recreated.state.value.showExplanation)
    }

    @Test fun simulationModeBlocksGrammarPeek() {
        val handle = SavedStateHandle(mapOf("question_submitted" to true))
        val viewModel = viewModel(handle, QuestionRunnerMode.SIMULATION)
        viewModel.openGrammarPeek("G1")
        assertNull(viewModel.state.value.peekPointId)
    }

    private fun viewModel(handle: SavedStateHandle, mode: QuestionRunnerMode) = QuestionRunnerViewModel(
        handle,
        ExamPracticeRepository(database),
        GrammarRepository(database.grammarExamDao()),
        FakeAgentRepository(),
        "Q1",
        mode,
        QuestionEntryContextType.TEM4,
        null,
    )
}
