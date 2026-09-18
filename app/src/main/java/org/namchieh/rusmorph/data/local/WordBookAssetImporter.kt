package org.namchieh.rusmorph.data.local

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.namchieh.rusmorph.data.repository.WordBookCatalog

/** Runs after lexicon initialization on fresh installs and upgrades; versioned and idempotent. */
class WordBookAssetImporter(private val context: Context, private val database: RusMorphDatabase) {
    suspend fun importIfNeeded() = withContext(Dispatchers.IO) {
        val catalog = context.assets.open("database/wordbooks.json").bufferedReader().use { Gson().fromJson(it, WordBookCatalog::class.java) }
        require(catalog.schemaVersion == 1)
        require(catalog.books.map { it.book.id }.distinct().size == catalog.books.size)
        database.withTransaction {
            val dao = database.wordBookDao()
            for (asset in catalog.books) {
                val book = asset.book.toDomain()
                val existing = dao.wordBooks().firstOrNull { it.id == book.id }
                if (existing != null && existing.version >= book.version) continue
                require(asset.lessons.size == book.lessonCount)
                require(asset.lessons.map { it.id }.distinct().size == asset.lessons.size)
                val references = asset.lessons.flatMap { it.words }.map { it.entryId }.toSet()
                val found = references.toList().chunked(400).flatMap { database.searchDao().entriesByIds(it) }.map { it.entry.id }.toSet()
                require(found == references) { "Word book references missing lexicon entries" }
                dao.clear_word_book_dialogue_lines(book.id)
                dao.clear_word_book_text_paragraphs(book.id)
                dao.clear_word_book_dialogues(book.id)
                dao.clear_word_book_texts(book.id)
                dao.clear_word_book_lesson_words(book.id)
                dao.clear_word_book_lessons(book.id)
                dao.upsertWordBook(WordBookEntity(book.id, book.title, book.subtitle, book.description.orEmpty(), book.cover ?: book.coverUri ?: book.coverResourceName, "BUILT_IN", book.version, System.currentTimeMillis()))
                dao.upsertLessons(asset.lessons.map { WordBookLessonEntity(it.id, book.id, it.number, it.titleRu, it.titleZh) })
                for (lesson in asset.lessons) {
                    dao.upsertLessonWords(lesson.words.map { WordBookLessonWordEntity(book.id, lesson.id, it.entryId, it.order, it.isKey) })
                    dao.upsertDialogues(lesson.dialogues.mapIndexed { i, d -> WordBookDialogueEntity(d.id, book.id, lesson.id, d.title, d.description, i) })
                    lesson.dialogues.forEach { d -> dao.upsertDialogueLines(d.lines.map { WordBookDialogueLineEntity(it.id, d.id, it.speaker, it.text, it.translation, it.audio, it.order, book.id, lesson.id) }) }
                    dao.upsertTexts(lesson.texts.mapIndexed { i, t -> WordBookTextEntity(t.id, book.id, lesson.id, t.title, t.translationTitle, i) })
                    lesson.texts.forEach { t -> dao.upsertTextParagraphs(t.paragraphs.map { WordBookTextParagraphEntity(it.id, t.id, it.text, it.translation, it.audio, it.order, book.id, lesson.id) }) }
                }
            }
        }
    }
}
