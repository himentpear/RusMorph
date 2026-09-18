package org.namchieh.rusmorph.ui.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.namchieh.rusmorph.data.local.LexiconEntryWithDetails
import org.namchieh.rusmorph.data.repository.WordBookRepository
import org.namchieh.rusmorph.data.repository.LearningRepository
import org.namchieh.rusmorph.data.repository.LearningStats
import org.namchieh.rusmorph.domain.learning.WordBook
import org.namchieh.rusmorph.domain.learning.Dialogue
import org.namchieh.rusmorph.domain.learning.LearningActivity
import org.namchieh.rusmorph.domain.learning.LearningActivityType
import org.namchieh.rusmorph.domain.learning.LearningProgress
import org.namchieh.rusmorph.domain.learning.LearningStatus
import org.namchieh.rusmorph.domain.learning.Lesson
import org.namchieh.rusmorph.domain.learning.ReviewItem
import org.namchieh.rusmorph.domain.learning.ReviewItemType
import org.namchieh.rusmorph.domain.learning.TextContent

sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Content<T>(val value: T) : Loadable<T>
    data class Error(val message: String) : Loadable<Nothing>
}

class WordBooksViewModel(private val repository: WordBookRepository, private val learning: LearningRepository) : ViewModel() {
    val state = MutableStateFlow<Loadable<List<WordBook>>>(Loadable.Loading)
    init { viewModelScope.launch {
        try {
            val books = repository.wordBooks()
            val lessons = books.associate { it.id to repository.lessons(it.id) }
            learning.observeProgress().collect { rows -> state.value = Loadable.Content(books.map { book ->
                book.withProgress(lessons[book.id].orEmpty(), rows)
            }) }
        } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; state.value = Loadable.Error(e.message ?: "词书加载失败") }
    } }
}

fun WordBook.withProgress(lessons: List<Lesson>, rows: List<LearningProgress>): WordBook {
    val latest = rows.filter { it.wordBookId == id && it.unitType == null && it.lessonId in lessons.map { l -> l.id } }
        .sortedByDescending { it.updatedAt }.distinctBy { it.lessonId }
    return copy(completedLessonCount = latest.count { it.status == LearningStatus.COMPLETED },
        progress = if (lessons.isEmpty()) 0f else latest.sumOf { it.progress.coerceIn(0f, 1f).toDouble() }.toFloat() / lessons.size,
        lastLessonId = latest.firstOrNull()?.lessonId)
}

class WordBookDetailViewModel(private val repository: WordBookRepository, private val wordBookId: String, private val learning: LearningRepository) : ViewModel() {
    val course = MutableStateFlow<WordBook?>(null)
    val lessons = MutableStateFlow<Loadable<List<Lesson>>>(Loadable.Loading)
    init { viewModelScope.launch {
        try {
            val book = requireNotNull(repository.wordBook(wordBookId))
            val catalog = repository.lessons(wordBookId)
            learning.observeProgress().collect { rows ->
                course.value = book.withProgress(catalog, rows)
                lessons.value = Loadable.Content(catalog.map { lesson ->
                    val saved = rows.filter { it.wordBookId == wordBookId && it.lessonId == lesson.id && it.unitType == null }.maxByOrNull { it.updatedAt }
                    if (saved == null) lesson else lesson.copy(progress = saved.progress, status = saved.status)
                })
            }
        } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; lessons.value = Loadable.Error(e.message ?: "课次加载失败") }
    } }
}

class LessonDetailViewModel(
    private val repository: WordBookRepository,
    private val learning: LearningRepository,
    private val wordBookId: String,
    private val lessonId: String,
) : ViewModel() {
    val state = MutableStateFlow<Loadable<Lesson>>(Loadable.Loading)

    init {
        viewModelScope.launch {
            state.value = runCatching {
                val lesson = requireNotNull(repository.lesson(wordBookId, lessonId))
                markLessonStarted()
                val saved = learning.observeProgress().first().firstOrNull { it.wordBookId == wordBookId && it.lessonId == lessonId && it.unitType == null }
                if (saved == null) lesson else lesson.copy(progress = saved.progress, status = saved.status)
            }
                .fold({ Loadable.Content(it) }, { Loadable.Error("未找到该课") })
        }
    }

    private suspend fun markLessonStarted() {
        learning.openLesson(wordBookId, lessonId)
    }
}

class VocabularyViewModel(
    private val repository: WordBookRepository,
    private val learning: LearningRepository,
    private val wordBookId: String,
    private val lessonId: String,
) : ViewModel() {
    val state = MutableStateFlow<Loadable<List<LexiconEntryWithDetails>>>(Loadable.Loading)
    init { viewModelScope.launch { state.value = runCatching { repository.lessonWords(wordBookId, lessonId) }.fold({ Loadable.Content(it) }, { Loadable.Error("单词加载失败") }) } }

    fun addReview(entryId: String) {
        viewModelScope.launch {
            learning.addToReview(
                ReviewItem(
                    id = "word-${org.namchieh.rusmorph.domain.learning.scopedContentId(wordBookId, lessonId, entryId)}", wordBookId = wordBookId, type = ReviewItemType.WORD, sourceId = entryId, lessonId = lessonId,
                    dueAt = System.currentTimeMillis(), interval = null, difficulty = null,
                    mistakeCount = 0, lastResult = null,
                ),
            )
        }
    }
}

class DialogueViewModel(private val repository: WordBookRepository, private val wordBookId: String, private val lessonId: String, private val dialogueId: String) : ViewModel() {
    val state = MutableStateFlow<Loadable<Dialogue>>(Loadable.Loading)
    init { viewModelScope.launch { state.value = runCatching { requireNotNull(repository.dialogue(wordBookId, lessonId, dialogueId)) }.fold({ Loadable.Content(it) }, { Loadable.Error("对话加载失败") }) } }
}

class TextViewModel(private val repository: WordBookRepository, private val wordBookId: String, private val lessonId: String, private val textId: String) : ViewModel() {
    val state = MutableStateFlow<Loadable<TextContent>>(Loadable.Loading)
    init { viewModelScope.launch { state.value = runCatching { requireNotNull(repository.text(wordBookId, lessonId, textId)) }.fold({ Loadable.Content(it) }, { Loadable.Error("课文加载失败") }) } }
}

class LearningSummaryViewModel(private val learning: LearningRepository) : ViewModel() {
    val stats: StateFlow<LearningStats> = learning.observeStats().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearningStats())
    val progress: StateFlow<List<LearningProgress>> = learning.observeProgress().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activities: StateFlow<List<LearningActivity>> = learning.observeRecentActivities().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val dueReviews: StateFlow<List<ReviewItem>> = learning.observeDueReviews().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun continueLesson(wordBookId: String, lessonId: String) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        learning.openLesson(wordBookId, lessonId)
    }
}
