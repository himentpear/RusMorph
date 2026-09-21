package org.namchieh.rusmorph.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.namchieh.rusmorph.data.local.KnowledgeProgressDao
import org.namchieh.rusmorph.data.local.KnowledgeProgressEntity
import org.namchieh.rusmorph.data.local.SentenceKnowledgeEntity
import org.namchieh.rusmorph.data.local.TextbookDao
import org.namchieh.rusmorph.data.local.TextbookKnowledgeDao
import org.namchieh.rusmorph.domain.textbook.KnowledgeProgress
import org.namchieh.rusmorph.domain.textbook.KnowledgeProgressStatus
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

class TextbookKnowledgeRepository(
    private val dao: TextbookKnowledgeDao,
    private val progressDao: KnowledgeProgressDao? = null,
) {
    suspend fun getSentenceKnowledge(sentenceId: String) = dao.knowledge(sentenceId).map { it.toDomain() }
    suspend fun getKnowledgeForSentences(sentenceIds: List<String>): List<SentenceKnowledge> =
        if (sentenceIds.isEmpty()) emptyList() else dao.knowledgeForSentences(sentenceIds).map { it.toDomain() }
    suspend fun getGrammar(sentenceId: String) = byType(sentenceId, KnowledgeType.GRAMMAR)
    suspend fun getPatterns(sentenceId: String) = byType(sentenceId, KnowledgeType.PATTERN)
    suspend fun getPhrases(sentenceId: String) = byType(sentenceId, KnowledgeType.PHRASE)
    private suspend fun byType(sentenceId: String, type: KnowledgeType) = dao.knowledgeByType(sentenceId, type.name).map { it.toDomain() }

    suspend fun getProgress(knowledgeId: String): KnowledgeProgress? = progressDao?.getProgress(knowledgeId)?.toDomain()
    suspend fun getProgressForLesson(lessonId: String): Map<String, KnowledgeProgress> =
        progressDao?.getProgressForLesson(lessonId)?.associate { it.knowledgeId to it.toDomain() } ?: emptyMap()

    fun observeProgressForLesson(lessonId: String): Flow<Map<String, KnowledgeProgress>> =
        progressDao?.observeProgressForLesson(lessonId)?.map { list -> list.associate { it.knowledgeId to it.toDomain() } }
            ?: kotlinx.coroutines.flow.flowOf(emptyMap())

    suspend fun recordSeen(knowledgeId: String, lessonId: String? = null): KnowledgeProgress {
        val now = System.currentTimeMillis()
        val existing = progressDao?.getProgress(knowledgeId)?.toDomain()
        val updated = if (existing != null) {
            existing.copy(
                seenCount = existing.seenCount + 1,
                updatedAt = now,
                lessonId = existing.lessonId ?: lessonId,
            )
        } else {
            KnowledgeProgress(
                knowledgeId = knowledgeId,
                status = KnowledgeProgressStatus.SEEN,
                seenCount = 1,
                updatedAt = now,
                lessonId = lessonId,
            )
        }
        progressDao?.upsert(updated.toEntity())
        return updated
    }

    suspend fun updateStatus(knowledgeId: String, status: KnowledgeProgressStatus, lessonId: String? = null): KnowledgeProgress {
        val now = System.currentTimeMillis()
        val existing = progressDao?.getProgress(knowledgeId)?.toDomain()
        val updated = if (existing != null) {
            existing.copy(
                status = status,
                understoodAt = if (status == KnowledgeProgressStatus.UNDERSTOOD && existing.understoodAt == null) now else existing.understoodAt,
                practicedCount = if (status == KnowledgeProgressStatus.PRACTICED) existing.practicedCount + 1 else existing.practicedCount,
                masteredAt = if (status == KnowledgeProgressStatus.MASTERED && existing.masteredAt == null) now else existing.masteredAt,
                updatedAt = now,
                lessonId = existing.lessonId ?: lessonId,
            )
        } else {
            KnowledgeProgress(
                knowledgeId = knowledgeId,
                status = status,
                seenCount = 1,
                understoodAt = if (status == KnowledgeProgressStatus.UNDERSTOOD) now else null,
                practicedCount = if (status == KnowledgeProgressStatus.PRACTICED) 1 else 0,
                masteredAt = if (status == KnowledgeProgressStatus.MASTERED) now else null,
                updatedAt = now,
                lessonId = lessonId,
            )
        }
        progressDao?.upsert(updated.toEntity())
        return updated
    }
}

private fun SentenceKnowledgeEntity.toDomain() = SentenceKnowledge(
    id, sentenceId, KnowledgeType.valueOf(type), text, label, explanation, example, start, end,
    status, knowledgeVersion, generatedBy, reviewStatus,
)

private fun KnowledgeProgressEntity.toDomain() = KnowledgeProgress(
    knowledgeId = knowledgeId,
    status = runCatching { KnowledgeProgressStatus.valueOf(status) }.getOrDefault(KnowledgeProgressStatus.SEEN),
    seenCount = seenCount,
    understoodAt = understoodAt,
    practicedCount = practicedCount,
    masteredAt = masteredAt,
    updatedAt = updatedAt,
    lessonId = lessonId,
)

private fun KnowledgeProgress.toEntity() = KnowledgeProgressEntity(
    knowledgeId = knowledgeId,
    status = status.name,
    seenCount = seenCount,
    understoodAt = understoodAt,
    practicedCount = practicedCount,
    masteredAt = masteredAt,
    updatedAt = updatedAt,
    lessonId = lessonId,
)

