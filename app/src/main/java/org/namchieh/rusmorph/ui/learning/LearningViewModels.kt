package org.namchieh.rusmorph.ui.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.namchieh.rusmorph.data.local.LexiconEntryWithDetails
import org.namchieh.rusmorph.data.repository.CourseRepository
import org.namchieh.rusmorph.data.repository.LearningRepository
import org.namchieh.rusmorph.data.repository.LearningStats
import org.namchieh.rusmorph.data.settings.AppSettings
import org.namchieh.rusmorph.domain.learning.Course
import org.namchieh.rusmorph.domain.learning.Dialogue
import org.namchieh.rusmorph.domain.learning.LearningActivity
import org.namchieh.rusmorph.domain.learning.LearningActivityType
import org.namchieh.rusmorph.domain.learning.LearningProgress
import org.namchieh.rusmorph.domain.learning.LearningStatus
import org.namchieh.rusmorph.domain.learning.Lesson
import org.namchieh.rusmorph.domain.learning.ReviewItem
import org.namchieh.rusmorph.domain.learning.TextContent

sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Content<T>(val value: T) : Loadable<T>
    data class Error(val message: String) : Loadable<Nothing>
}

class CoursesViewModel(private val courses: CourseRepository) : ViewModel() {
    val state = MutableStateFlow<Loadable<List<Course>>>(Loadable.Loading)
    init { viewModelScope.launch { state.value = runCatching { courses.courses() }.fold({ Loadable.Content(it) }, { Loadable.Error(it.message ?: "课程加载失败") }) } }
}

class CourseDetailViewModel(private val repository: CourseRepository, private val courseId: String) : ViewModel() {
    val course = MutableStateFlow<Course?>(null)
    val lessons = MutableStateFlow<Loadable<List<Lesson>>>(Loadable.Loading)
    init { viewModelScope.launch {
        course.value = repository.course(courseId)
        lessons.value = runCatching { repository.lessons(courseId) }.fold({ Loadable.Content(it) }, { Loadable.Error(it.message ?: "课次加载失败") })
    } }
}

class LessonDetailViewModel(private val repository: CourseRepository, private val courseId: String, private val lessonId: String) : ViewModel() {
    val state = MutableStateFlow<Loadable<Lesson>>(Loadable.Loading)
    init { viewModelScope.launch { state.value = runCatching { requireNotNull(repository.lesson(courseId, lessonId)) }.fold({ Loadable.Content(it) }, { Loadable.Error("未找到该课") }) } }
}

class VocabularyViewModel(
    private val repository: CourseRepository,
    private val lessonId: String,
    private val learning: LearningRepository? = null,
) : ViewModel() {
    val state = MutableStateFlow<Loadable<List<LexiconEntryWithDetails>>>(Loadable.Loading)
    val enrolledIds: StateFlow<Set<String>> = (learning?.observeAllReviewSourceIds() ?: kotlinx.coroutines.flow.flowOf(emptySet()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch {
            state.value = runCatching { repository.vocabulary(lessonId) }
                .fold({ Loadable.Content(it) }, { Loadable.Error("词汇加载失败") })
        }
    }

    fun batchAddWords(entryIds: List<String>) = viewModelScope.launch {
        learning?.batchAddToReview(entryIds, lessonId)
    }

    fun addWord(entryId: String) = viewModelScope.launch {
        learning?.addToReview(
            ReviewItem(
                id = "word-$entryId",
                type = org.namchieh.rusmorph.domain.learning.ReviewItemType.WORD,
                sourceId = entryId,
                lessonId = lessonId,
                dueAt = System.currentTimeMillis(),
                interval = 1,
                difficulty = 2.5,
                mistakeCount = 0,
                lastResult = null,
            ),
        )
    }

    fun removeWord(entryId: String) = viewModelScope.launch {
        learning?.removeFromReview(entryId)
    }
}

class DialogueViewModel(private val repository: CourseRepository, private val dialogueId: String) : ViewModel() {
    val state = MutableStateFlow<Loadable<Dialogue>>(Loadable.Loading)
    init { viewModelScope.launch { state.value = runCatching { requireNotNull(repository.dialogue(dialogueId)) }.fold({ Loadable.Content(it) }, { Loadable.Error("对话加载失败") }) } }
}

class TextDetailViewModel(private val repository: CourseRepository, private val textId: String) : ViewModel() {
    val state = MutableStateFlow<Loadable<TextContent>>(Loadable.Loading)
    init {
        viewModelScope.launch {
            state.value = runCatching { requireNotNull(repository.text(textId)) }
                .fold({ Loadable.Content(it) }, { Loadable.Error("课文加载失败") })
        }
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LearningSummaryViewModel(
    private val learning: LearningRepository,
    private val appSettings: AppSettings? = null,
    private val courseRepository: CourseRepository? = null,
) : ViewModel() {
    val activeCourseId: StateFlow<String> = appSettings?.activeCourseId
        ?: MutableStateFlow(AppSettings.DEFAULT_COURSE_ID)

    val stats: StateFlow<LearningStats> = activeCourseId.flatMapLatest { courseId ->
        learning.observeCourseStats(courseId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearningStats())

    val dueReviews: StateFlow<List<ReviewItem>> = activeCourseId.flatMapLatest { courseId ->
        learning.observeCourseDueReviews(courseId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val progress: StateFlow<List<LearningProgress>> = learning.observeProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activities: StateFlow<List<LearningActivity>> = learning.observeRecentActivities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectCourse(courseId: String) {
        appSettings?.setActiveCourseId(courseId)
    }

    fun continueLesson(courseId: String, lessonId: String) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        learning.saveProgress(LearningProgress(lessonId, courseId, lessonId, null, 0f, LearningStatus.IN_PROGRESS, now))
    }

    /**
     * 每日自动推进：检查当前课本当前课次，按目标配额自动收纳未学习生词
     */
    fun checkAndAutoAdvanceDaily(currentCourseId: String) = viewModelScope.launch {
        if (appSettings == null || courseRepository == null) return@launch
        if (!appSettings.autoAdvanceEnabled.value) return@launch

        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastDate = appSettings.getLastAutoAdvanceDate()
        if (today == lastDate) return@launch

        val targetCount = appSettings.dailyNewWordTarget.value
        val lessons = courseRepository.lessons(currentCourseId)

        for (lesson in lessons) {
            val vocab = runCatching { courseRepository.vocabulary(lesson.id) }.getOrNull() ?: continue
            val unlearned = vocab.filter { !learning.isWordInReview(it.entry.id) }
            if (unlearned.isNotEmpty()) {
                val toAdd = unlearned.take(targetCount).map { it.entry.id }
                learning.batchAddToReview(toAdd, lesson.id)
                appSettings.setLastAutoAdvanceDate(today)
                break
            }
        }
    }
}
