package org.namchieh.rusmorph.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.namchieh.rusmorph.domain.learning.Dialogue
import org.namchieh.rusmorph.domain.learning.DialogueLine
import org.namchieh.rusmorph.domain.learning.TextContent
import org.namchieh.rusmorph.domain.learning.TextParagraph
import org.namchieh.rusmorph.domain.learning.TextSentence

/** Read the validated second-volume source records without changing the v0.006 Room schema. */
internal class UniversityRussian2Assets(private val context: Context) {
    private val gson = Gson()
    private val root = "database/textbook/university_russian_2"

    private inline fun <reified T> read(name: String): List<T> =
        context.assets.open("$root/$name.json").bufferedReader().use { reader ->
            gson.fromJson(reader, object : TypeToken<List<T>>() {}.type)
        }

    val lessons: List<LessonAsset> by lazy { read<LessonAsset>("lessons").sortedBy { it.lessonNumber } }
    private val sections: List<SectionAsset> by lazy { read("sections") }
    private val paragraphs: List<ParagraphAsset> by lazy { read("paragraphs") }
    private val sentences: List<SentenceAsset> by lazy { read("sentences") }
    private val translations: List<TranslationAsset> by lazy { read("translations") }
    private val speakers: List<SpeakerAsset> by lazy { read("speakers") }

    fun readingCount(number: Int): Int {
        val lesson = sourceLessonId(number)
        val readingSections = sections.filter { it.lessonId == lesson && it.type == "READING" }.map { it.id }.toSet()
        val paragraphIds = paragraphs.filter { it.sectionId in readingSections }.map { it.id }.toSet()
        return sentences.count { it.paragraphId in paragraphIds }
    }

    fun dialogueCount(number: Int): Int {
        val lesson = sourceLessonId(number)
        val dialogueSections = sections.filter { it.lessonId == lesson && it.type == "DIALOGUE" }.map { it.id }.toSet()
        return paragraphs.count { it.sectionId in dialogueSections }
    }

    fun reading(number: Int, appLessonId: String): TextContent? {
        val lesson = sourceLessonId(number)
        val section = sections.firstOrNull { it.lessonId == lesson && it.type == "READING" } ?: return null
        val sourceParagraphs = paragraphs.filter { it.sectionId == section.id }.sortedBy { it.order }
        if (sourceParagraphs.isEmpty()) return null
        val sentencesByParagraph = sentences.groupBy { it.paragraphId }
        val sentenceTranslations = translations.filter { it.targetType == "SENTENCE" && it.language == "zh-CN" }
            .associateBy { it.targetId }
        return TextContent(
            id = "text-$appLessonId",
            lessonId = appLessonId,
            title = section.titleRu,
            translationTitle = section.titleZh,
            paragraphs = sourceParagraphs.map { paragraph ->
                TextParagraph(
                    id = paragraph.id,
                    order = paragraph.order,
                    sentences = sentencesByParagraph[paragraph.id].orEmpty().sortedBy { it.order }.map { sentence ->
                        TextSentence(
                            id = sentence.id,
                            order = sentence.order,
                            text = sentence.textRu,
                            translation = sentenceTranslations[sentence.id]?.text,
                        )
                    },
                )
            },
        )
    }

    fun dialogue(number: Int, appLessonId: String): Dialogue? {
        val lesson = sourceLessonId(number)
        val section = sections.firstOrNull { it.lessonId == lesson && it.type == "DIALOGUE" } ?: return null
        val sourceLines = paragraphs.filter { it.sectionId == section.id }.sortedBy { it.order }
        if (sourceLines.isEmpty()) return null
        val speakerNames = speakers.associate { it.id to it.displayName }
        return Dialogue(
            id = "dialogue-$appLessonId",
            lessonId = appLessonId,
            title = section.titleRu,
            description = section.titleZh,
            lines = sourceLines.map { line ->
                DialogueLine(line.id, line.speakerId?.let(speakerNames::get), line.textRu, null, null, line.order)
            },
        )
    }

    private fun sourceLessonId(number: Int) = "ur2_lesson_${number.toString().padStart(2, '0')}"

    data class LessonAsset(val id: String, val lessonNumber: Int, val titleRu: String, val titleZh: String)
    private data class SectionAsset(val id: String, val lessonId: String, val type: String, val titleRu: String, val titleZh: String)
    private data class ParagraphAsset(val id: String, val sectionId: String, val order: Int, val speakerId: String?, val textRu: String)
    private data class SentenceAsset(val id: String, val paragraphId: String, val order: Int, val textRu: String)
    private data class TranslationAsset(val targetType: String, val targetId: String, val language: String, val text: String)
    private data class SpeakerAsset(val id: String, val displayName: String)
}
