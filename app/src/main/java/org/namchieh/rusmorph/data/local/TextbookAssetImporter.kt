package org.namchieh.rusmorph.data.local

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class TextbookAsset(val id: String, val title: String, val language: String, val level: String, val sourceVersion: Int)
private data class LessonAsset(val id: String, val textbookId: String, val lessonNumber: Int, val title: String)
private data class SectionAsset(val id: String, val lessonId: String, val order: Int, val type: String, val title: String?)
private data class ParagraphAsset(val id: String, val sectionId: String, val order: Int, val content: String)

/** Imports only the verified structured assets emitted by tools/textbook_importer. */
class TextbookAssetImporter(private val context: Context, private val database: RusMorphDatabase) {
    suspend fun importIfNeeded() = withContext(Dispatchers.IO) {
        val gson = Gson()
        fun <T> read(name: String, token: TypeToken<T>): T = context.assets.open("database/$name").bufferedReader().use { gson.fromJson(it, token.type) }
        val textbooks = read("textbooks.json", object : TypeToken<List<TextbookAsset>>() {})
        val lessons = read("lessons.json", object : TypeToken<List<LessonAsset>>() {})
        val sections = read("sections.json", object : TypeToken<List<SectionAsset>>() {})
        val paragraphs = read("paragraphs.json", object : TypeToken<List<ParagraphAsset>>() {})
        require(textbooks.map { it.id }.distinct().size == textbooks.size)
        require(lessons.all { it.textbookId in textbooks.map { book -> book.id }.toSet() })
        require(sections.all { it.lessonId in lessons.map { lesson -> lesson.id }.toSet() })
        require(paragraphs.all { it.sectionId in sections.map { section -> section.id }.toSet() })
        database.withTransaction {
            val dao = database.textbookDao()
            dao.clearParagraphs(); dao.clearSections(); dao.clearLessons(); dao.clearTextbooks()
            dao.upsertTextbooks(textbooks.map { TextbookEntity(it.id, it.title, it.language, it.level, it.sourceVersion) })
            dao.upsertLessons(lessons.map { TextbookLessonEntity(it.id, it.textbookId, it.lessonNumber, it.title) })
            dao.upsertSections(sections.map { ReadingSectionEntity(it.id, it.lessonId, it.order, it.type, it.title) })
            dao.upsertParagraphs(paragraphs.map { ParagraphEntity(it.id, it.sectionId, it.order, it.content) })
        }
    }
}
