package org.namchieh.rusmorph.agent

import java.util.UUID
import org.namchieh.rusmorph.BuildConfig
import org.namchieh.rusmorph.data.repository.KnowledgeRetrievalResult
import org.namchieh.rusmorph.data.repository.normalizeRussianForSearch
import org.namchieh.rusmorph.ui.WordDetailUiState

class AgentContextBuilder {
    fun build(
        detail: WordDetailUiState,
        retrieval: KnowledgeRetrievalResult,
        questionType: AgentQuestionType,
        userQuestion: String,
        conversationId: String = UUID.randomUUID().toString(),
        requestId: String = UUID.randomUUID().toString(),
    ): AgentRequest {
        val supplement = userQuestion.trim().take(MAX_QUESTION_LENGTH)
        val template = AgentQuestionTemplateFactory.create(questionType, detail.displayForm)
        val question = if (questionType == AgentQuestionType.CUSTOM) supplement
            else listOf(template, supplement).filter(String::isNotBlank).joinToString("\n\n补充要求：")

        var remaining = MAX_KNOWLEDGE_CHARS
        val chunks = retrieval.knowledgeChunks.take(MAX_KNOWLEDGE_CHUNKS).mapNotNull { chunk ->
            if (remaining <= 0) return@mapNotNull null
            val content = chunk.content.trim().take(remaining)
            if (content.isBlank()) return@mapNotNull null
            remaining -= content.length
            AgentKnowledgeContext(
                chunkId = chunk.id,
                title = chunk.title.trim(),
                category = chunk.category,
                content = content,
                sourceDocument = chunk.sourceDocument,
                sectionPath = chunk.sectionPath.filter(String::isNotBlank),
            )
        }
        val source = detail.sources.firstOrNull()
        return AgentRequest(
            requestId = requestId,
            conversationId = conversationId,
            entryId = detail.id,
            word = detail.displayForm,
            normalizedWord = normalizeRussianForSearch(detail.displayForm),
            questionType = questionType,
            question = question,
            entryContext = AgentEntryContext(
                meaningZh = detail.chineseMeaning.nonBlank(),
                partsOfSpeech = detail.partsOfSpeech.map(String::trim).filter(String::isNotBlank).distinct(),
                lesson = detail.lesson,
                gender = detail.gender.nonBlank(),
                declensionClass = detail.declensionClass.nonBlank(),
                endingType = detail.endingType.nonBlank(),
                pluralStressPattern = detail.pluralStressPattern.nonBlank(),
                aspect = detail.aspect.nonBlank(),
                conjugationClass = detail.conjugationClass.nonBlank(),
                phoneticAlternation = detail.phoneticAlternation.nonBlank(),
                searchForms = detail.searchForms.map(::normalizeRussianForSearch)
                    .filter(String::isNotBlank).distinct().take(MAX_SEARCH_FORMS),
                source = source?.let { AgentSource(it.sheet, it.row) },
            ),
            knowledgeContext = chunks,
            client = AgentClientMetadata(BuildConfig.VERSION_NAME),
        )
    }

    companion object {
        const val MAX_SEARCH_FORMS = 12
        const val MAX_KNOWLEDGE_CHUNKS = 4
        const val MAX_KNOWLEDGE_CHARS = 12_000
        const val MAX_QUESTION_LENGTH = 1_000
    }
}

private fun String?.nonBlank(): String? = this?.trim()?.takeIf(String::isNotBlank)
