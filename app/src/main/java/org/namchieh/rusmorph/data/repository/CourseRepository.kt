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
    private val course = Course(
        id = COURSE_ID,
        title = "大学俄语 1",
        subtitle = "Русский язык · Том 1",
        description = "以教材课次为主轴，整合词汇、对话、朗读与复习。",
        lessonCount = 18,
    )

    suspend fun courses(): List<Course> = listOf(course)

    suspend fun course(courseId: String): Course? = courses().firstOrNull { it.id == courseId }

    suspend fun lessons(courseId: String): List<Lesson> = withContext(Dispatchers.IO) {
        if (courseId != COURSE_ID) return@withContext emptyList()
        val dialogues = dialogues().associateBy { it.lessonId }
        (1..course.lessonCount).map { number ->
            val lessonId = lessonId(number)
            val vocabularyCount = searchRepository.browseEntries(lesson = number, limit = 5_000).size
            val units = buildList {
                if (vocabularyCount > 0) add(unit(lessonId, LearningUnitType.VOCABULARY, vocabularyCount))
                dialogues[lessonId]?.let { add(unit(lessonId, LearningUnitType.DIALOGUE, it.lines.size)) }
                if (number == 8) add(unit(lessonId, LearningUnitType.REVIEW, 0))
            }
            Lesson(
                id = lessonId,
                courseId = courseId,
                number = number,
                titleRu = if (number == 8) "ПОВТОРЕНИЕ" else "УРОК $number",
                titleZh = if (number == 8) "复习" else "第 $number 课",
                units = units,
                isReviewLesson = number == 8,
            )
        }
    }

    suspend fun lesson(courseId: String, lessonId: String): Lesson? = lessons(courseId).firstOrNull { it.id == lessonId }
    suspend fun vocabulary(lessonId: String) = searchRepository.browseEntries(lesson = lessonNumber(lessonId), limit = 5_000)
    suspend fun dialogue(dialogueId: String): Dialogue? = dialogues().firstOrNull { it.id == dialogueId }
    suspend fun dialogueForLesson(lessonId: String): Dialogue? = dialogues().firstOrNull { it.lessonId == lessonId }

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
        fun lessonId(number: Int) = "ur1-lesson-$number"
        fun lessonNumber(lessonId: String) = lessonId.substringAfterLast('-').toIntOrNull()
    }
}
