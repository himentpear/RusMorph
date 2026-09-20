package org.namchieh.rusmorph.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.namchieh.rusmorph.domain.textbook.LessonTextContent
import org.namchieh.rusmorph.domain.textbook.ReadingParagraph
import org.namchieh.rusmorph.domain.textbook.ReadingSection
import org.namchieh.rusmorph.domain.textbook.ReadingSectionType
import org.namchieh.rusmorph.domain.textbook.Textbook
import org.namchieh.rusmorph.domain.textbook.TextbookLesson
import org.namchieh.rusmorph.ui.learning.Loadable
import org.namchieh.rusmorph.ui.screen.learning.LessonTextbookScreen
import org.namchieh.rusmorph.ui.theme.RusMorphTheme

class LessonTextbookScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun renders_verified_russian_paragraph() {
        val content = LessonTextContent(
            Textbook("book", "大学俄语 1", "ru", "1", 1),
            TextbookLesson("lesson", "book", 1, "Урок 1"),
            listOf(ReadingSection("section", "lesson", 1, ReadingSectionType.READING, null, listOf(ReadingParagraph("paragraph", "section", 1, "Э́то ма́ма.")))),
        )
        compose.setContent { RusMorphTheme { LessonTextbookScreen(Loadable.Content(content), {}) } }
        compose.onNodeWithText("Э́то ма́ма.").assertIsDisplayed()
    }
}
