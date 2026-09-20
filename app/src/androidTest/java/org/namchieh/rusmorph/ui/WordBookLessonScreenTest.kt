package org.namchieh.rusmorph.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.namchieh.rusmorph.domain.learning.Lesson
import org.namchieh.rusmorph.ui.learning.Loadable
import org.namchieh.rusmorph.ui.screen.learning.LessonDetailScreen

class WordBookLessonScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun empty_wordbook_lesson_does_not_invent_units() {
        compose.setContent {
            MaterialTheme {
                LessonDetailScreen(
                    Loadable.Content(Lesson("same", "second-book", 1, "УРОК 1", "第一课", emptyList())),
                    { _, _ -> error("Empty lesson must not navigate") }, {}, {},
                )
            }
        }
        compose.onNodeWithText("УРОК 1").assertIsDisplayed()
    }
}
