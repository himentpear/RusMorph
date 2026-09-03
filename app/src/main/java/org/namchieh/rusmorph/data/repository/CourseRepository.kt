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

class CourseRepository(
    private val context: Context,
    private val searchRepository: SearchRepository,
    private val gson: Gson = Gson(),
) {
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
        description = "第二册教材课程，收录全册 947 个词汇及课文对话占位。",
        lessonCount = 12,
    )

    suspend fun courses(): List<Course> = listOf(course1, course2)

    suspend fun course(courseId: String): Course? = courses().firstOrNull { it.id == courseId }

    suspend fun lessons(courseId: String): List<Lesson> = withContext(Dispatchers.IO) {
        val targetCourse = course(courseId) ?: return@withContext emptyList()
        val dialogues = dialogues().associateBy { it.lessonId }
        val isBook2 = (courseId == COURSE_2_ID)

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
                    add(unit(lessonId, LearningUnitType.DIALOGUE, 0))
                } else {
                    dialogues[lessonId]?.let { add(unit(lessonId, LearningUnitType.DIALOGUE, it.lines.size)) }
                }
                if (!isBook2 && number == 8) add(unit(lessonId, LearningUnitType.REVIEW, 0))
            }
            Lesson(
                id = lessonId,
                courseId = courseId,
                number = number,
                titleRu = if (!isBook2 && number == 8) "ПОВТОРЕНИЕ" else "УРОК $number",
                titleZh = if (!isBook2 && number == 8) "复习" else "第 $number 课",
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

    suspend fun dialogue(dialogueId: String): Dialogue? {
        if (dialogueId.contains("ur2") || dialogueId.contains(COURSE_2_ID)) {
            val lessonNumber = dialogueId.substringAfterLast('-').toIntOrNull() ?: 1
            return placeholderDialogue(COURSE_2_ID, lessonNumber)
        }
        val number = dialogueId.substringAfterLast('-').toIntOrNull()
        return dialogues().firstOrNull { it.id == dialogueId || (number != null && it.id == "dialogue-$number") }
    }

    suspend fun dialogueForLesson(lessonId: String): Dialogue? {
        if (lessonId.startsWith("ur2-")) {
            val lessonNumber = lessonNumber(lessonId) ?: 1
            return placeholderDialogue(COURSE_2_ID, lessonNumber)
        }
        return dialogues().firstOrNull { it.lessonId == lessonId }
    }

    private fun placeholderDialogue(courseId: String, lessonNumber: Int): Dialogue {
        val lessonId = lessonId(courseId, lessonNumber)
        return Dialogue(
            id = "dialogue-$lessonId",
            lessonId = lessonId,
            title = "Диалог · Урок $lessonNumber",
            description = "第二册课文对话正在整理录入中",
            lines = emptyList(),
        )
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

    private fun unit(lessonId: String, type: LearningUnitType, count: Int): LearningUnit {
        val titles = when (type) {
            LearningUnitType.VOCABULARY -> "СЛОВА" to "词汇"
            LearningUnitType.DIALOGUE -> "ДИАЛОГ" to "对话"
            LearningUnitType.REVIEW -> "ПОВТОРЕНИЕ" to "复习"
            else -> type.name to type.name
        }
        return LearningUnit("$lessonId-${type.name.lowercase()}", lessonId, type, titles.first, titles.second, count)
    }

    private data class DialogueAsset(val lessonNumber: Int, val lines: List<String>)

    companion object {
        const val COURSE_ID = "university-russian-1"
        const val COURSE_1_ID = "university-russian-1"
        const val COURSE_2_ID = "university-russian-2"
        fun lessonId(number: Int) = "ur1-lesson-$number"
        fun lessonId(courseId: String, number: Int) = if (courseId == COURSE_2_ID) "ur2-lesson-$number" else "ur1-lesson-$number"
        fun lessonNumber(lessonId: String) = lessonId.substringAfterLast('-').toIntOrNull()
    }
}
