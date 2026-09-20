package org.namchieh.rusmorph.domain.textbook

enum class ReadingSectionType { READING, DIALOGUE, LETTER }
enum class AnnotationType { WORD, PHRASE, GRAMMAR, PRONUNCIATION, AI_NOTE }

data class Textbook(val id: String, val title: String, val language: String, val level: String, val sourceVersion: Int)
data class TextbookLesson(val id: String, val textbookId: String, val lessonNumber: Int, val title: String)
data class ReadingSection(val id: String, val lessonId: String, val order: Int, val type: ReadingSectionType, val title: String?, val paragraphs: List<ReadingParagraph>)
data class ReadingParagraph(val id: String, val sectionId: String, val order: Int, val content: String)
data class LessonTextContent(val textbook: Textbook, val lesson: TextbookLesson, val sections: List<ReadingSection>)

/** Reserved boundary for future word selection; this phase intentionally has no lookup UI. */
fun interface TextSelectionHandler {
    fun onTextSelected(paragraphId: String, startOffset: Int, endOffset: Int)
}
