package org.namchieh.rusmorph.data.repository

@Deprecated("Use WordBookRepository")
class CourseRepository(private val books: WordBookRepository) {
    suspend fun courses() = books.getWordBooks()
    suspend fun course(courseId: String) = books.getWordBook(courseId)
    suspend fun lessons(courseId: String) = books.getLessons(courseId)
    suspend fun lesson(courseId: String, lessonId: String) = books.getLesson(courseId, lessonId)
    companion object {
        const val COURSE_ID = AssetWordBookDataSource.WORD_BOOK_ID
        fun lessonId(number: Int) = AssetWordBookDataSource.lessonId(number)
    }
}
