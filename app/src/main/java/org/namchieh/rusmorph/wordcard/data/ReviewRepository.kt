package org.namchieh.rusmorph.wordcard.data

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.namchieh.rusmorph.wordcard.model.ReviewResult
import org.namchieh.rusmorph.wordcard.model.ReviewState
import org.namchieh.rusmorph.wordcard.review.ReviewScheduler

/**
 * 单词复习状态隔离数据仓库。
 * 遵循原则：用户学习状态不得写入语言知识数据中，ReviewState 单独保存。
 */
class ReviewRepository(context: Context) {
    private val prefs = context.getSharedPreferences("werus_word_review_states", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val memoryCache = mutableMapOf<String, ReviewState>()
    private val stateUpdateFlow = MutableStateFlow<Long>(System.currentTimeMillis())

    suspend fun getReviewState(lexemeId: String, userId: String = "local_user"): ReviewState = withContext(Dispatchers.IO) {
        val key = "${userId}_$lexemeId"
        synchronized(memoryCache) {
            memoryCache[key]?.let { return@withContext it }
        }
        val json = prefs.getString(key, null)
        val state = if (!json.isNullOrBlank()) {
            try {
                gson.fromJson(json, ReviewState::class.java)
            } catch (_: Exception) {
                ReviewState(lexemeId = lexemeId, userId = userId)
            }
        } else {
            ReviewState(lexemeId = lexemeId, userId = userId)
        }
        synchronized(memoryCache) {
            memoryCache[key] = state
        }
        state
    }

    suspend fun recordReview(lexemeId: String, result: ReviewResult, userId: String = "local_user"): ReviewState = withContext(Dispatchers.IO) {
        val current = getReviewState(lexemeId, userId)
        val next = ReviewScheduler.computeNextState(current, result).copy(isInReviewQueue = true)
        saveReviewState(next)
        next
    }

    suspend fun toggleEnrollment(lexemeId: String, userId: String = "local_user"): ReviewState = withContext(Dispatchers.IO) {
        val current = getReviewState(lexemeId, userId)
        val next = current.copy(isInReviewQueue = !current.isEnrolled)
        saveReviewState(next)
        next
    }

    suspend fun saveReviewState(state: ReviewState) = withContext(Dispatchers.IO) {
        val key = "${state.userId}_${state.lexemeId}"
        synchronized(memoryCache) {
            memoryCache[key] = state
        }
        prefs.edit().putString(key, gson.toJson(state)).apply()
        stateUpdateFlow.value = System.currentTimeMillis()
    }
}
