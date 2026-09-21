package org.namchieh.rusmorph.data.repository

import org.namchieh.rusmorph.data.local.SentenceKnowledgeEntity
import org.namchieh.rusmorph.data.local.TextbookDao
import org.namchieh.rusmorph.data.local.TextbookKnowledgeDao
import org.namchieh.rusmorph.domain.textbook.KnowledgeType
import org.namchieh.rusmorph.domain.textbook.LessonSentence
import org.namchieh.rusmorph.domain.textbook.LessonTextContent
import org.namchieh.rusmorph.domain.textbook.SentenceKnowledge
import org.namchieh.rusmorph.domain.textbook.Textbook
import org.namchieh.rusmorph.domain.textbook.TextbookBlock
import org.namchieh.rusmorph.domain.textbook.TextbookBlockType
import org.namchieh.rusmorph.domain.textbook.TextbookLesson

class TextbookRepository(private val dao: TextbookDao) {
    suspend fun getTextbooks(): List<Textbook> = dao.textbooks().map { Textbook(it.id, it.title, it.language, it.level, it.sourceVersion) }
    suspend fun getLessons(textbookId: String): List<TextbookLesson> = dao.lessons(textbookId).map { TextbookLesson(it.id, it.textbookId, it.lessonNumber, it.title) }
    suspend fun getLessonContent(lessonId: String): LessonTextContent? {
        val lesson = dao.lesson(lessonId) ?: return null
        val textbook = dao.textbooks().firstOrNull { it.id == lesson.textbookId } ?: return null
        return LessonTextContent(
            Textbook(textbook.id, textbook.title, textbook.language, textbook.level, textbook.sourceVersion),
            TextbookLesson(lesson.id, lesson.textbookId, lesson.lessonNumber, lesson.title),
            dao.blocks(lessonId).map { block ->
                TextbookBlock(
                    block.id, block.lessonId, block.order, TextbookBlockType.valueOf(block.type), block.title,
                    dao.sentences(block.id).map { LessonSentence(it.id, it.lessonId, it.blockId, it.order, it.text, it.sourceText) },
                )
            },
        )
    }
}

class TextbookKnowledgeRepository(private val dao: TextbookKnowledgeDao) {
    suspend fun getSentenceKnowledge(sentenceId: String) = dao.knowledge(sentenceId).map { it.toDomain() }
    suspend fun getGrammar(sentenceId: String) = byType(sentenceId, KnowledgeType.GRAMMAR)
    suspend fun getPatterns(sentenceId: String) = byType(sentenceId, KnowledgeType.PATTERN)
    suspend fun getPhrases(sentenceId: String) = byType(sentenceId, KnowledgeType.PHRASE)
    private suspend fun byType(sentenceId: String, type: KnowledgeType) = dao.knowledgeByType(sentenceId, type.name).map { it.toDomain() }
}

private fun SentenceKnowledgeEntity.toDomain() = SentenceKnowledge(
    id, sentenceId, KnowledgeType.valueOf(type), text, label, explanation, example, start, end,
    status, knowledgeVersion, generatedBy, reviewStatus,
)
