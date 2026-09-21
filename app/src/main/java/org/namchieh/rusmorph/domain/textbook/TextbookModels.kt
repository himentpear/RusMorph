package org.namchieh.rusmorph.domain.textbook

enum class TextbookBlockType { READING, DIALOGUE, LETTER }
enum class KnowledgeType { WORD, PHRASE, GRAMMAR, PATTERN, PRONUNCIATION, AI_NOTE }

data class Textbook(val id: String, val title: String, val language: String, val level: String, val sourceVersion: Int)
data class TextbookLesson(val id: String, val textbookId: String, val lessonNumber: Int, val title: String)
data class TextbookBlock(val id: String, val lessonId: String, val order: Int, val type: TextbookBlockType, val title: String?, val sentences: List<LessonSentence>)
data class LessonSentence(val id: String, val lessonId: String, val blockId: String, val order: Int, val text: String, val sourceText: String)
data class SentenceKnowledge(
    val id: String, val sentenceId: String, val type: KnowledgeType, val text: String,
    val label: String?, val explanation: String?, val example: String?, val start: Int, val end: Int,
    val status: String?, val knowledgeVersion: Int = 1, val generatedBy: String? = null, val reviewStatus: String? = null,
)
data class LessonTextContent(val textbook: Textbook, val lesson: TextbookLesson, val blocks: List<TextbookBlock>)

enum class KnowledgeProgressStatus {
    SEEN,
    UNDERSTOOD,
    PRACTICED,
    MASTERED;

    val labelZh: String get() = when (this) {
        SEEN -> "已读"
        UNDERSTOOD -> "已理解"
        PRACTICED -> "已练习"
        MASTERED -> "已掌握"
    }

    val rank: Int get() = when (this) {
        SEEN -> 1
        UNDERSTOOD -> 2
        PRACTICED -> 3
        MASTERED -> 4
    }
}

data class KnowledgeProgress(
    val knowledgeId: String,
    val status: KnowledgeProgressStatus = KnowledgeProgressStatus.SEEN,
    val seenCount: Int = 1,
    val understoodAt: Long? = null,
    val practicedCount: Int = 0,
    val masteredAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val lessonId: String? = null,
)

/** Boundary between textbook text selection and the existing vocabulary lookup system. */
fun interface TextSelectionHandler {
    fun onWordSelected(sentenceId: String, word: String)
}

