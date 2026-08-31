package org.namchieh.rusmorph.ui.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.namchieh.rusmorph.data.local.LexiconEntryWithDetails
import org.namchieh.rusmorph.data.repository.CourseRepository
import org.namchieh.rusmorph.data.repository.LearningRepository
import org.namchieh.rusmorph.data.repository.LearningStats
import org.namchieh.rusmorph.domain.learning.Course
import org.namchieh.rusmorph.domain.learning.Dialogue
import org.namchieh.rusmorph.domain.learning.LearningActivity
import org.namchieh.rusmorph.domain.learning.LearningActivityType
import org.namchieh.rusmorph.domain.learning.LearningProgress
import org.namchieh.rusmorph.domain.learning.LearningStatus
import org.namchieh.rusmorph.domain.learning.Lesson
import org.namchieh.rusmorph.domain.learning.ReviewItem

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

class VocabularyViewModel(private val repository: CourseRepository, private val lessonId: String) : ViewModel() {
    val state = MutableStateFlow<Loadable<List<LexiconEntryWithDetails>>>(Loadable.Loading)
    init { viewModelScope.launch { state.value = runCatching { repository.vocabulary(lessonId) }.fold({ Loadable.Content(it) }, { Loadable.Error("词汇加载失败") }) } }
}

class DialogueViewModel(private val repository: CourseRepository, private val dialogueId: String) : ViewModel() {
    val state = MutableStateFlow<Loadable<Dialogue>>(Loadable.Loading)
    init { viewModelScope.launch { state.value = runCatching { requireNotNull(repository.dialogue(dialogueId)) }.fold({ Loadable.Content(it) }, { Loadable.Error("对话加载失败") }) } }
}

class LearningSummaryViewModel(private val learning: LearningRepository) : ViewModel() {
    val stats: StateFlow<LearningStats> = learning.observeStats().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearningStats())
    val progress: StateFlow<List<LearningProgress>> = learning.observeProgress().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activities: StateFlow<List<LearningActivity>> = learning.observeRecentActivities().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val dueReviews: StateFlow<List<ReviewItem>> = learning.observeDueReviews().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun continueLesson(courseId: String, lessonId: String) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        learning.saveProgress(LearningProgress(lessonId, courseId, lessonId, null, 0f, LearningStatus.IN_PROGRESS, now))
    }
}
