package org.namchieh.rusmorph.data.remote

data class WordBookDto(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val coverUrl: String?,
    val lessonCount: Int,
    val version: Int,
)

data class WordBookLessonDto(
    val id: String,
    val wordBookId: String,
    val number: Int,
    val titleRu: String?,
    val titleZh: String?,
    val content: org.namchieh.rusmorph.domain.learning.LessonContentAvailability,
)

data class LessonWordDto(val entryId: String, val position: Int, val isKey: Boolean)
data class DialogueLineDto(val id: String, val speaker: String?, val text: String, val translation: String?, val audio: String?, val position: Int)
data class LessonDialogueDto(val id: String, val title: String, val description: String?, val lines: List<DialogueLineDto>)
data class TextParagraphDto(val id: String, val text: String, val translation: String?, val audio: String?, val position: Int)
data class LessonTextDto(val id: String, val title: String, val translationTitle: String?, val paragraphs: List<TextParagraphDto>)

data class WordBookListDto(val schemaVersion: Int, val items: List<WordBookDto>, val nextCursor: String?, val wordBookId: String? = null, val lessonId: String? = null)
data class WordBookLessonListDto(val schemaVersion: Int, val wordBookId: String, val items: List<WordBookLessonDto>, val nextCursor: String?, val lessonId: String? = null)
data class LessonWordListDto(val schemaVersion: Int, val wordBookId: String, val lessonId: String, val items: List<LessonWordDto>, val nextCursor: String?)
data class LessonDialogueListDto(val schemaVersion: Int, val wordBookId: String, val lessonId: String, val items: List<LessonDialogueDto>, val nextCursor: String?)
data class LessonTextListDto(val schemaVersion: Int, val wordBookId: String, val lessonId: String, val items: List<LessonTextDto>, val nextCursor: String?)
