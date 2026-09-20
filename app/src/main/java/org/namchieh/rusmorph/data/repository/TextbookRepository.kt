package org.namchieh.rusmorph.data.repository

import org.namchieh.rusmorph.data.local.TextbookDao
import org.namchieh.rusmorph.domain.textbook.LessonTextContent
import org.namchieh.rusmorph.domain.textbook.ReadingParagraph
import org.namchieh.rusmorph.domain.textbook.ReadingSection
import org.namchieh.rusmorph.domain.textbook.ReadingSectionType
import org.namchieh.rusmorph.domain.textbook.Textbook
import org.namchieh.rusmorph.domain.textbook.TextbookLesson

/** Owns verified textbook reading material; it deliberately has no WordBook dependency. */
class TextbookRepository(private val dao: TextbookDao) {
    suspend fun getTextbooks(): List<Textbook> = dao.textbooks().map { Textbook(it.id, it.title, it.language, it.level, it.sourceVersion) }
    suspend fun getLessons(textbookId: String): List<TextbookLesson> = dao.lessons(textbookId).map { TextbookLesson(it.id, it.textbookId, it.lessonNumber, it.title) }
    suspend fun getParagraphs(sectionId: String): List<ReadingParagraph> = dao.paragraphs(sectionId).map { ReadingParagraph(it.id, it.sectionId, it.position, it.content) }
    suspend fun getLessonContent(lessonId: String): LessonTextContent? {
        val lesson = dao.lesson(lessonId) ?: return null
        val textbook = dao.textbooks().firstOrNull { it.id == lesson.textbookId } ?: return null
        return LessonTextContent(
            Textbook(textbook.id, textbook.title, textbook.language, textbook.level, textbook.sourceVersion),
            TextbookLesson(lesson.id, lesson.textbookId, lesson.lessonNumber, lesson.title),
            dao.sections(lessonId).map { section ->
                ReadingSection(section.id, section.lessonId, section.position, ReadingSectionType.valueOf(section.type), section.title, getParagraphs(section.id))
            },
        )
    }
}
