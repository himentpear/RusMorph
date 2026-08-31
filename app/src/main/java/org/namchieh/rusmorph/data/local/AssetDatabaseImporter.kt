package org.namchieh.rusmorph.data.local

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.io.FileNotFoundException
import com.google.gson.reflect.TypeToken
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.namchieh.rusmorph.BuildConfig

private const val DATA_VERSION_KEY = "database_asset_version"
private const val BATCH_SIZE = 500

sealed interface InitializationState {
    data object NotStarted : InitializationState
    data object Initializing : InitializationState
    data class Ready(val importedNewVersion: Boolean) : InitializationState
    data class Failed(val reason: InitializationFailureReason) : InitializationState
}

enum class InitializationFailureReason {
    ASSET_MISSING,
    INVALID_DATA,
    DATABASE_WRITE,
    UNKNOWN,
}

interface DataInitializer {
    val state: StateFlow<InitializationState>
    suspend fun initialize()
}

class AssetDatabaseImporter(
    private val context: Context,
    private val database: RusMorphDatabase,
    private val gson: Gson = Gson(),
    private val assetReader: ((String) -> ByteArray)? = null,
    private val failureInjector: () -> Unit = {},
) : DataInitializer {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<InitializationState>(InitializationState.NotStarted)
    override val state: StateFlow<InitializationState> = mutableState.asStateFlow()

    override suspend fun initialize() {
        mutex.withLock {
            if (mutableState.value is InitializationState.Ready) return
            mutableState.value = InitializationState.Initializing
            try {
                val imported = importIfNeeded()
                mutableState.value = InitializationState.Ready(imported)
            } catch (exception: Exception) {
                if (BuildConfig.DEBUG) Log.e("RusMorphImporter", "Local data import failed", exception)
                mutableState.value = InitializationState.Failed(exception.toFailureReason())
            }
        }
    }

    suspend fun importIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        val assetBytes = DATABASE_ASSETS.associateWith(::readAsset)
        val version = calculateVersion(assetBytes)
        val dao = database.dataImportDao()
        if (dao.metadataValue(DATA_VERSION_KEY) == version) return@withContext false

        val bundle = parseBundle(assetBytes)
        database.withTransaction {
            if (dao.metadataValue(DATA_VERSION_KEY) == version) return@withTransaction
            dao.clearCrossRefs()
            dao.clearSources()
            dao.clearPartsOfSpeech()
            dao.clearAnnotations()
            dao.clearSearchForms()
            dao.clearDeclensionRules()
            dao.clearKnowledgeChunks()
            dao.clearLexiconEntries()
            failureInjector()

            bundle.entries.chunked(BATCH_SIZE).forEach { dao.insertLexiconEntries(it) }
            bundle.searchForms.chunked(BATCH_SIZE).forEach { dao.insertSearchForms(it) }
            bundle.partsOfSpeech.chunked(BATCH_SIZE).forEach { dao.insertPartsOfSpeech(it) }
            bundle.annotations.chunked(BATCH_SIZE).forEach { dao.insertAnnotations(it) }
            bundle.sources.chunked(BATCH_SIZE).forEach { dao.insertSources(it) }
            bundle.declensionRules.chunked(BATCH_SIZE).forEach { dao.insertDeclensionRules(it) }
            bundle.knowledgeChunks.chunked(BATCH_SIZE).forEach { dao.insertKnowledgeChunks(it) }
            bundle.crossRefs.chunked(BATCH_SIZE).forEach {
                dao.insertEntryKnowledgeCrossRefs(it)
            }
            verifyImportedData(dao, bundle)
            dao.upsertMetadata(AppMetadataEntity(DATA_VERSION_KEY, version))
        }
        true
    }

    private fun readAsset(name: String): ByteArray =
        assetReader?.invoke(name)
            ?: context.assets.open("database/$name").use { it.readBytes() }

    private suspend fun verifyImportedData(dao: DataImportDao, bundle: ImportBundle) {
        val countsMatch = dao.lexiconEntryCount() == bundle.entries.size &&
            dao.searchFormCount() == bundle.searchForms.size &&
            dao.partOfSpeechCount() == bundle.partsOfSpeech.size &&
            dao.annotationCount() == bundle.annotations.size &&
            dao.sourceCount() == bundle.sources.size &&
            dao.declensionRuleCount() == bundle.declensionRules.size &&
            dao.knowledgeChunkCount() == bundle.knowledgeChunks.size &&
            dao.crossRefCount() == bundle.crossRefs.size
        if (!countsMatch) throw SQLiteException("Imported data count mismatch")
        database.query("PRAGMA foreign_key_check", emptyArray()).use { cursor ->
            if (cursor.moveToFirst()) throw SQLiteException("Imported data violates foreign keys")
        }
    }

    private fun calculateVersion(assets: Map<String, ByteArray>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        assets.toSortedMap().forEach { (name, bytes) ->
            digest.update(name.toByteArray(StandardCharsets.UTF_8))
            digest.update(0.toByte())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun parseBundle(assets: Map<String, ByteArray>): ImportBundle {
        val lexiconType = object : TypeToken<List<AssetLexiconEntry>>() {}.type
        val knowledgeType = object : TypeToken<List<AssetKnowledgeChunk>>() {}.type
        val lexicon: List<AssetLexiconEntry> = gson.fromJson(
            assets.getValue("lexicon.json").toString(StandardCharsets.UTF_8),
            lexiconType,
        )
        val declension: AssetDeclensionFile = gson.fromJson(
            assets.getValue("declension_rules.json").toString(StandardCharsets.UTF_8),
            AssetDeclensionFile::class.java,
        )
        val knowledge: List<AssetKnowledgeChunk> = gson.fromJson(
            assets.getValue("knowledge_chunks.json").toString(StandardCharsets.UTF_8),
            knowledgeType,
        )
        val knownChunkIds = knowledge.mapTo(mutableSetOf()) { it.id }

        return ImportBundle(
            entries = lexicon.map(AssetLexiconEntry::toEntity),
            searchForms = lexicon.flatMap { item ->
                item.searchForms.distinct().map { EntrySearchFormEntity(item.id, it) }
            },
            partsOfSpeech = lexicon.flatMap { item ->
                item.partsOfSpeech.distinct().map { EntryPartOfSpeechEntity(item.id, it) }
            },
            annotations = lexicon.flatMap { item ->
                item.annotations.orEmpty().distinctBy { "${it.fieldName}\u0000${it.normalizedValue}" }.map {
                    EntryAnnotationEntity(item.id, it.fieldName, it.value, it.normalizedValue)
                }
            },
            sources = lexicon.flatMap { item ->
                val sources = item.provenance.ifEmpty {
                    listOf(AssetSource(item.sourceWorkbook, item.sourceSheet, item.sourceRow))
                }
                sources.distinct().map {
                    EntrySourceEntity(item.id, it.sourceWorkbook, it.sourceSheet, it.sourceRow)
                }
            },
            declensionRules = declension.rules.map(AssetDeclensionRule::toEntity),
            knowledgeChunks = knowledge.map { it.toEntity(gson) },
            crossRefs = lexicon.flatMap { item ->
                item.relatedKnowledgeChunkIds
                    .filter(knownChunkIds::contains)
                    .distinct()
                    .map { EntryKnowledgeCrossRef(item.id, it) }
            },
        )
    }

    private companion object {
        val DATABASE_ASSETS = listOf(
            "lexicon.json",
            "declension_rules.json",
            "knowledge_chunks.json",
        )
    }
}

private fun Exception.toFailureReason(): InitializationFailureReason = when (this) {
    is FileNotFoundException -> InitializationFailureReason.ASSET_MISSING
    is JsonParseException -> InitializationFailureReason.INVALID_DATA
    is SQLiteException -> InitializationFailureReason.DATABASE_WRITE
    else -> InitializationFailureReason.UNKNOWN
}

private data class ImportBundle(
    val entries: List<LexiconEntryEntity>,
    val searchForms: List<EntrySearchFormEntity>,
    val partsOfSpeech: List<EntryPartOfSpeechEntity>,
    val annotations: List<EntryAnnotationEntity>,
    val sources: List<EntrySourceEntity>,
    val declensionRules: List<DeclensionRuleEntity>,
    val knowledgeChunks: List<KnowledgeChunkEntity>,
    val crossRefs: List<EntryKnowledgeCrossRef>,
)

private data class AssetLexiconEntry(
    val id: String,
    val lesson: Int? = null,
    val sequence: Int? = null,
    val displayForm: String,
    val lemma: String,
    val normalizedLemma: String,
    val searchForms: List<String> = emptyList(),
    val chineseMeaning: String? = null,
    val partsOfSpeech: List<String> = emptyList(),
    val annotations: List<AssetAnnotation>? = emptyList(),
    val gender: String? = null,
    val declensionClass: String? = null,
    val endingType: String? = null,
    val pluralStressPattern: String? = null,
    val aspect: String? = null,
    val conjugationClass: String? = null,
    val phoneticAlternation: String? = null,
    val sourceWorkbook: String,
    val sourceSheet: String,
    val sourceRow: Int,
    val provenance: List<AssetSource> = emptyList(),
    val relatedKnowledgeChunkIds: List<String> = emptyList(),
) {
    fun toEntity() = LexiconEntryEntity(
        id = id,
        lesson = lesson,
        sequence = sequence,
        displayForm = displayForm,
        lemma = lemma,
        normalizedLemma = normalizedLemma,
        chineseMeaning = chineseMeaning,
        gender = gender,
        declensionClass = declensionClass,
        endingType = endingType,
        pluralStressPattern = pluralStressPattern,
        aspect = aspect,
        conjugationClass = conjugationClass,
        phoneticAlternation = phoneticAlternation,
        sourceWorkbook = sourceWorkbook,
        sourceSheet = sourceSheet,
        sourceRow = sourceRow,
    )
}

private data class AssetSource(
    val sourceWorkbook: String,
    val sourceSheet: String,
    val sourceRow: Int,
)

private data class AssetAnnotation(
    val fieldName: String,
    val value: String,
    val normalizedValue: String,
)

private data class AssetDeclensionFile(
    val rules: List<AssetDeclensionRule> = emptyList(),
)

private data class AssetDeclensionRule(
    val id: String,
    val category: String,
    val gender: String? = null,
    val endingType: String? = null,
    val number: String? = null,
    val caseName: String,
    val resultEnding: String,
    val description: String,
    val sourceWorkbook: String? = null,
    val sourceSheet: String,
    val sourceCellRange: String,
) {
    fun toEntity() = DeclensionRuleEntity(
        id,
        category,
        gender,
        endingType,
        number,
        caseName,
        resultEnding,
        description,
        sourceWorkbook,
        sourceSheet,
        sourceCellRange,
    )
}

private data class AssetKnowledgeChunk(
    val id: String,
    val title: String,
    val category: String,
    val keywords: List<String> = emptyList(),
    val content: String,
    val examples: List<String> = emptyList(),
    val sourceDocument: String,
    val sectionPath: List<String> = emptyList(),
) {
    fun toEntity(gson: Gson) = KnowledgeChunkEntity(
        id = id,
        title = title,
        category = category,
        keywordsJson = gson.toJson(keywords),
        content = content,
        examplesJson = gson.toJson(examples),
        sourceDocument = sourceDocument,
        sectionPathJson = gson.toJson(sectionPath),
    )
}
