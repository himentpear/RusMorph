package org.namchieh.rusmorph.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.namchieh.rusmorph.data.local.LexiconEntryWithDetails
import org.namchieh.rusmorph.data.local.WordBookDao
import org.namchieh.rusmorph.domain.learning.Dialogue
import org.namchieh.rusmorph.domain.learning.DialogueLine
import org.namchieh.rusmorph.domain.learning.LearningUnit
import org.namchieh.rusmorph.domain.learning.LearningUnitType
import org.namchieh.rusmorph.domain.learning.Lesson
import org.namchieh.rusmorph.domain.learning.TextContent
import org.namchieh.rusmorph.domain.learning.TextParagraph
import org.namchieh.rusmorph.domain.learning.WordBook
import org.namchieh.rusmorph.domain.learning.WordBookSourceType

/**
 * Stable boundary for built-in, imported, or remote word books. Every lesson exposes
 * words, dialogues, and texts independently; absent verified content is an empty list.
 */
interface WordBookRepository {
    suspend fun wordBooks(): List<WordBook>
    suspend fun wordBook(wordBookId: String): WordBook?
    suspend fun lessons(wordBookId: String): List<Lesson>
    suspend fun lesson(wordBookId: String, lessonId: String): Lesson?
    suspend fun lessonWords(wordBookId: String, lessonId: String): List<LexiconEntryWithDetails>
    suspend fun lessonDialogues(wordBookId: String, lessonId: String): List<Dialogue>
    suspend fun lessonTexts(wordBookId: String, lessonId: String): List<TextContent>
    suspend fun dialogue(dialogueId: String): Dialogue?
    suspend fun text(textId: String): TextContent?
    suspend fun dialogue(wordBookId: String, lessonId: String, dialogueId: String): Dialogue? = lessonDialogues(wordBookId, lessonId).singleOrNull { it.id == dialogueId }
    suspend fun text(wordBookId: String, lessonId: String, textId: String): TextContent? = lessonTexts(wordBookId, lessonId).singleOrNull { it.id == textId }
    suspend fun getWordBooks() = wordBooks()
    suspend fun getWordBook(wordBookId: String) = wordBook(wordBookId)
    suspend fun getLessons(wordBookId: String) = lessons(wordBookId)
    suspend fun getLesson(wordBookId: String, lessonId: String) = lesson(wordBookId, lessonId)
    suspend fun getLessonWords(wordBookId: String, lessonId: String) = lessonWords(wordBookId, lessonId).mapIndexed { i, entry -> org.namchieh.rusmorph.domain.learning.LessonWord(wordBookId, lessonId, entry.entry.id, i, false, entry) }
    suspend fun getLessonDialogues(wordBookId: String, lessonId: String) = lessonDialogues(wordBookId, lessonId)
    suspend fun getLessonTexts(wordBookId: String, lessonId: String) = lessonTexts(wordBookId, lessonId)

}

data class WordBookCatalog(val schemaVersion: Int, val books: List<WordBookAsset>)
data class WordBookAsset(val book: WordBookAssetMetadata, val lessons: List<WordBookLessonAsset>)
// Deserialize optional JSON fields into nullable DTOs: Gson does not run Kotlin defaults.
data class WordBookAssetMetadata(
    val id: String, val title: String, val lessonCount: Int, val version: Int,
    val description: String?, val subtitle: String?, val cover: String?,
    val coverResourceName: String?, val coverUri: String?, val sourceType: WordBookSourceType?,
) {
    fun toDomain() = WordBook(id = id, title = title, lessonCount = lessonCount, version = version,
        description = description, subtitle = subtitle.orEmpty(), coverResourceName = coverResourceName,
        coverUri = coverUri, cover = cover ?: coverUri ?: coverResourceName,
        sourceType = sourceType ?: WordBookSourceType.BUILT_IN)
}
data class WordBookLessonAsset(val id: String, val number: Int, val titleRu: String?, val titleZh: String?, val words: List<WordReference>, val dialogues: List<Dialogue>, val texts: List<TextContent>)
data class WordReference(val entryId: String, val order: Int, val isKey: Boolean)

class AssetWordBookDataSource(
    private val context: Context,
    private val searchRepository: SearchRepository,
    private val gson: Gson = Gson(),
) : WordBookRepository {
    private val catalog by lazy {
        context.assets.open("database/wordbooks.json").bufferedReader().use {
            gson.fromJson(it, WordBookCatalog::class.java).also { catalog ->
                require(catalog.schemaVersion == 1)
                require(catalog.books.map { it.book.id }.distinct().size == catalog.books.size)
                catalog.books.forEach { asset ->
                    require(asset.book.lessonCount == asset.lessons.size)
                    require(asset.lessons.map { it.id }.distinct().size == asset.lessons.size)
                }
            }
        }
    }
    private suspend fun asset(id: String) = withContext(Dispatchers.IO) { catalog.books.firstOrNull { it.book.id == id } }
    private suspend fun lessonAsset(book: String, lesson: String) = asset(book)?.lessons?.firstOrNull { it.id == lesson }
    override suspend fun wordBooks() = withContext(Dispatchers.IO) { catalog.books.map { it.book.toDomain() } }
    override suspend fun wordBook(wordBookId: String) = asset(wordBookId)?.book?.toDomain()
    override suspend fun lessons(wordBookId: String) = asset(wordBookId)?.lessons.orEmpty().map { l ->
        val units = buildList {
            if (l.words.isNotEmpty()) add(LearningUnit("$wordBookId/${l.id}/words", l.id, LearningUnitType.VOCABULARY, "СЛОВА", "本课单词", l.words.size))
            l.dialogues.forEach { add(LearningUnit(it.id, l.id, LearningUnitType.DIALOGUE, "ДИАЛОГ", it.title, it.lines.size, sourceId = it.id)) }
            l.texts.forEach { add(LearningUnit(it.id, l.id, LearningUnitType.TEXT, "ТЕКСТ", it.title, it.paragraphs.size, sourceId = it.id)) }
        }
        Lesson(l.id, wordBookId, l.number, l.titleRu, l.titleZh, units)
    }
    override suspend fun lesson(wordBookId: String, lessonId: String) = lessons(wordBookId).firstOrNull { it.id == lessonId }
    override suspend fun getLessonWords(wordBookId: String, lessonId: String): List<org.namchieh.rusmorph.domain.learning.LessonWord> {
        val refs = lessonAsset(wordBookId, lessonId)?.words.orEmpty().sortedBy { it.order }
        val entries = searchRepository.entriesByIds(refs.map { it.entryId }).associateBy { it.entry.id }
        return refs.map { ref -> org.namchieh.rusmorph.domain.learning.LessonWord(wordBookId, lessonId, ref.entryId, ref.order, ref.isKey, requireNotNull(entries[ref.entryId]) { "Missing lexicon entry ${ref.entryId}" }) }
    }
    override suspend fun lessonWords(wordBookId: String, lessonId: String) = getLessonWords(wordBookId, lessonId).map { it.entry }
    override suspend fun lessonDialogues(wordBookId: String, lessonId: String) = lessonAsset(wordBookId, lessonId)?.dialogues.orEmpty()
    override suspend fun lessonTexts(wordBookId: String, lessonId: String) = lessonAsset(wordBookId, lessonId)?.texts.orEmpty()
    override suspend fun dialogue(dialogueId: String) = withContext(Dispatchers.IO) { catalog.books.flatMap { it.lessons }.flatMap { it.dialogues }.singleOrNull { it.id == dialogueId } }
    override suspend fun text(textId: String) = withContext(Dispatchers.IO) { catalog.books.flatMap { it.lessons }.flatMap { it.texts }.singleOrNull { it.id == textId } }
    companion object {
        const val WORD_BOOK_ID = "university-russian-1"
        fun lessonId(number: Int) = "ur1-lesson-$number"
    }
}
typealias AssetWordBookRepository = AssetWordBookDataSource

/** Makes imported/custom Room content immediately visible through the same app contract. */
class RoomWordBookDataSource(
    private val dao: WordBookDao,
    private val searchRepository: SearchRepository,
) : WordBookRepository {
    override suspend fun wordBooks(): List<WordBook> = dao.wordBooks().map { entity ->
        WordBook(
            id = entity.id,
            title = entity.title,
            subtitle = entity.subtitle,
            description = entity.description,
            lessonCount = dao.lessons(entity.id).size,
            coverUri = entity.coverUri,
            sourceType = runCatching { WordBookSourceType.valueOf(entity.sourceType) }.getOrDefault(WordBookSourceType.IMPORTED),
            version = entity.version,
        )
    }

    override suspend fun wordBook(wordBookId: String): WordBook? = wordBooks().firstOrNull { it.id == wordBookId }

    override suspend fun lessons(wordBookId: String): List<Lesson> = dao.lessons(wordBookId).map { entity ->
        val words = lessonWords(wordBookId, entity.id)
        val dialogues = lessonDialogues(wordBookId, entity.id)
        val texts = lessonTexts(wordBookId, entity.id)
        val units = buildList {
            if (words.isNotEmpty()) add(LearningUnit("${entity.id}-vocabulary", entity.id, LearningUnitType.VOCABULARY, "СЛОВА", "单词", words.size))
            dialogues.forEach { add(LearningUnit(it.id, entity.id, LearningUnitType.DIALOGUE, "ДИАЛОГ", it.title, it.lines.size, sourceId = it.id)) }
            texts.forEach { add(LearningUnit(it.id, entity.id, LearningUnitType.TEXT, "ТЕКСТ", it.title, it.paragraphs.size, sourceId = it.id)) }
        }
        Lesson(entity.id, entity.wordBookId, entity.number, entity.titleRu, entity.titleZh, units)
    }

    override suspend fun lesson(wordBookId: String, lessonId: String): Lesson? = lessons(wordBookId).firstOrNull { it.id == lessonId }

    override suspend fun getLessonWords(wordBookId: String, lessonId: String): List<org.namchieh.rusmorph.domain.learning.LessonWord> {
        val refs = dao.lessonWords(wordBookId, lessonId)
        val entries = searchRepository.entriesByIds(refs.map { it.entryId }).associateBy { it.entry.id }
        return refs.mapNotNull { ref -> entries[ref.entryId]?.let { org.namchieh.rusmorph.domain.learning.LessonWord(wordBookId, lessonId, ref.entryId, ref.position, ref.isKey, it) } }
    }
    override suspend fun lessonWords(wordBookId: String, lessonId: String): List<LexiconEntryWithDetails> {
        val ids = dao.lessonWordIds(wordBookId, lessonId)
        val byId = searchRepository.entriesByIds(ids).associateBy { it.entry.id }
        return ids.mapNotNull(byId::get)
    }

    override suspend fun lessonDialogues(wordBookId: String, lessonId: String): List<Dialogue> =
        dao.dialogues(wordBookId, lessonId).map { it.toDomain() }

    override suspend fun lessonTexts(wordBookId: String, lessonId: String): List<TextContent> =
        dao.texts(wordBookId, lessonId).map { it.toDomain() }

    override suspend fun dialogue(dialogueId: String): Dialogue? = wordBooks().flatMap { b -> lessons(b.id).flatMap { l -> lessonDialogues(b.id, l.id) } }.singleOrNull { it.id == dialogueId }
    override suspend fun text(textId: String): TextContent? = wordBooks().flatMap { b -> lessons(b.id).flatMap { l -> lessonTexts(b.id, l.id) } }.singleOrNull { it.id == textId }

    private suspend fun org.namchieh.rusmorph.data.local.WordBookDialogueEntity.toDomain() = Dialogue(
        id, lessonId, title, description,
        dao.dialogueLines(wordBookId, lessonId, id).map { DialogueLine(it.id, it.speaker, it.text, it.translation, it.audio, it.position) }, wordBookId,
    )

    private suspend fun org.namchieh.rusmorph.data.local.WordBookTextEntity.toDomain() = TextContent(
        id, lessonId, title, translationTitle,
        dao.textParagraphs(wordBookId, lessonId, id).map { TextParagraph(it.id, it.position, it.text, it.translation, it.audio) }, wordBookId,
    )
}

typealias RoomWordBookRepository = RoomWordBookDataSource

class CompositeWordBookRepository(
    private val builtIn: WordBookRepository,
    private val imported: WordBookRepository,
) : WordBookRepository {
    override suspend fun wordBooks(): List<WordBook> {
        return (builtIn.wordBooks() + imported.wordBooks()).groupBy { it.id }.values.map { versions -> versions.maxBy { it.version } }
    }

    override suspend fun wordBook(wordBookId: String) = wordBooks().firstOrNull { it.id == wordBookId }
    private suspend fun source(wordBookId: String): WordBookRepository {
        val local = imported.wordBook(wordBookId)
        val asset = builtIn.wordBook(wordBookId)
        return if (local != null && (asset == null || local.version > asset.version)) imported else builtIn
    }
    override suspend fun getLessonWords(wordBookId: String, lessonId: String) = source(wordBookId).getLessonWords(wordBookId, lessonId)
    override suspend fun lessons(wordBookId: String) = source(wordBookId).lessons(wordBookId)
    override suspend fun lesson(wordBookId: String, lessonId: String) = source(wordBookId).lesson(wordBookId, lessonId)
    override suspend fun lessonWords(wordBookId: String, lessonId: String) = source(wordBookId).lessonWords(wordBookId, lessonId)
    override suspend fun lessonDialogues(wordBookId: String, lessonId: String) = source(wordBookId).lessonDialogues(wordBookId, lessonId)
    override suspend fun lessonTexts(wordBookId: String, lessonId: String) = source(wordBookId).lessonTexts(wordBookId, lessonId)
    override suspend fun dialogue(dialogueId: String): Dialogue? {
        val matches = wordBooks().flatMap { book -> lessons(book.id).flatMap { lesson -> lessonDialogues(book.id, lesson.id) } }
        return matches.singleOrNull { it.id == dialogueId }
    }
    override suspend fun text(textId: String): TextContent? {
        val matches = wordBooks().flatMap { book -> lessons(book.id).flatMap { lesson -> lessonTexts(book.id, lesson.id) } }
        return matches.singleOrNull { it.id == textId }
    }
}
