package org.namchieh.rusmorph.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.namchieh.rusmorph.data.local.*
import org.namchieh.rusmorph.domain.learning.*
import org.namchieh.rusmorph.ui.learning.withProgress
import org.namchieh.rusmorph.ui.navigation.Routes

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WordBookRepositoryTest {
    private lateinit var db: RusMorphDatabase
    private lateinit var context: Context
    private lateinit var assets: AssetWordBookDataSource
    private lateinit var room: RoomWordBookDataSource
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val executor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, RusMorphDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor(executor).setTransactionExecutor(executor).build()
        val search = SearchRepository(RoomSearchDataSource(db.searchDao()))
        assets = AssetWordBookDataSource(context, search)
        room = RoomWordBookDataSource(db.wordBookDao(), search)
    }
    @After fun close() = db.close()

    @Test fun offlineCatalogImportsExactContentAndIsIdempotent() = runTest {
        AssetDatabaseImporter(context, db).initialize()
        val books = assets.wordBooks()
        assertEquals(2, books.size)
        val book1 = books.first { it.id == "university-russian-1" }
        val book2 = books.first { it.id == "university-russian-2" }
        val lessons1 = assets.lessons(book1.id)
        val lessons2 = assets.lessons(book2.id)
        assertEquals("brick", book1.withProgress(lessons1, emptyList()).visualIdentity)
        assertEquals("cover_university_russian_1", book1.cover)
        assertEquals("cover_university_russian_2", book2.cover)
        assertEquals(18, lessons1.size)
        assertEquals(12, lessons2.size)
        assertEquals(982, lessons1.sumOf { assets.getLessonWords(book1.id, it.id).size })
        assertEquals(947, lessons2.sumOf { assets.getLessonWords(book2.id, it.id).size })
        assertEquals(17, lessons1.sumOf { assets.lessonDialogues(book1.id, it.id).size })
        assertEquals(0, lessons2.sumOf { assets.lessonDialogues(book2.id, it.id).size })
        assertTrue(lessons1.all { assets.lessonTexts(book1.id, it.id).isEmpty() && !it.content.texts })
        assertTrue(lessons2.all { assets.lessonTexts(book2.id, it.id).isEmpty() && !it.content.texts })
        assertEquals(982, lessons1.sumOf { room.getLessonWords(book1.id, it.id).size })
        assertEquals(947, lessons2.sumOf { room.getLessonWords(book2.id, it.id).size })
        WordBookAssetImporter(context, db).importIfNeeded()
        assertEquals(18, room.lessons(book1.id).size)
        assertEquals(12, room.lessons(book2.id).size)
        assertNull(assets.lesson("missing", lessons1.first().id))
        assertTrue(assets.lessonWords(book1.id, "fake-1").isEmpty())
        assertTrue(assets.lessonDialogues("missing", lessons1.first().id).isEmpty())
    }

    private suspend fun seed(book: String, version: Int = 1) {
        val dao = db.wordBookDao()
        dao.upsertWordBook(WordBookEntity(book, book, "", "", null, "IMPORTED", version, 0))
        dao.upsertLessons(listOf(WordBookLessonEntity("lesson-1", book, 1, null, null)))
        dao.upsertDialogues(listOf(WordBookDialogueEntity("dialogue-1", book, "lesson-1", book, null, 0)))
        dao.upsertDialogueLines(listOf(WordBookDialogueLineEntity("line-1", "dialogue-1", null, book, null, null, 0, book, "lesson-1")))
        dao.upsertTexts(listOf(WordBookTextEntity("text-1", book, "lesson-1", book, null, 0)))
        dao.upsertTextParagraphs(listOf(WordBookTextParagraphEntity("paragraph-1", "text-1", book, null, null, 0, book, "lesson-1")))
    }

    @Test fun repeatingLessonAndContentIdsStayIsolated() = runTest {
        seed("a"); seed("b")
        for (book in listOf("a", "b")) {
            db.openHelper.writableDatabase.execSQL("INSERT INTO lexicon_entries(id,lesson,sequence,displayForm,lemma,normalizedLemma,sourceWorkbook,sourceSheet,sourceRow,isRecommended) VALUES (?,1,1,'test','test','test','original','sheet',2,0)", arrayOf("entry-$book"))
            db.wordBookDao().upsertLessonWords(listOf(WordBookLessonWordEntity(book, "lesson-1", "entry-$book", 3, true)))
        }
        val repository = CompositeWordBookRepository(assets, room)
        for (book in listOf("a", "b")) {
            assertEquals(book, repository.getLesson(book, "lesson-1")!!.wordBookId)
            assertEquals(book, repository.dialogue(book, "lesson-1", "dialogue-1")!!.lines.single().text)
            assertEquals(book, repository.text(book, "lesson-1", "text-1")!!.paragraphs.single().text)
            assertEquals("entry-$book", repository.getLessonWords(book, "lesson-1").single().entryId)
            assertTrue(repository.getLessonWords(book, "lesson-1").single().isKey)
        }
        assertNull(repository.dialogue("dialogue-1")) // Ambiguous legacy lookup fails closed.
        assertNotEquals(Routes.dialogue("a", "lesson-1", "dialogue-1"), Routes.dialogue("b", "lesson-1", "dialogue-1"))
        assertNotEquals(scopedLearningId("ab", "c"), scopedLearningId("a", "bc"))
    }

    @Test fun newerRoomBookWinsWithoutMixingSupersededAssets() = runTest {
        val book = assets.wordBooks().first { it.id == "university-russian-1" }
        seed(book.id, book.version)
        val repository = CompositeWordBookRepository(assets, room)
        assertEquals(18, repository.lessons(book.id).size) // asset wins ties
        seed(book.id, book.version + 1)
        assertEquals(listOf("lesson-1"), repository.lessons(book.id).map { it.id })
        assertTrue(repository.lessonDialogues(book.id, "ur1-lesson-1").isEmpty())
        WordBookAssetImporter(context, db).importIfNeeded()
        assertEquals(book.version + 1, room.wordBook(book.id)!!.version)
    }

    @Test fun reopeningLessonPreservesProgressAndBookStatsStayScoped() = runTest {
        val learning = LearningRepository(db.learningDao())
        learning.saveProgress(LearningProgress("legacy", "a", "lesson-1", null, .5f, LearningStatus.IN_PROGRESS, 1))
        learning.openLesson("a", "lesson-1")
        learning.openLesson("b", "lesson-1")
        val rows = learning.observeProgress().first()
        assertEquals(2, rows.size)
        assertEquals(.5f, rows.single { it.wordBookId == "a" }.progress)
        assertEquals("legacy", rows.single { it.wordBookId == "a" }.sourceId)
        assertEquals(.5f, WordBook("a", "A", lessonCount = 1).withProgress(listOf(WordBookLesson("lesson-1", "a", 1, "", "")), rows).progress)
        assertEquals(0f, rows.single { it.wordBookId == "b" }.progress)
        for (book in listOf("a", "b")) learning.recordPronunciation(PronunciationSession(book, PronunciationSessionType.DIALOGUE_LINE, "same-line", book, "lesson-1", "test", 0, 1, 50.0))
        assertEquals(setOf("a", "b"), learning.observeDueReviews().first().map { it.wordBookId }.toSet())
    }
}
