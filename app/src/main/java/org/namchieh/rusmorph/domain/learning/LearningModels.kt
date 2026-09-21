package org.namchieh.rusmorph.domain.learning

enum class LearningUnitType { VOCABULARY, GRAMMAR, DIALOGUE, TEXT, PRACTICE, REVIEW, LISTENING, WRITING, QUIZ }
enum class WordBookSourceType { BUILT_IN, IMPORTED, REMOTE }
enum class LearningStatus { NOT_STARTED, IN_PROGRESS, COMPLETED }
enum class ReviewItemType { WORD, GRAMMAR, SENTENCE, DIALOGUE, TEXT, EXERCISE }
enum class MistakeType { WORD, GRAMMAR, PRONUNCIATION, TEXT, EXERCISE }
enum class PronunciationSessionType { FREE, SENTENCE, DIALOGUE_LINE, TEXT_SENTENCE }
enum class AIContextType { NONE, WORD, GRAMMAR, COURSE, LESSON, DIALOGUE, DIALOGUE_LINE, TEXT, TEXT_PARAGRAPH, TEXT_SENTENCE, REVIEW }
enum class LearningActivityType {
    WORD_VIEWED, WORD_LEARNED, GRAMMAR_COMPLETED, DIALOGUE_COMPLETED, TEXT_COMPLETED,
    PRONUNCIATION_COMPLETED, REVIEW_COMPLETED, LESSON_COMPLETED,
}

data class Course(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val lessonCount: Int,
    val completedLessonCount: Int = 0,
    val progress: Float = 0f,
    val visualIdentity: String = "brick",
    val lastLessonId: String? = null,
    val coverResourceName: String? = null,
)

/** Versioned catalog metadata; progress remains owned by Course/LearningRepository. */
data class WordBook(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val description: String? = null,
    val lessonCount: Int,
    val completedLessonCount: Int = 0,
    val progress: Float = 0f,
    val visualIdentity: String = "brick",
    val lastLessonId: String? = null,
    val coverResourceName: String? = null,
    val coverUri: String? = null,
    val cover: String? = coverUri ?: coverResourceName,
    val sourceType: WordBookSourceType = WordBookSourceType.BUILT_IN,
    val version: Int = 1,
)

data class LessonContentAvailability(val words: Boolean = false, val dialogues: Boolean = false, val texts: Boolean = false)
data class LessonWord(
    val wordBookId: String,
    val lessonId: String,
    val entryId: String,
    val order: Int,
    val isKey: Boolean,
    val entry: org.namchieh.rusmorph.data.local.LexiconEntryWithDetails,
)

fun scopedLearningId(wordBookId: String, lessonId: String): String = "${wordBookId.length}:$wordBookId$lessonId"

data class Lesson(
    val id: String,
    val courseId: String,
    val number: Int,
    val titleRu: String,
    val titleZh: String,
    val units: List<LearningUnit>,
    val progress: Float = 0f,
    val status: LearningStatus = LearningStatus.NOT_STARTED,
    val latestScore: Double? = null,
    val isReviewLesson: Boolean = false,
)

data class LearningUnit(
    val id: String,
    val lessonId: String,
    val type: LearningUnitType,
    val titleRu: String,
    val titleZh: String,
    val itemCount: Int,
    val progress: Float = 0f,
    val status: LearningStatus = LearningStatus.NOT_STARTED,
    val latestScore: Double? = null,
    val sourceId: String? = null,
)

data class VocabularyItem(val entryId: String, val lessonId: String, val isKey: Boolean = false, val learned: Boolean = false)
data class GrammarPoint(
    val id: String, val titleRu: String, val titleZh: String, val summary: String,
    val explanation: String, val rules: List<String>, val examples: List<String>,
    val exceptions: List<String>, val relatedWords: List<String>, val relatedLessons: List<String>, val tags: List<String>,
)
data class Dialogue(val id: String, val lessonId: String, val title: String, val description: String?, val lines: List<DialogueLine>, val wordBookId: String? = null) {
    val canRolePlay: Boolean get() = lines.isNotEmpty() && lines.all { !it.speaker.isNullOrBlank() } && lines.mapNotNull { it.speaker }.distinct().size > 1
}
data class DialogueLine(
    val id: String, val speaker: String?, val text: String, val translation: String?,
    val audio: String?, val order: Int, val pronunciationMetadata: Map<String, String> = emptyMap(),
)
data class TextContent(val id: String, val lessonId: String, val title: String, val translationTitle: String?, val paragraphs: List<TextParagraph>, val wordBookId: String? = null)
data class TextParagraph(val id: String, val order: Int, val text: String, val translation: String?, val audio: String?, val sentences: List<String> = emptyList())
data class Exercise(val id: String, val lessonId: String, val title: String, val kind: String)
data class LearningProgress(val sourceId: String, val courseId: String?, val lessonId: String?, val unitType: LearningUnitType?, val progress: Float, val status: LearningStatus, val updatedAt: Long)
data class ReviewItem(val id: String, val type: ReviewItemType, val sourceId: String, val lessonId: String?, val dueAt: Long?, val interval: Int?, val difficulty: Double?, val mistakeCount: Int, val lastResult: Double?)
data class MistakeItem(val id: String, val type: MistakeType, val sourceId: String, val lessonId: String?, val count: Int, val lastOccurredAt: Long)
data class LearningActivity(val id: String, val type: LearningActivityType, val sourceId: String?, val courseId: String?, val lessonId: String?, val occurredAt: Long, val durationSeconds: Int = 0)
data class PronunciationSession(val id: String, val type: PronunciationSessionType, val sourceId: String?, val courseId: String?, val lessonId: String?, val targetText: String, val startedAt: Long, val completedAt: Long? = null, val intelligibilityScore: Double? = null)
data class DialoguePracticeSession(val id: String, val dialogueId: String, val selectedRole: String, val completedLines: List<String>, val scores: Map<String, Double>, val mistakes: List<String>, val startedAt: Long, val completedAt: Long?)
data class AIContext(val type: AIContextType, val sourceId: String? = null, val courseId: String? = null, val lessonId: String? = null, val label: String? = null, val excerpt: String? = null)

