package org.namchieh.rusmorph.data.repository

import kotlinx.coroutines.flow.first
import org.namchieh.rusmorph.ui.WordDetailUiState
import org.namchieh.rusmorph.agent.AgentQuestionType

typealias KnowledgeQuestionType = AgentQuestionType

data class KnowledgeRetrievalResult(
    val entry: WordDetailUiState?,
    val knowledgeChunks: List<WordDetailUiState.KnowledgeExplanation>,
    val evidence: List<String>,
    val warnings: List<String>,
)

class KnowledgeRetriever(
    private val repository: SearchRepository,
) {
    suspend fun retrieve(
        entryId: String,
        questionType: AgentQuestionType,
        customQuestion: String? = null,
    ): KnowledgeRetrievalResult {
        val entry = repository.observeWordDetail(entryId).first()
            ?: return KnowledgeRetrievalResult(
                entry = null,
                knowledgeChunks = emptyList(),
                evidence = emptyList(),
                warnings = listOf("本地词库中不存在该词条"),
            )
        val relevant = when (questionType) {
            AgentQuestionType.MORPHOLOGY -> entry.relatedKnowledge
                .sortedWith(compareBy({ MORPHOLOGY_PRIORITY[it.category] ?: Int.MAX_VALUE }, { it.id }))

            AgentQuestionType.ETYMOLOGY -> entry.relatedKnowledge
                .filter { explanation -> HISTORICAL_EVIDENCE.containsMatchIn(explanation.content) }
                .sortedBy { it.id }

            AgentQuestionType.DERIVATION -> entry.relatedKnowledge
                .filter { explanation -> DERIVATION_EVIDENCE.containsMatchIn(explanation.content) }
                .sortedBy { it.id }

            AgentQuestionType.CUSTOM -> entry.relatedKnowledge.sortedBy { it.id }
        }.take(MAX_CHUNKS)
        val warnings = buildList {
            if (customQuestion?.isBlank() == true && questionType == AgentQuestionType.CUSTOM) {
                add("自定义问题为空，仅返回已关联的本地资料")
            }
            if (relevant.isEmpty()) add("当前本地资料没有可用于该问题的关联知识块")
        }
        return KnowledgeRetrievalResult(
            entry = entry,
            knowledgeChunks = relevant,
            evidence = relevant.map { "EntryKnowledgeCrossRef:${it.id}" },
            warnings = warnings,
        )
    }

    private companion object {
        const val MAX_CHUNKS = 4
        val MORPHOLOGY_PRIORITY = listOf(
            "IOTATION",
            "FIRST_PALATALIZATION",
            "MIXED_CONJUGATION",
            "ATHEMATIC_CONJUGATION",
            "STEM_ALTERNATION",
        ).withIndex().associate { (index, category) -> category to index }
        val HISTORICAL_EVIDENCE = Regex("词源|历史来源|历史演变|古俄语|演变而来|来源于")
        val DERIVATION_EVIDENCE = Regex("派生|构词|前缀|后缀|词干")
    }
}
