package org.namchieh.rusmorph.data.repository

import org.namchieh.rusmorph.domain.learning.Course
import org.namchieh.rusmorph.domain.learning.Dialogue
import org.namchieh.rusmorph.domain.learning.Lesson

/** Compatibility façade: WordBookRepository is the sole owner of course content. */
class CourseRepository(private val books: WordBookRepository) {
    suspend fun courses(): List<Course> = books.wordBooks().map { book ->
        Course(book.id, book.title, book.subtitle, book.description.orEmpty(), book.lessonCount,
            book.completedLessonCount, book.progress, book.visualIdentity, book.lastLessonId)
    }

    suspend fun course(courseId: String): Course? = courses().firstOrNull { it.id == courseId }
    suspend fun lessons(courseId: String): List<Lesson> = books.lessons(courseId)
    suspend fun lesson(courseId: String, lessonId: String): Lesson? = books.lesson(courseId, lessonId)
    suspend fun vocabulary(lessonId: String) = books.lessonWords(wordBookIdFor(lessonId), lessonId)
    suspend fun dialogue(dialogueId: String): Dialogue? = books.dialogue(dialogueId)
    suspend fun dialogueForLesson(lessonId: String): Dialogue? =
        books.lessonDialogues(wordBookIdFor(lessonId), lessonId).firstOrNull()

    private fun wordBookIdFor(lessonId: String) = if (lessonId.startsWith("ur2-")) COURSE_2_ID else COURSE_1_ID

    companion object {
        const val COURSE_ID = "university-russian-1"
        const val COURSE_1_ID = COURSE_ID
        const val COURSE_2_ID = "university-russian-2"
        fun lessonId(number: Int) = "ur1-lesson-$number"
        fun lessonId(courseId: String, number: Int) = if (courseId == COURSE_2_ID) "ur2-lesson-$number" else lessonId(number)
        fun lessonNumber(lessonId: String) = lessonId.substringAfterLast('-').toIntOrNull()
    }
}
