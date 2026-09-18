package org.namchieh.rusmorph.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.namchieh.rusmorph.domain.learning.*
import org.namchieh.rusmorph.ui.learning.Loadable
import org.namchieh.rusmorph.ui.screen.learning.LessonDetailScreen

class WordBookLessonScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyModulesHaveNoFabricatedContent() {
        compose.setContent { MaterialTheme {
            LessonDetailScreen(Loadable.Content(WordBookLesson("same", "second-book", 1, "Lesson", "第一课")), { _, _ -> error("Empty module must not navigate") }, {}, {})
        } }
        compose.onNodeWithText("本课单词 · 尚未导入").assertIsDisplayed()
        compose.onNodeWithText("对话 · 尚未导入").assertIsDisplayed()
        compose.onNodeWithText("课文 · 尚未导入").assertIsDisplayed()
    }

    @Test fun navigationReceivesActualContentIdAndBookScope() {
        var selected: Triple<String, String, String?>? = null
        val unit = LearningUnit("opaque-id", "same", LearningUnitType.DIALOGUE, "ДИАЛОГ", "真实对话", 1, sourceId = "opaque-id")
        compose.setContent { MaterialTheme {
            LessonDetailScreen(Loadable.Content(WordBookLesson("same", "second-book", 1, "Lesson", "第一课", listOf(unit))),
                { lesson, content -> selected = Triple(lesson.wordBookId, lesson.id, content.sourceId) }, {}, {})
        } }
        compose.onNodeWithText("真实对话").performClick()
        compose.runOnIdle { assertEquals(Triple("second-book", "same", "opaque-id"), selected) }
    }
}
