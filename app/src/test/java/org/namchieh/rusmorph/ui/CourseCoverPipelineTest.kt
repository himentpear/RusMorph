package org.namchieh.rusmorph.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.namchieh.rusmorph.data.local.LexiconEntryWithDetails
import org.namchieh.rusmorph.data.repository.CourseRepository
import org.namchieh.rusmorph.data.repository.WordBookRepository
import org.namchieh.rusmorph.domain.learning.Dialogue
import org.namchieh.rusmorph.domain.learning.Lesson
import org.namchieh.rusmorph.domain.learning.TextContent
import org.namchieh.rusmorph.domain.learning.WordBook
import org.namchieh.rusmorph.ui.components.resolveCoverResourceId
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CourseCoverPipelineTest {

    private val fakeWordBookRepository = object : WordBookRepository {
        override suspend fun wordBooks(): List<WordBook> = listOf(
            WordBook(
                id = "university-russian-1",
                title = "大学俄语 1",
                subtitle = "Русский язык · Том 1",
                lessonCount = 18,
                coverResourceName = "cover_university_russian_1",
            ),
            WordBook(
                id = "university-russian-2",
                title = "大学俄语 2",
                subtitle = "Русский язык · Том 2",
                lessonCount = 12,
                coverResourceName = "cover_university_russian_2",
            ),
        )
        override suspend fun wordBook(wordBookId: String): WordBook? = wordBooks().firstOrNull { it.id == wordBookId }
        override suspend fun lessons(wordBookId: String): List<Lesson> = emptyList()
        override suspend fun lesson(wordBookId: String, lessonId: String): Lesson? = null
        override suspend fun lessonWords(wordBookId: String, lessonId: String): List<LexiconEntryWithDetails> = emptyList()
        override suspend fun lessonDialogues(wordBookId: String, lessonId: String): List<Dialogue> = emptyList()
        override suspend fun lessonTexts(wordBookId: String, lessonId: String): List<TextContent> = emptyList()
        override suspend fun dialogue(dialogueId: String): Dialogue? = null
        override suspend fun text(textId: String): TextContent? = null
    }

    @Test
    fun course_repository_preserves_cover_resource_names_from_wordbooks() = runTest {
        val courseRepo = CourseRepository(fakeWordBookRepository)
        val courses = courseRepo.courses()

        val c1 = courses.first { it.id == "university-russian-1" }
        val c2 = courses.first { it.id == "university-russian-2" }

        assertEquals("cover_university_russian_1", c1.coverResourceName)
        assertEquals("cover_university_russian_2", c2.coverResourceName)
    }

    @Test
    fun cover_resolver_resolves_existing_and_gracefully_handles_missing_assets() {
        val context: Context = ApplicationProvider.getApplicationContext()

        // 大学俄语 2 has actual drawable file in res/drawable-nodpi/cover_university_russian_2.png
        val c2ResId = resolveCoverResourceId(context, "cover_university_russian_2")
        assertNotNull("cover_university_russian_2 should resolve to drawable resource id", c2ResId)

        // 大学俄语 1 is currently missing from res/drawable*, should safely return null for paper placeholder
        val c1ResId = resolveCoverResourceId(context, "cover_university_russian_1")
        assertNull("cover_university_russian_1 should return null to trigger Werus paper placeholder", c1ResId)

        // Non-existent or null
        assertNull(resolveCoverResourceId(context, null))
        assertNull(resolveCoverResourceId(context, ""))
        assertNull(resolveCoverResourceId(context, "invalid_cover_name"))
    }
}
