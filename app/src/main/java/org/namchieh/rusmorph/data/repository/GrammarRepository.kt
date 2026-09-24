package org.namchieh.rusmorph.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.namchieh.rusmorph.data.local.GrammarExamDao
import org.namchieh.rusmorph.data.local.GrammarMasteryEntity
import org.namchieh.rusmorph.data.local.GrammarPointEntity
import org.namchieh.rusmorph.data.local.QuestionEntity
import org.namchieh.rusmorph.domain.grammar.GrammarPointOverview

class GrammarRepository(private val dao: GrammarExamDao) {
    fun observeGrammarPoints(): Flow<List<GrammarPointOverview>> = combine(
        dao.observeAllGrammarPoints(),
        dao.observeGrammarPointStats(),
        dao.observeAllGrammarMastery(),
    ) { points, stats, mastery ->
        val statsByPoint = stats.associateBy { it.pointId }
        val masteryByPoint = mastery.associateBy { it.pointId }
        points.map { point ->
            GrammarPointOverview(
                point = point,
                mastery = masteryByPoint[point.pointId]?.mastery ?: 0.0,
                realQuestionCount = statsByPoint[point.pointId]?.realQuestionCount ?: 0,
                completedQuestionCount = statsByPoint[point.pointId]?.completedQuestionCount ?: 0,
            )
        }
    }

    suspend fun getAllGrammarPoints(): List<GrammarPointEntity> = dao.getAllGrammarPoints()
    suspend fun getGrammarPoint(pointId: String): GrammarPointEntity? = dao.getGrammarPoint(pointId)
    fun observeGrammarPoint(pointId: String): Flow<GrammarPointEntity?> = dao.observeGrammarPoint(pointId)
    fun observeQuestionsForGrammarPoint(pointId: String): Flow<List<QuestionEntity>> =
        dao.observeQuestionsForGrammarPoint(pointId)
    suspend fun getQuestionsForGrammarPoint(pointId: String): List<QuestionEntity> =
        dao.getQuestionsForGrammarPoint(pointId)
    suspend fun getGrammarPointsForQuestion(questionId: String): List<GrammarPointEntity> =
        dao.getGrammarPointsForQuestion(questionId)
    fun observeGrammarMastery(pointId: String): Flow<GrammarMasteryEntity?> = dao.observeGrammarMastery(pointId)
}
