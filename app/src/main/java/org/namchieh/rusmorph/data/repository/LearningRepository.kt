package org.namchieh.rusmorph.data.repository

import java.util.UUID
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
import org.namchieh.rusmorph.data.settings.AppSettings
import org.namchieh.rusmorph.domain.learning.EbbinghausRetention
import org.namchieh.rusmorph.domain.learning.MistakeItem
import org.namchieh.rusmorph.domain.learning.PronunciationSession
import org.namchieh.rusmorph.domain.learning.ReviewItem
import org.namchieh.rusmorph.domain.learning.WordRetentionRecord

data class LearningStats(
    val dueReviewCount: Int = 0,
    val favoriteWordCount: Int = 0,
    val mistakeCount: Int = 0,
    val pronunciationCount: Int = 0,
    val activityCount: Int = 0,
    val studySeconds: Int = 0,
    val totalWordsInReview: Int = 0,
    val averageRetention: Float = 0.85f,
)

private data class GeneralLearningStats(
    val favorites: Int,
    val mistakes: Int,
    val pronunciations: Int,
    val activities: List<org.namchieh.rusmorph.data.local.LearningActivityEntity>,
)

class LearningRepository(private val dao: LearningDao) {
    fun coursePrefix(courseId: String): String =
        if (courseId == AppSettings.COURSE_2_ID || courseId.contains("2")) "ur2-" else "ur1-"

    fun observeProgress(): Flow<List<LearningProgress>> = dao.observeProgress().map { rows -> rows.map { it.toDomain() } }
    fun observeRecentActivities(limit: Int = 20): Flow<List<LearningActivity>> = dao.observeRecentActivities(limit).map { rows -> rows.map { it.toDomain() } }

    fun observeDueReviews(now: Long = System.currentTimeMillis()): Flow<List<ReviewItem>> = combine(
        dao.observeDueReviewItems(now), dao.observeLegacyDueItems(now),
    ) { generic, legacy ->
        generic.map { it.toDomain() } + legacy.map {
            ReviewItem(it.id, org.namchieh.rusmorph.domain.learning.ReviewItemType.WORD, it.sourceId,
                it.lessonNumber?.let { number -> CourseRepository.lessonId(number) }, it.dueAt, null, null, 0, null)
        }
    }

    /**
     * 按指定教材（第一册 / 第二册）严格物理隔离的待复习队列
     */
    fun observeCourseDueReviews(courseId: String, now: Long = System.currentTimeMillis()): Flow<List<ReviewItem>> {
        val prefix = coursePrefix(courseId)
        val isBook1 = prefix == "ur1-"
        return combine(
            dao.observeDueReviewItemsByPrefix(prefix, now),
            if (isBook1) dao.observeLegacyDueItems(now) else kotlinx.coroutines.flow.flowOf(emptyList()),
        ) { generic, legacy ->
            generic.map { it.toDomain() } + legacy.map {
                ReviewItem(it.id, org.namchieh.rusmorph.domain.learning.ReviewItemType.WORD, it.sourceId,
                    it.lessonNumber?.let { number -> CourseRepository.lessonId(number) }, it.dueAt, null, null, 0, null)
            }
        }
    }

    /**
     * 按指定教材严格计算的艾宾浩斯平均记忆留存率 (0.0f ~ 1.0f)
     */
    fun observeCourseRetention(courseId: String, now: Long = System.currentTimeMillis()): Flow<Float> {
        val prefix = coursePrefix(courseId)
        return dao.observeAllReviewItemsByPrefix(prefix).map { items ->
            if (items.isEmpty()) return@map 0.85f
            val records = items.map { item ->
                WordRetentionRecord(
                    entryId = item.sourceId,
                    lastReviewAt = item.updatedAt.takeIf { it > 0L },
                    intervalDays = (item.interval ?: 1).toDouble(),
                    easeFactor = item.difficulty ?: 2.5,
                    lessonId = item.lessonId,
                )
            }
            EbbinghausRetention.calculateAverageRetention(records, defaultWhenEmpty = 0.85f, nowMs = now)
        }
    }

    /**
     * 课本隔离的统计面板流，含真实艾宾浩斯留存率与总收纳词数
     */
    fun observeCourseStats(courseId: String, now: Long = System.currentTimeMillis()): Flow<LearningStats> {
        val prefix = coursePrefix(courseId)
        val isBook1 = prefix == "ur1-"

        val dueCountFlow = combine(
            dao.observeDueReviewItemsByPrefix(prefix, now),
            if (isBook1) dao.observeLegacyDueCount(now) else kotlinx.coroutines.flow.flowOf(0),
        ) { generic, legacy -> generic.size + legacy }

        val totalWordsFlow = dao.observeAllReviewItemsByPrefix(prefix).map { it.size }
        val retentionFlow = observeCourseRetention(courseId, now)

        val courseMetricsFlow = combine(dueCountFlow, totalWordsFlow, retentionFlow) { due, total, retention ->
            Triple(due, total, retention)
        }
        val generalStatsFlow = combine(
            dao.observeFavoriteCount(),
            dao.observeMistakeCount(),
            dao.observePronunciationCount(),
            dao.observeRecentActivities(500),
        ) { favorites, mistakes, pronunciations, activities ->
            GeneralLearningStats(favorites, mistakes, pronunciations, activities)
        }

        return combine(courseMetricsFlow, generalStatsFlow) { (due, totalWords, retention), general ->
            LearningStats(
                dueReviewCount = due,
                favoriteWordCount = general.favorites,
                mistakeCount = general.mistakes,
                pronunciationCount = general.pronunciations,
                activityCount = general.activities.size,
                studySeconds = general.activities.sumOf { it.durationSeconds },
                totalWordsInReview = totalWords,
                averageRetention = retention,
            )
        }
    }

    fun observeStats(now: Long = System.currentTimeMillis()): Flow<LearningStats> =
        observeCourseStats(AppSettings.DEFAULT_COURSE_ID, now)

    /**
     * 监听所有已收纳进复习队列的生词 sourceId 集合，供词汇列表即时打标
     */
    fun observeAllReviewSourceIds(): Flow<Set<String>> =
        dao.observeAllReviewItems().map { list -> list.map { it.sourceId }.toSet() }

    suspend fun saveProgress(item: LearningProgress) = dao.upsertProgress(item.toEntity())
    suspend fun recordActivity(item: LearningActivity) = dao.upsertActivity(item.toEntity())
    suspend fun addToReview(item: ReviewItem) = dao.upsertReviewItem(item.toEntity())

    /**
     * 批量将多条生词收纳至复习计划
     */
    suspend fun batchAddToReview(entryIds: List<String>, lessonId: String, now: Long = System.currentTimeMillis()) {
        val entities = entryIds.map { entryId ->
            GenericReviewItemEntity(
                id = "word-$entryId",
                type = "WORD",
                sourceId = entryId,
                lessonId = lessonId,
                dueAt = now,
                interval = 1,
                difficulty = 2.5,
                mistakeCount = 0,
                lastResult = null,
                updatedAt = now,
            )
        }
        dao.upsertReviewItems(entities)
    }

    /**
     * 从复习计划中移出生词
     */
    suspend fun removeFromReview(entryId: String) {
        dao.deleteReviewItem("word-$entryId")
        dao.deleteReviewItem(entryId)
    }

    /**
     * 检查词汇是否在复习计划中
     */
    suspend fun isWordInReview(entryId: String): Boolean {
        return dao.getReviewItemById("word-$entryId") != null || dao.getReviewItemById(entryId) != null
    }

    suspend fun recordMistake(item: MistakeItem) = dao.upsertMistake(item.toEntity())

    suspend fun recordPronunciation(session: PronunciationSession) {
        dao.upsertPronunciationSession(session.toEntity())
        val score = session.intelligibilityScore ?: return
        if (score < 70.0 && session.sourceId != null) {
            val now = System.currentTimeMillis()
            dao.upsertReviewItem(GenericReviewItemEntity(
                id = "pronunciation-${session.sourceId}", type = "SENTENCE", sourceId = session.sourceId,
                lessonId = session.lessonId, dueAt = now, interval = null, difficulty = null,
                mistakeCount = 1, lastResult = score, updatedAt = now,
            ))
            dao.upsertMistake(MistakeItemV2Entity(
                id = "pronunciation-${session.sourceId}", type = "PRONUNCIATION", sourceId = session.sourceId,
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
private fun GenericReviewItemEntity.toDomain() = ReviewItem(id, org.namchieh.rusmorph.domain.learning.ReviewItemType.valueOf(type), sourceId, lessonId, dueAt, interval, difficulty, mistakeCount, lastResult)
private fun ReviewItem.toEntity() = GenericReviewItemEntity(id, type.name, sourceId, lessonId, dueAt, interval, difficulty, mistakeCount, lastResult, System.currentTimeMillis())
private fun MistakeItem.toEntity() = MistakeItemV2Entity(id, type.name, sourceId, lessonId, count, lastOccurredAt)
private fun PronunciationSession.toEntity() = PronunciationSessionEntity(id, type.name, sourceId, courseId, lessonId, targetText, startedAt, completedAt, intelligibilityScore)
