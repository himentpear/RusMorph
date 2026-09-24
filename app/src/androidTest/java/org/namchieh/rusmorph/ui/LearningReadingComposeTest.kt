package org.namchieh.rusmorph.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.namchieh.rusmorph.data.repository.LearningStats
import org.namchieh.rusmorph.domain.learning.Course
import org.namchieh.rusmorph.domain.learning.TextContent
import org.namchieh.rusmorph.domain.learning.TextParagraph
import org.namchieh.rusmorph.domain.learning.TextSentence
import org.namchieh.rusmorph.ui.learning.Loadable
import org.namchieh.rusmorph.ui.screen.learning.HomeScreen
import org.namchieh.rusmorph.ui.screen.learning.LearningScreen
import org.namchieh.rusmorph.ui.screen.learning.text.TextDetailScreen
import org.namchieh.rusmorph.ui.theme.RusMorphTheme

class LearningReadingComposeTest {
    @get:Rule val compose = createComposeRule()

    private val courses = listOf(
        Course("book-1", "大学俄语 1", "Русский язык · Том 1", "", 18),
        Course("book-2", "大学俄语 2", "Русский язык · Том 2", "", 12),
    )

    @Test fun homeShowsOnlyTheActiveCourseAndNoCourseSwitcher() {
        compose.setContent {
            RusMorphTheme {
                HomeScreen(
                    coursesState = Loadable.Content(courses), stats = LearningStats(), progress = emptyList(),
                    selectedCourseId = "book-1", onCourse = {}, onContinue = { _, _ -> }, onDictionary = {},
                    onPronunciation = {}, onReview = {}, onBottom = {}, onAI = {},
                )
            }
        }
        compose.onNodeWithText("大学俄语 1").assertIsDisplayed()
        compose.onAllNodesWithText("大学俄语 2").assertCountEquals(0)
    }

    @Test fun myTextbooksSelectsBeforeOpeningDirectory() {
        val calls = mutableListOf<String>()
        compose.setContent {
            RusMorphTheme {
                LearningScreen(
                    coursesState = Loadable.Content(courses), activeCourseId = "book-1", progress = emptyList(),
                    onSelectCourse = { calls += "select:$it" }, onCourse = { calls += "open:$it" },
                    onPronunciation = {}, onAiCommands = {}, onReview = {}, onBottom = {},
                )
            }
        }
        compose.onNodeWithText("我的教材").assertIsDisplayed()
        compose.onNode(hasClickAction() and hasAnyDescendant(hasText("大学俄语 2")), useUnmergedTree = true)
            .performScrollTo().performClick()
        assertEquals(listOf("select:book-2", "open:book-2"), calls)
    }

    @Test fun sentenceAnalysisIsAccordionAndTranslationCanBeToggled() {
        val first = TextSentence("s1", 1, "Мы живём в Москве.", translation = "我们住在莫斯科。")
        val second = TextSentence("s2", 2, "Каждый день мы учимся.", translation = "我们每天学习。")
        val text = TextContent("text-1", "ur1-lesson-3", "ТЕКСТ", null, listOf(TextParagraph("p1", 1, listOf(first, second))))
        compose.setContent { RusMorphTheme { TextDetailScreen(Loadable.Content(text), {}, {}) } }

        compose.onAllNodesWithText("我们住在莫斯科。").assertCountEquals(0)
        compose.onNodeWithContentDescription("显示逐句翻译").performClick()
        compose.onNodeWithText("我们住在莫斯科。").assertIsDisplayed()
        compose.onNodeWithContentDescription("隐藏逐句翻译").performClick()
        compose.onAllNodesWithText("我们住在莫斯科。").assertCountEquals(0)

        compose.onNodeWithContentDescription("俄语句子：Мы живём в Москве.").performClick()
        compose.onNode(hasContentDescription("俄语句子：Мы живём в Москве.") and hasStateDescription("已展开")).assertIsDisplayed()
        compose.onAllNodesWithText("暂无语法标注").assertCountEquals(1)
        compose.onNodeWithContentDescription("俄语句子：Каждый день мы учимся.").performClick()
        compose.onNode(hasContentDescription("俄语句子：Мы живём в Москве.") and hasStateDescription("已收起")).assertIsDisplayed()
        compose.onAllNodesWithText("暂无语法标注").assertCountEquals(1)
    }
}
