package org.namchieh.rusmorph.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.namchieh.rusmorph.data.local.EntrySourceEntity
import org.namchieh.rusmorph.data.local.KnowledgeChunkEntity
import org.namchieh.rusmorph.data.local.LexiconEntryWithDetails
import org.namchieh.rusmorph.data.local.SearchDao
import org.namchieh.rusmorph.data.local.FuzzyCandidateRow
import org.namchieh.rusmorph.agent.MorphologyFilters
import org.namchieh.rusmorph.ui.WordDetailUiState
import org.namchieh.rusmorph.ui.LocalExplanationUiState

private val EXTRA_WHITESPACE = Regex("\\s+")

fun normalizeRussianForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFC)
        .replace("\u0301", "")
        .lowercase(Locale.ROOT)
        .replace(EXTRA_WHITESPACE, " ")
        .trim()

interface SearchDataSource {
    suspend fun search(
        normalizedQuery: String,
        rawQuery: String,
        partOfSpeech: String?,
        lesson: Int?,
        limit: Int,
    ): List<LexiconEntryWithDetails>

    suspend fun recentOrRecommended(limit: Int): List<LexiconEntryWithDetails>
    suspend fun browseEntries(partOfSpeech: String?, lesson: Int?, limit: Int): List<LexiconEntryWithDetails> = emptyList()
    suspend fun filteredEntries(
        partOfSpeech: String?,
        lesson: Int?,
        filters: MorphologyFilters,
        limit: Int,
    ): List<LexiconEntryWithDetails> =
        browseEntries(partOfSpeech, lesson, 2_000).filter { it.matches(filters) }.take(limit)
    suspend fun recentEntries(limit: Int): List<LexiconEntryWithDetails> = recentOrRecommended(limit)
    suspend fun getEntryCount(): Int = 0
    suspend fun fuzzyCandidates(partOfSpeech: String?, lesson: Int?, limit: Int): List<FuzzyCandidateRow> = emptyList()
    suspend fun entriesByIds(entryIds: List<String>): List<LexiconEntryWithDetails> = emptyList()
    fun observeEntry(entryId: String): Flow<LexiconEntryWithDetails?>
    suspend fun markViewed(entryId: String, viewedAt: Long)
    fun observePartOfSpeechOptions(): Flow<List<String>>
    fun observeLessonOptions(): Flow<List<Int>>
    fun observeKnowledgeChunk(chunkId: String): Flow<KnowledgeChunkEntity?>
}

class RoomSearchDataSource(private val dao: SearchDao) : SearchDataSource {
    override suspend fun search(
        normalizedQuery: String,
        rawQuery: String,
        partOfSpeech: String?,
        lesson: Int?,
        limit: Int,
    ) = dao.search(normalizedQuery, rawQuery, partOfSpeech, lesson, limit)

    override suspend fun recentOrRecommended(limit: Int) = dao.recentOrRecommended(limit)
    override suspend fun browseEntries(partOfSpeech: String?, lesson: Int?, limit: Int) =
        dao.browseEntries(partOfSpeech, lesson, limit)
    override suspend fun filteredEntries(
        partOfSpeech: String?,
        lesson: Int?,
        filters: MorphologyFilters,
        limit: Int,
    ) = dao.browseEntriesByMorphology(
        partOfSpeech = partOfSpeech,
        lesson = lesson,
        genders = filters.genders,
        genderCount = filters.genders.size,
        declensionClasses = filters.declensionClasses,
        declensionClassCount = filters.declensionClasses.size,
        endingTypes = filters.endingTypes,
        endingTypeCount = filters.endingTypes.size,
        aspects = filters.aspects,
        aspectCount = filters.aspects.size,
        conjugationClasses = filters.conjugationClasses,
        conjugationClassCount = filters.conjugationClasses.size,
        phoneticAlternations = filters.phoneticAlternations,
        phoneticAlternationCount = filters.phoneticAlternations.size,
        hasPhoneticAlternation = filters.hasPhoneticAlternation,
        hasPluralStressPattern = filters.hasPluralStressPattern,
        limit = limit,
    )
    override suspend fun recentEntries(limit: Int) = dao.recentEntries(limit)
    override suspend fun getEntryCount() = dao.entryCount()
    override suspend fun fuzzyCandidates(partOfSpeech: String?, lesson: Int?, limit: Int) =
        dao.fuzzyCandidateRows(partOfSpeech, lesson, limit)
    override suspend fun entriesByIds(entryIds: List<String>) = dao.entriesByIds(entryIds)
    override fun observeEntry(entryId: String) = dao.observeEntry(entryId)
    override suspend fun markViewed(entryId: String, viewedAt: Long) =
        dao.markViewed(entryId, viewedAt)
    override fun observePartOfSpeechOptions() = dao.observePartOfSpeechOptions()
    override fun observeLessonOptions() = dao.observeLessonOptions()
    override fun observeKnowledgeChunk(chunkId: String) = dao.observeKnowledgeChunk(chunkId)
}

class SearchRepository(
    private val dataSource: SearchDataSource,
    private val gson: Gson = Gson(),
) {
    /** Exact search remains the default; callers opt into this zero-result fallback. */
    suspend fun searchWithFuzzyFallback(
        query: String,
        partOfSpeech: String? = null,
        lesson: Int? = null,
        limit: Int = 50,
    ): SearchResponse {
        val exact = search(query, partOfSpeech, lesson, limit)
        if (exact.isNotEmpty() || query.isBlank()) return SearchResponse(exact)
        val fuzzy = fuzzySearch(query, partOfSpeech, lesson, limit)
        return SearchResponse(
            entries = fuzzy,
            redirectedFrom = query.takeIf { fuzzy.isNotEmpty() },
            redirectedTo = fuzzy.firstOrNull()?.entry?.displayForm,
            isFuzzyMatch = fuzzy.isNotEmpty(),
        )
    }

    suspend fun search(
        query: String,
        partOfSpeech: String? = null,
        lesson: Int? = null,
        limit: Int = 50,
    ): List<LexiconEntryWithDetails> {
        val raw = query.trim()
        if (raw.isEmpty()) return browseEntries(partOfSpeech, lesson, limit)
        return dataSource.search(
            normalizedQuery = normalizeRussianForSearch(raw),
            rawQuery = raw,
            partOfSpeech = partOfSpeech,
            lesson = lesson,
            limit = limit,
        )
    }

    suspend fun browseEntries(
        partOfSpeech: String? = null,
        lesson: Int? = null,
        limit: Int = 30,
    ): List<LexiconEntryWithDetails> = dataSource.browseEntries(partOfSpeech, lesson, limit)

    suspend fun filteredEntries(
        partOfSpeech: String? = null,
        lesson: Int? = null,
        filters: MorphologyFilters,
        limit: Int = 30,
    ): List<LexiconEntryWithDetails> =
        dataSource.filteredEntries(partOfSpeech, lesson, filters, limit)

    suspend fun recentEntries(limit: Int = 10): List<LexiconEntryWithDetails> =
        dataSource.recentEntries(limit)

    suspend fun getEntryCount(): Int = dataSource.getEntryCount()
    suspend fun entriesByIds(entryIds: List<String>): List<LexiconEntryWithDetails> =
        if (entryIds.isEmpty()) emptyList() else dataSource.entriesByIds(entryIds)

    suspend fun fuzzySearch(
        query: String,
        partOfSpeech: String? = null,
        lesson: Int? = null,
        limit: Int = 20,
    ): List<LexiconEntryWithDetails> {
        val raw = query.trim()
        if (raw.isBlank()) return browseEntries(partOfSpeech, lesson, limit)
        val variants = russianSearchVariants(raw)
        val direct = variants.flatMap { search(it, partOfSpeech, lesson, limit) }
            .distinctBy { it.entry.id }
        if (direct.isNotEmpty()) return direct.take(limit)
        val normalizedVariants = variants.map(::normalizeRussianForSearch).distinct()
        val rankedIds = dataSource.fuzzyCandidates(partOfSpeech, lesson, 2_000)
            .mapNotNull { row ->
                val forms = listOfNotNull(row.normalizedLemma, row.normalizedSearchForm).distinct()
                val distance = forms.minOf { form ->
                    normalizedVariants.minOf { candidate -> editDistanceAtMost(candidate, form, 2) }
                }
                row.entryId.takeIf { distance <= 2 }?.let { it to distance }
            }
            .distinctBy { it.first }
            .sortedWith(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(limit)
            .map { it.first }
        if (rankedIds.isEmpty()) return emptyList()
        val byId = dataSource.entriesByIds(rankedIds).associateBy { it.entry.id }
        return rankedIds.mapNotNull(byId::get)
    }

    fun observeWordDetail(entryId: String): Flow<WordDetailUiState?> =
        dataSource.observeEntry(entryId).map { details -> details?.toWordDetailUiState() }

    fun observePartOfSpeechOptions(): Flow<List<String>> =
        dataSource.observePartOfSpeechOptions()

    fun observeLessonOptions(): Flow<List<Int>> = dataSource.observeLessonOptions()

    fun observeLocalExplanation(chunkId: String): Flow<LocalExplanationUiState?> =
        dataSource.observeKnowledgeChunk(chunkId).map { chunk ->
            chunk?.let {
                LocalExplanationUiState(
                    id = it.id,
                    title = it.title,
                    category = it.category,
                    content = it.content,
                    examples = gson.fromJson(it.examplesJson, STRING_LIST_TYPE),
                    keywords = gson.fromJson(it.keywordsJson, STRING_LIST_TYPE),
                    sourceDocument = it.sourceDocument,
                    sectionPath = gson.fromJson(it.sectionPathJson, STRING_LIST_TYPE),
                )
            }
        }

    suspend fun markViewed(entryId: String, viewedAt: Long = System.currentTimeMillis()) {
        dataSource.markViewed(entryId, viewedAt)
    }
}

data class SearchResponse(
    val entries: List<LexiconEntryWithDetails>,
    val redirectedFrom: String? = null,
    val redirectedTo: String? = null,
    val isFuzzyMatch: Boolean = false,
)

internal fun LexiconEntryWithDetails.matches(filters: MorphologyFilters): Boolean {
    fun String?.isPresent() = !isNullOrBlank() && this != "8"
    fun String?.matchesAny(values: List<String>): Boolean {
        if (values.isEmpty()) return true
        val normalizedTokens = orEmpty()
            .split(Regex("[,，;；/、\\s]+"))
            .map(::normalizeRussianForSearch)
            .filter(String::isNotBlank)
            .toSet()
        return values.any { normalizeRussianForSearch(it) in normalizedTokens }
    }
    return entry.gender.matchesAny(filters.genders) &&
        entry.declensionClass.matchesAny(filters.declensionClasses) &&
        entry.endingType.matchesAny(filters.endingTypes) &&
        entry.aspect.matchesAny(filters.aspects) &&
        entry.conjugationClass.matchesAny(filters.conjugationClasses) &&
        entry.phoneticAlternation.matchesAny(filters.phoneticAlternations) &&
        (filters.hasPhoneticAlternation == null ||
            entry.phoneticAlternation.isPresent() == filters.hasPhoneticAlternation) &&
        (filters.hasPluralStressPattern == null ||
            entry.pluralStressPattern.isPresent() == filters.hasPluralStressPattern)
}

private val STRING_LIST_TYPE = object : TypeToken<List<String>>() {}.type

private fun russianSearchVariants(text: String): List<String> {
    val normalized = normalizeRussianForSearch(text)
    return buildList {
        add(normalized)
        if ('ё' in normalized) add(normalized.replace('ё', 'е'))
        if ('е' in normalized) add(normalized.replace('е', 'ё'))
    }.distinct()
}

internal fun editDistanceAtMost(left: String, right: String, maximum: Int): Int {
    if (kotlin.math.abs(left.length - right.length) > maximum) return maximum + 1
    var previous = IntArray(right.length + 1) { it }
    for (i in left.indices) {
        val current = IntArray(right.length + 1)
        current[0] = i + 1
        var rowMinimum = current[0]
        for (j in right.indices) {
            current[j + 1] = minOf(
                current[j] + 1,
                previous[j + 1] + 1,
                previous[j] + if (left[i] == right[j]) 0 else 1,
            )
            rowMinimum = minOf(rowMinimum, current[j + 1])
        }
        if (rowMinimum > maximum) return maximum + 1
        previous = current
    }
    return previous[right.length]
}

private fun LexiconEntryWithDetails.toWordDetailUiState() = WordDetailUiState(
    id = entry.id,
    lesson = entry.lesson,
    sequence = entry.sequence,
    displayForm = entry.displayForm,
    normalizedLemma = entry.normalizedLemma,
    searchForms = searchForms.map { it.normalizedSearchForm },
    chineseMeaning = entry.chineseMeaning,
    partsOfSpeech = partsOfSpeech.map { it.partOfSpeech },
    gender = entry.gender,
    declensionClass = entry.declensionClass,
    endingType = entry.endingType,
    pluralStressPattern = entry.pluralStressPattern,
    aspect = entry.aspect,
    conjugationClass = entry.conjugationClass,
    phoneticAlternation = entry.phoneticAlternation,
    annotations = annotations.map {
        WordDetailUiState.Annotation(it.fieldName, it.value)
    },
    relatedKnowledge = knowledgeChunks.map(KnowledgeChunkEntity::toKnowledgeExplanation),
    sources = sources.map(EntrySourceEntity::toSourceUiModel),
)

private fun KnowledgeChunkEntity.toKnowledgeExplanation() =
    WordDetailUiState.KnowledgeExplanation(
        id, title, category, content, sourceDocument,
        Gson().fromJson(sectionPathJson, STRING_LIST_TYPE),
    )

private fun EntrySourceEntity.toSourceUiModel() =
    WordDetailUiState.Source(sourceWorkbook, sourceSheet, sourceRow)
