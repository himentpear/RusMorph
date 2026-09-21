package org.namchieh.rusmorph.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.namchieh.rusmorph.domain.textbook.KnowledgeType
import org.namchieh.rusmorph.domain.textbook.LessonSentence
import org.namchieh.rusmorph.domain.textbook.LessonTextContent
import org.namchieh.rusmorph.domain.textbook.SentenceKnowledge
import org.namchieh.rusmorph.domain.textbook.Textbook
import org.namchieh.rusmorph.domain.textbook.TextbookBlock
import org.namchieh.rusmorph.domain.textbook.TextbookBlockType
import org.namchieh.rusmorph.domain.textbook.TextbookLesson
import org.namchieh.rusmorph.ui.learning.Loadable
import org.namchieh.rusmorph.ui.screen.learning.LessonTextbookScreen
import org.namchieh.rusmorph.ui.theme.RusMorphTheme

class KnowledgeUiTest {
    @get:Rule val compose = createComposeRule()

    private val annotated = LessonSentence("annotated", "ur2-lesson-1", "block", 1, "Меня́ зову́т Анто́н.", "Меня́ зову́т Анто́н.")
    private val empty = LessonSentence("empty", "ur2-lesson-1", "block", 2, "Предложе́ние без аннота́ций.", "Предложе́ние без аннота́ций.")
    private val content = LessonTextContent(
        Textbook("book", "大学俄语 2", "ru", "2", 2),
        TextbookLesson("ur2-lesson-1", "book", 1, "Урок 1"),
        listOf(TextbookBlock("block", "ur2-lesson-1", 1, TextbookBlockType.READING, null, listOf(annotated, empty))),
    )
    private val word = SentenceKnowledge("word", annotated.id, KnowledgeType.WORD, "Анто́н", "安东", null, null, 13, 19, "OK")

    @Test fun sentence_click_shows_sheet_and_word_action_uses_wordbook_lookup() {
        var selected by mutableStateOf<LessonSentence?>(null)
        var lookup: String? = null
        compose.setContent {
            RusMorphTheme {
                LessonTextbookScreen(
                    state = Loadable.Content(content), selectedSentence = selected,
                    knowledge = selected?.let { Loadable.Content(if (it.id == annotated.id) listOf(word) else emptyList()) },
                    onSentence = { selected = it }, onDismissKnowledge = { selected = null },
                    onWordLookup = { lookup = it }, onBack = {},
                )
            }
        }

        compose.onNodeWithText(annotated.text).performClick()
        compose.onNodeWithText("知识卡片").assertIsDisplayed()
        compose.onNodeWithText("在词库中查找").performClick()
        compose.runOnIdle { assertEquals("Анто́н", lookup) }
    }

    @Test fun sentence_without_knowledge_still_renders_and_shows_empty_state() {
        var selected by mutableStateOf<LessonSentence?>(null)
        compose.setContent {
            RusMorphTheme {
                LessonTextbookScreen(
                    state = Loadable.Content(content), selectedSentence = selected,
                    knowledge = selected?.let { Loadable.Content(emptyList()) },
                    onSentence = { selected = it }, onDismissKnowledge = { selected = null }, onBack = {},
                )
            }
        }

        compose.onNodeWithText(empty.text).assertIsDisplayed().performClick()
        compose.onNodeWithText("暂无知识标注").assertIsDisplayed()
    }
}
