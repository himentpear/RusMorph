package org.namchieh.rusmorph.data.repository

import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.namchieh.rusmorph.data.local.GenericReviewItemEntity
import org.namchieh.rusmorph.data.local.LearningActivityEntity
import org.namchieh.rusmorph.data.local.LearningDao
import org.namchieh.rusmorph.data.local.LearningProgressEntity
import org.namchieh.rusmorph.data.local.MistakeItemV2Entity
import org.namchieh.rusmorph.data.local.PronunciationSessionEntity
import org.namchieh.rusmorph.domain.learning.LearningActivity
import org.namchieh.rusmorph.domain.learning.LearningProgress
import org.namchieh.rusmorph.domain.learning.MistakeItem
import org.namchieh.rusmorph.domain.learning.PronunciationSession
import org.namchieh.rusmorph.domain.learning.ReviewItem

data class LearningStats(
    val dueReviewCount: Int = 0,
    val favoriteWordCount: Int = 0,
    val mistakeCount: Int = 0,
    val pronunciationCount: Int = 0,
    val activityCount: Int = 0,
    val studySeconds: Int = 0,
)

class LearningRepository(private val dao: LearningDao) {
    fun observeProgress(): Flow<List<LearningProgress>> = dao.observeProgress().map { rows -> rows.map { it.toDomain() } }
    fun observeRecentActivities(limit: Int = 20): Flow<List<LearningActivity>> = dao.observeRecentActivities(limit).map { rows -> rows.map { it.toDomain() } }
    fun observeDueReviews(now: Long = System.currentTimeMillis()): Flow<List<ReviewItem>> = combine(
        dao.observeDueReviewItems(now), dao.observeLegacyDueItems(now),
    ) { generic, legacy ->
        generic.map { it.toDomain() } + legacy.map {
            ReviewItem(it.id, org.namchieh.rusmorph.domain.learning.ReviewItemType.WORD, it.sourceId,
                it.lessonNumber?.let { number -> AssetWordBookRepository.lessonId(number) }, it.dueAt, null, null, 0, null)
        }
    }
    fun observeStats(now: Long = System.currentTimeMillis()): Flow<LearningStats> = combine(
        combine(dao.observeLegacyDueCount(now), dao.observeDueReviewItems(now)) { legacy, generic -> legacy + generic.size },
        dao.observeFavoriteCount(), dao.observeMistakeCount(), dao.observePronunciationCount(), dao.observeRecentActivities(500),
    ) { due, favorites, mistakes, pronunciations, activities ->
        LearningStats(due, favorites, mistakes, pronunciations, activities.size, activities.sumOf { it.durationSeconds })
    }

    suspend fun saveProgress(item: LearningProgress) = dao.upsertProgress(item.toEntity())
    suspend fun openLesson(wordBookId: String, lessonId: String) {
        val rows = observeProgress().first()
        val existing = rows.firstOrNull { it.wordBookId == wordBookId && it.lessonId == lessonId && it.unitType == null }
        saveProgress(existing?.copy(updatedAt = System.currentTimeMillis()) ?: LearningProgress(
            org.namchieh.rusmorph.domain.learning.scopedLearningId(wordBookId, lessonId), wordBookId, lessonId, null, 0f,
            org.namchieh.rusmorph.domain.learning.LearningStatus.IN_PROGRESS, System.currentTimeMillis()))
    }
    suspend fun recordActivity(item: LearningActivity) = dao.upsertActivity(item.toEntity())
    suspend fun addToReview(item: ReviewItem) = dao.upsertReviewItem(item.toEntity())
    suspend fun recordMistake(item: MistakeItem) = dao.upsertMistake(item.toEntity())

    suspend fun recordPronunciation(session: PronunciationSession) {
        dao.upsertPronunciationSession(session.toEntity())
        val score = session.intelligibilityScore ?: return
        if (score < 70.0 && session.sourceId != null) {
            val now = System.currentTimeMillis()
            dao.upsertReviewItem(GenericReviewItemEntity(
                id = "pronunciation-${org.namchieh.rusmorph.domain.learning.scopedContentId(session.wordBookId.orEmpty(), session.lessonId.orEmpty(), session.sourceId)}", type = "SENTENCE", sourceId = session.sourceId,
                lessonId = session.lessonId, dueAt = now, interval = null, difficulty = null,
                mistakeCount = 1, lastResult = score, updatedAt = now, wordBookId = session.wordBookId,
            ))
            dao.upsertMistake(MistakeItemV2Entity(
                id = "pronunciation-${org.namchieh.rusmorph.domain.learning.scopedContentId(session.wordBookId.orEmpty(), session.lessonId.orEmpty(), session.sourceId)}", type = "PRONUNCIATION", sourceId = session.sourceId,
                lessonId = session.lessonId, count = 1, lastOccurredAt = now,
            ))
        }
    }

    companion object {
        fun activityId() = UUID.randomUUID().toString()
    }
}

private fun LearningProgressEntity.toDomain() = LearningProgress(sourceId, courseId, lessonId, unitType?.let { org.namchieh.rusmorph.domain.learning.LearningUnitType.valueOf(it) }, progress, org.namchieh.rusmorph.domain.learning.LearningStatus.valueOf(status), updatedAt)
private fun LearningProgress.toEntity() = LearningProgressEntity(sourceId, courseId, lessonId, unitType?.name, progress, status.name, updatedAt)
private fun LearningActivityEntity.toDomain() = LearningActivity(id, org.namchieh.rusmorph.domain.learning.LearningActivityType.valueOf(type), sourceId, courseId, lessonId, occurredAt, durationSeconds)
private fun LearningActivity.toEntity() = LearningActivityEntity(id, type.name, sourceId, courseId, lessonId, occurredAt, durationSeconds)
private fun GenericReviewItemEntity.toDomain() = ReviewItem(id, org.namchieh.rusmorph.domain.learning.ReviewItemType.valueOf(type), sourceId, lessonId, dueAt, interval, difficulty, mistakeCount, lastResult, wordBookId)
private fun ReviewItem.toEntity() = GenericReviewItemEntity(id, type.name, sourceId, lessonId, dueAt, interval, difficulty, mistakeCount, lastResult, System.currentTimeMillis(), wordBookId)
private fun MistakeItem.toEntity() = MistakeItemV2Entity(id, type.name, sourceId, lessonId, count, lastOccurredAt)
private fun PronunciationSession.toEntity() = PronunciationSessionEntity(id, type.name, sourceId, courseId, lessonId, targetText, startedAt, completedAt, intelligibilityScore)
