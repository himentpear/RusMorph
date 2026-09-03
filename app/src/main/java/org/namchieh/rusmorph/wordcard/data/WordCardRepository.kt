package org.namchieh.rusmorph.wordcard.data

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.namchieh.rusmorph.data.local.LexiconEntryWithDetails
import org.namchieh.rusmorph.data.remote.AgentApi
import org.namchieh.rusmorph.data.remote.WordCardRequestDto
import org.namchieh.rusmorph.data.repository.SearchRepository
import org.namchieh.rusmorph.wordcard.agent.WordCardNormalizer
import org.namchieh.rusmorph.wordcard.agent.WordCardValidator
import org.namchieh.rusmorph.wordcard.model.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 单词卡片与词元数据仓库。
 * 负责两级缓存策略 (Memory + Disk `ru:${lemma}:${schema_version}`)、
 * 本地知识优先加载、形态反查推导以及 Agent 智能结构化补充。
 */
class WordCardRepository(
    private val context: Context,
    private val searchRepository: SearchRepository,
    private val agentApi: AgentApi? = null,
) {
    private val memoryCache = ConcurrentHashMap<String, Pair<Lexeme, WordForm>>()
    private val cacheDir = File(context.cacheDir, "wordcards_v2").apply { mkdirs() }
    private val gson = Gson()

    /**
     * 加载单词卡片核心数据。
     * @param query 用户输入的词形或原形（如 "студент" 或 "студентами" 或 "окна"）
     * @param forceRefresh 是否强制向 Agent 重新请求更新
     */
    suspend fun getWordCard(query: String, forceRefresh: Boolean = false): Result<Pair<Lexeme, WordForm>> = withContext(Dispatchers.IO) {
        val cleanQuery = query.replace("́", "").trim().lowercase()
        if (cleanQuery.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Word query cannot be blank"))
        }

        val cacheKey = "ru:${cleanQuery}:2.0"

        // 1. 内存与磁盘缓存检查 (当非强制刷新时)
        if (!forceRefresh) {
            memoryCache[cacheKey]?.let { return@withContext Result.success(it) }
            val diskData = readFromDisk(cacheKey)
            if (diskData != null) {
                memoryCache[cacheKey] = diskData
                return@withContext Result.success(diskData)
            }
        }

        // 2. 形态反查检查：如果当前词形不是原形（如 студентами），先分析语法形态
        val candidateAnalyses = FormAnalysisEngine.analyze(cleanQuery)
        val candidateLemma = candidateAnalyses.firstOrNull()?.lemma ?: cleanQuery

        // 3. 本地词库检索
        val searchCandidates = searchRepository.search(candidateLemma, limit = 5)
        var localEntry: LexiconEntryWithDetails? = searchCandidates.firstOrNull {
            it.entry.lemma.equals(candidateLemma, ignoreCase = true)
        } ?: searchCandidates.firstOrNull()
        if (localEntry == null && candidateLemma != cleanQuery) {
            val fallbackCandidates = searchRepository.search(cleanQuery, limit = 5)
            localEntry = fallbackCandidates.firstOrNull {
                it.entry.lemma.equals(cleanQuery, ignoreCase = true)
            } ?: fallbackCandidates.firstOrNull()
        }

        var lexeme: Lexeme? = null
        var wordForm: WordForm? = null

        if (localEntry != null) {
            val (normLexeme, normForm) = WordCardNormalizer.normalizeFromLocalEntry(localEntry)
            lexeme = normLexeme
            wordForm = if (candidateAnalyses.isNotEmpty() && candidateLemma != cleanQuery) {
                normForm.copy(
                    inputForm = cleanQuery,
                    displayForm = query,
                    isLemma = false,
                    analyses = candidateAnalyses.flatMap { it.analyses },
                    explanationZh = candidateAnalyses.firstOrNull()?.explanationZh.orEmpty(),
                )
            } else {
                normForm
            }
        }

        // 4. 若无本地变格变位数据或用户要求补充，且 Agent 服务可用，向 Agent 请求
        val needsAgent = forceRefresh || lexeme == null || (lexeme.morphology is Lexeme.MorphologyInfo.Empty && candidateLemma.length >= 3)
        if (needsAgent && agentApi != null) {
            try {
                val contextHint = localEntry?.let { "${it.entry.gender ?: ""} ${it.entry.declensionClass ?: ""} ${it.entry.chineseMeaning ?: ""}" } ?: ""
                val response = agentApi.generateWordCard(WordCardRequestDto(word = cleanQuery, context = contextHint))
                if (response.isSuccessful && response.body() != null) {
                    val rawJson = response.body().toString()
                    val validation = WordCardValidator.validateAndParse(rawJson)
                    if (validation is WordCardValidator.ValidationResult.Success) {
                        val (agentLexeme, agentForm) = WordCardNormalizer.normalizeFromDto(validation.dto, cleanQuery)
                        // 合并本地权威释义与 Agent 生成的完整变格/例句
                        lexeme = if (localEntry != null) {
                            agentLexeme.copy(
                                id = localEntry.entry.id,
                                basic = agentLexeme.basic.copy(
                                    translationsZh = (localEntry.entry.chineseMeaning?.split("；", ";")?.map { it.trim() } ?: emptyList())
                                        .ifEmpty { agentLexeme.basic.translationsZh }
                                )
                            )
                        } else {
                            agentLexeme
                        }
                        wordForm = agentForm
                    }
                }
            } catch (_: Exception) {
                // Agent 请求异常降级，不阻断本地结果呈现
            }
        }

        // 5. 兜底保障（若本地和远程都无数据，生成安全基础对象）
        if (lexeme == null) {
            lexeme = Lexeme(
                id = "lex_$cleanQuery",
                lemma = candidateLemma,
                displayForm = query,
                basic = Lexeme.BasicInfo(partOfSpeech = "词", translationsZh = listOf("暂无词典释义")),
            )
        }
        if (wordForm == null) {
            wordForm = WordForm(
                inputForm = cleanQuery,
                displayForm = query,
                isLemma = candidateLemma == cleanQuery,
                analyses = candidateAnalyses.flatMap { it.analyses }.ifEmpty { listOf(FormAnalysis(noteZh = "输入词形")) },
                explanationZh = candidateAnalyses.firstOrNull()?.explanationZh.orEmpty(),
            )
        }

        val resultPair = lexeme to wordForm
        memoryCache[cacheKey] = resultPair
        saveToDisk(cacheKey, resultPair)

        Result.success(resultPair)
    }

    private fun readFromDisk(cacheKey: String): Pair<Lexeme, WordForm>? {
        val file = File(cacheDir, "${cacheKey.replace(":", "_")}.json")
        if (!file.exists()) return null
        return try {
            val text = file.readText()
            val parsed = gson.fromJson(text, DiskCacheModel::class.java)
            parsed.toPair()
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    private fun saveToDisk(cacheKey: String, data: Pair<Lexeme, WordForm>) {
        try {
            val file = File(cacheDir, "${cacheKey.replace(":", "_")}.json")
            val cacheModel = DiskCacheModel(
                lexemeJson = gson.toJson(data.first),
                formJson = gson.toJson(data.second)
            )
            file.writeText(gson.toJson(cacheModel))
        } catch (_: Exception) {}
    }

    private data class DiskCacheModel(val lexemeJson: String, val formJson: String) {
        fun toPair(): Pair<Lexeme, WordForm> {
            val gson = Gson()
            val l = gson.fromJson(lexemeJson, Lexeme::class.java)
            val f = gson.fromJson(formJson, WordForm::class.java)
            return l to f
        }
    }
}
