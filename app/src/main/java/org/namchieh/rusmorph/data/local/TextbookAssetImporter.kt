package org.namchieh.rusmorph.data.local

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class TextbookAsset(val id: String, val title: String, val language: String, val level: String, val sourceVersion: Int)
private data class LessonAsset(val id: String, val textbookId: String, val lessonNumber: Int, val title: String)
private data class BlockAsset(val id: String, val lessonId: String, val order: Int, val type: String, val title: String?)
private data class SentenceAsset(val id: String, val lessonId: String, val blockId: String, val order: Int, val text: String, val sourceText: String)
private data class KnowledgeAsset(
    val id: String, val sentenceId: String, val type: String, val text: String, val label: String?,
    val explanation: String?, val example: String?, val start: Int, val end: Int, val status: String?,
    val knowledgeVersion: Int?, val generatedBy: String?, val reviewStatus: String?,
)

class TextbookAssetImporter(
    private val context: Context,
    private val database: RusMorphDatabase,
    private val assetDirectory: String = "database",
    private val openAsset: (String) -> java.io.Reader = { path -> context.assets.open(path).bufferedReader() },
) {
    suspend fun importIfNeeded() = withContext(Dispatchers.IO) {
        val gson = Gson()
        fun <T> read(name: String, token: TypeToken<T>): T = openAsset("$assetDirectory/$name").use { gson.fromJson(it, token.type) }
        val textbooks = read("textbooks.json", object : TypeToken<List<TextbookAsset>>() {})
        val lessons = read("lessons.json", object : TypeToken<List<LessonAsset>>() {})
        val blocks = read("blocks.json", object : TypeToken<List<BlockAsset>>() {})
        val sentences = read("sentences.json", object : TypeToken<List<SentenceAsset>>() {})
        val knowledge = read("knowledge.json", object : TypeToken<List<KnowledgeAsset>>() {})
        val textbookIds = textbooks.map { it.id }.toSet(); val lessonIds = lessons.map { it.id }.toSet()
        val blockIds = blocks.map { it.id }.toSet(); val sentenceIds = sentences.map { it.id }.toSet()
        val sentenceById = sentences.associateBy { it.id }; val knowledgeIds = knowledge.map { it.id }.toSet()
        require(textbookIds.size == textbooks.size && lessons.all { it.textbookId in textbookIds })
        require(blockIds.size == blocks.size && blocks.all { it.lessonId in lessonIds })
        require(sentenceIds.size == sentences.size && sentences.all { it.lessonId in lessonIds && it.blockId in blockIds })
        require(knowledgeIds.size == knowledge.size)
        require(knowledge.all {
            val sourceLength = sentenceById[it.sentenceId]?.sourceText?.length ?: return@all false
            it.type in setOf("WORD", "PHRASE", "GRAMMAR", "PATTERN", "PRONUNCIATION", "AI_NOTE") &&
                it.start >= 0 && it.end >= it.start && it.end <= sourceLength
        })
        database.withTransaction {
            val textbookDao = database.textbookDao(); val knowledgeDao = database.textbookKnowledgeDao()
            knowledgeDao.clearKnowledge(); textbookDao.clearSentences(); textbookDao.clearBlocks(); textbookDao.clearLessons(); textbookDao.clearTextbooks()
            textbookDao.upsertTextbooks(textbooks.map { TextbookEntity(it.id, it.title, it.language, it.level, it.sourceVersion) })
            textbookDao.upsertLessons(lessons.map { TextbookLessonEntity(it.id, it.textbookId, it.lessonNumber, it.title) })
            textbookDao.upsertBlocks(blocks.map { TextbookBlockEntity(it.id, it.lessonId, it.order, it.type, it.title) })
            textbookDao.upsertSentences(sentences.map { LessonSentenceEntity(it.id, it.lessonId, it.blockId, it.order, it.text, it.sourceText) })
            knowledgeDao.upsertKnowledge(knowledge.map {
                SentenceKnowledgeEntity(it.id, it.sentenceId, it.type, it.text, it.label, it.explanation, it.example,
                    it.start, it.end, it.status, it.knowledgeVersion ?: 1, it.generatedBy, it.reviewStatus)
            })
        }
    }
}
