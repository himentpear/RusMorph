package org.namchieh.rusmorph.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.namchieh.rusmorph.data.local.ParagraphEntity
import org.namchieh.rusmorph.data.local.ReadingSectionEntity
import org.namchieh.rusmorph.data.local.RusMorphDatabase
import org.namchieh.rusmorph.data.local.TextbookEntity
import org.namchieh.rusmorph.data.local.TextbookLessonEntity
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TextbookRepositoryTest {
    private lateinit var database: RusMorphDatabase
    private lateinit var repository: TextbookRepository

    @Before fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RusMorphDatabase::class.java).build()
        database.textbookDao().apply {
            upsertTextbooks(listOf(TextbookEntity("book", "大学俄语 1", "ru", "1", 1)))
            upsertLessons(listOf(TextbookLessonEntity("lesson", "book", 1, "Урок 1")))
            upsertSections(listOf(ReadingSectionEntity("section", "lesson", 1, "READING", null)))
            upsertParagraphs(listOf(ParagraphEntity("paragraph", "section", 1, "Э́то ма́ма.")))
        }
        repository = TextbookRepository(database.textbookDao())
    }

    @After fun tearDown() = database.close()

    @Test fun loads_lesson_and_preserves_paragraph_content() = runTest {
        val content = repository.getLessonContent("lesson")
        assertNotNull(content)
        assertEquals("Э́то ма́ма.", content!!.sections.single().paragraphs.single().content)
    }
}
