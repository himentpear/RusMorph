package org.namchieh.rusmorph.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.namchieh.rusmorph.domain.learning.Course
import org.namchieh.rusmorph.domain.learning.Dialogue
import org.namchieh.rusmorph.domain.learning.DialogueLine
import org.namchieh.rusmorph.domain.learning.LearningUnit
import org.namchieh.rusmorph.domain.learning.LearningUnitType
import org.namchieh.rusmorph.domain.learning.Lesson
import org.namchieh.rusmorph.domain.learning.TextContent
import org.namchieh.rusmorph.domain.learning.TextParagraph
import org.namchieh.rusmorph.domain.learning.TextSentence

class CourseRepository(
    private val context: Context,
    private val searchRepository: SearchRepository,
    private val gson: Gson = Gson(),
) {
    private val textbook2 by lazy { UniversityRussian2Assets(context) }
    private val course1 = Course(
        id = COURSE_1_ID,
        title = "大学俄语 1",
        subtitle = "Русский язык · Том 1",
        description = "以教材课次为主轴，整合词汇、对话、朗读与复习。",
        lessonCount = 18,
    )

    private val course2 = Course(
        id = COURSE_2_ID,
        title = "大学俄语 2",
        subtitle = "Русский язык · Том 2",
        description = "第二册教材课程，收录词汇和已校验的课文正文。",
        lessonCount = 12,
    )

    suspend fun courses(): List<Course> = listOf(course1, course2)

    suspend fun course(courseId: String): Course? = courses().firstOrNull { it.id == courseId }

    suspend fun lessons(courseId: String): List<Lesson> = withContext(Dispatchers.IO) {
        val targetCourse = course(courseId) ?: return@withContext emptyList()
        val isBook2 = (courseId == COURSE_2_ID)
        val dialogues = if (isBook2) emptyMap() else dialogues().associateBy { it.lessonId }
        val texts = if (isBook2) emptyMap() else texts().associateBy { it.lessonId }
        val textbookLessons = if (isBook2) textbook2.lessons.associateBy { it.lessonNumber } else emptyMap()

        (1..targetCourse.lessonCount).map { number ->
            val lessonId = lessonId(courseId, number)
            val allEntries = searchRepository.browseEntries(lesson = number, limit = 5_000)
            val bookEntries = allEntries.filter {
                if (isBook2) it.entry.sourceWorkbook.contains("（二）")
                else it.entry.sourceWorkbook.contains("（一）")
            }
            val vocabularyCount = bookEntries.size
            val units = buildList {
                if (vocabularyCount > 0) add(unit(lessonId, LearningUnitType.VOCABULARY, vocabularyCount))
                if (isBook2) {
                    val dialogueCount = textbook2.dialogueCount(number)
                    if (dialogueCount > 0) add(unit(lessonId, LearningUnitType.DIALOGUE, dialogueCount))
                    val readingCount = textbook2.readingCount(number)
                    if (readingCount > 0) add(unit(lessonId, LearningUnitType.TEXT, readingCount))
                } else {
                    dialogues[lessonId]?.let { add(unit(lessonId, LearningUnitType.DIALOGUE, it.lines.size)) }
                    texts[lessonId]?.let { text ->
                        add(unit(lessonId, LearningUnitType.TEXT, text.paragraphs.sumOf { it.sentences.size }))
                    }
                }
                if (!isBook2 && number == 8) add(unit(lessonId, LearningUnitType.REVIEW, 0))
            }
            Lesson(
                id = lessonId,
                courseId = courseId,
                number = number,
                titleRu = textbookLessons[number]?.titleRu ?: if (!isBook2 && number == 8) "ПОВТОРЕНИЕ" else "УРОК $number",
                titleZh = textbookLessons[number]?.titleZh ?: if (!isBook2 && number == 8) "复习" else "第 $number 课",
                units = units,
                isReviewLesson = (!isBook2 && number == 8),
            )
        }
    }

    suspend fun lesson(courseId: String, lessonId: String): Lesson? = lessons(courseId).firstOrNull { it.id == lessonId }

    suspend fun vocabulary(lessonId: String) = withContext(Dispatchers.IO) {
        val number = lessonNumber(lessonId) ?: return@withContext emptyList()
        val isBook2 = lessonId.startsWith("ur2-")
        searchRepository.browseEntries(lesson = number, limit = 5_000).filter {
            if (isBook2) it.entry.sourceWorkbook.contains("（二）")
            else it.entry.sourceWorkbook.contains("（一）")
        }
    }

    suspend fun dialogue(dialogueId: String): Dialogue? = withContext(Dispatchers.IO) {
        if (dialogueId.contains("ur2") || dialogueId.contains(COURSE_2_ID)) {
            val number = dialogueId.substringAfterLast('-').toIntOrNull() ?: return@withContext null
            return@withContext textbook2.dialogue(number, lessonId(COURSE_2_ID, number))
        }
        val number = dialogueId.substringAfterLast('-').toIntOrNull()
        dialogues().firstOrNull { it.id == dialogueId || (number != null && it.id == "dialogue-$number") }
    }

    suspend fun dialogueForLesson(lessonId: String): Dialogue? = withContext(Dispatchers.IO) {
        if (lessonId.startsWith("ur2-")) {
            val number = lessonNumber(lessonId) ?: return@withContext null
            return@withContext textbook2.dialogue(number, lessonId)
        }
        dialogues().firstOrNull { it.lessonId == lessonId }
    }

    suspend fun text(textId: String): TextContent? = withContext(Dispatchers.IO) {
        if (textId.contains("ur2-")) {
            val number = textId.substringAfterLast('-').toIntOrNull() ?: return@withContext null
            textbook2.reading(number, lessonId(COURSE_2_ID, number))
        } else texts().firstOrNull { it.id == textId || it.lessonId == textId }
    }

    suspend fun textForLesson(lessonId: String): TextContent? = withContext(Dispatchers.IO) {
        if (lessonId.startsWith("ur2-")) {
            val number = lessonNumber(lessonId) ?: return@withContext null
            textbook2.reading(number, lessonId)
        } else texts().firstOrNull { it.lessonId == lessonId }
    }

    private fun dialogues(): List<Dialogue> = runCatching {
        context.assets.open("database/textbook_dialogues.json").bufferedReader().use { reader ->
            val type = object : TypeToken<List<DialogueAsset>>() {}.type
            gson.fromJson<List<DialogueAsset>>(reader, type).map { asset ->
                val lessonId = lessonId(asset.lessonNumber)
                Dialogue(
                    id = "dialogue-${asset.lessonNumber}", lessonId = lessonId,
                    title = "Диалог · Урок ${asset.lessonNumber}", description = null,
                    lines = asset.lines.mapIndexed { index, text ->
                        DialogueLine("dialogue-${asset.lessonNumber}-line-${index + 1}", null, text, null, null, index + 1)
                    },
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun texts(): List<TextContent> = runCatching {
        context.assets.open("database/textbook_texts.json").bufferedReader().use { reader ->
            val type = object : TypeToken<List<TextAsset>>() {}.type
            gson.fromJson<List<TextAsset>>(reader, type).map { asset ->
                val lessonId = lessonId(asset.lessonNumber)
                TextContent(
                    id = "text-$lessonId",
                    lessonId = lessonId,
                    title = asset.title?.takeIf { it.isNotBlank() } ?: "ТЕКСТ · УРОК ${asset.lessonNumber}",
                    translationTitle = null,
                    paragraphs = asset.paragraphs.mapIndexed { paragraphIndex, paragraph ->
                        val paragraphId = "text-$lessonId-paragraph-${paragraphIndex + 1}"
                        TextParagraph(
                            id = paragraphId,
                            order = paragraphIndex + 1,
                            sentences = splitSentences(paragraph).mapIndexed { sentenceIndex, sentence ->
                                TextSentence(
                                    id = "$paragraphId-sentence-${sentenceIndex + 1}",
                                    order = sentenceIndex + 1,
                                    text = sentence,
                                )
                            },
                        )
                    },
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun splitSentences(paragraph: String): List<String> =
        paragraph.trim().split(Regex("(?<=[.!?…])\\s+(?=[А-ЯЁ–—])"))
            .map(String::trim)
            .filter(String::isNotBlank)

    private fun unit(lessonId: String, type: LearningUnitType, count: Int): LearningUnit {
        val titles = when (type) {
            LearningUnitType.VOCABULARY -> "СЛОВА" to "词汇"
            LearningUnitType.DIALOGUE -> "ДИАЛОГ" to "对话"
            LearningUnitType.REVIEW -> "ПОВТОРЕНИЕ" to "复习"
            LearningUnitType.TEXT -> "ТЕКСТ" to "课文"
            else -> type.name to type.name
        }
        return LearningUnit("$lessonId-${type.name.lowercase()}", lessonId, type, titles.first, titles.second, count)
    }

    private data class DialogueAsset(val lessonNumber: Int, val lines: List<String>)
    private data class TextAsset(val lessonNumber: Int, val title: String?, val paragraphs: List<String>)

    companion object {
        const val COURSE_ID = "university-russian-1"
        const val COURSE_1_ID = "university-russian-1"
        const val COURSE_2_ID = "university-russian-2"
        fun lessonId(number: Int) = "ur1-lesson-$number"
        fun lessonId(courseId: String, number: Int) = if (courseId == COURSE_2_ID) "ur2-lesson-$number" else "ur1-lesson-$number"
        fun lessonNumber(lessonId: String) = lessonId.substringAfterLast('-').toIntOrNull()
    }
}
