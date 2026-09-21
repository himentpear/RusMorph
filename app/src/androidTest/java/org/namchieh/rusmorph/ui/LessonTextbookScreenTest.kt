package org.namchieh.rusmorph.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Rule
import org.junit.Test
import org.namchieh.rusmorph.domain.textbook.LessonTextContent
import org.namchieh.rusmorph.domain.textbook.KnowledgeType
import org.namchieh.rusmorph.domain.textbook.LessonSentence
import org.namchieh.rusmorph.domain.textbook.SentenceKnowledge
import org.namchieh.rusmorph.domain.textbook.TextbookBlock
import org.namchieh.rusmorph.domain.textbook.TextbookBlockType
import org.namchieh.rusmorph.domain.textbook.Textbook
import org.namchieh.rusmorph.domain.textbook.TextbookLesson
import org.namchieh.rusmorph.ui.learning.Loadable
import org.namchieh.rusmorph.ui.screen.learning.LessonTextbookScreen
import org.namchieh.rusmorph.ui.theme.RusMorphTheme

class LessonTextbookScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sentence_click_opens_knowledge_panel() {
        val sentence = LessonSentence("sentence", "lesson", "block", 1, "Э́то ма́ма.", "Э́то ма́ма.")
        val content = LessonTextContent(
            Textbook("book", "大学俄语 1", "ru", "1", 1),
            TextbookLesson("lesson", "book", 1, "Урок 1"),
            listOf(TextbookBlock("block", "lesson", 1, TextbookBlockType.READING, null, listOf(sentence))),
        )
        val item = SentenceKnowledge("knowledge", "sentence", KnowledgeType.PATTERN, "Э́то + существительное", "指示结构", "用于指认人物或事物", null, 0, 4, "OK")
        var selected by mutableStateOf<LessonSentence?>(null)
        compose.setContent {
            RusMorphTheme {
                LessonTextbookScreen(
                    state = Loadable.Content(content), selectedSentence = selected,
                    knowledge = selected?.let { Loadable.Content(listOf(item)) },
                    onSentence = { selected = it }, onDismissKnowledge = { selected = null }, onBack = {},
                )
            }
        }
        compose.onNodeWithText("Э́то ма́ма.").performClick()
        compose.onNodeWithText("知识卡片").assertIsDisplayed()
        compose.onNodeWithText("Э́то + существительное").assertIsDisplayed()
    }
}
