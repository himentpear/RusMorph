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

/** Boundary between textbook text selection and the existing vocabulary lookup system. */
fun interface TextSelectionHandler {
    fun onWordSelected(sentenceId: String, word: String)
}
