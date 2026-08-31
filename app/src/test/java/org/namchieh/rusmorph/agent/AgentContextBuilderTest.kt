package org.namchieh.rusmorph.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.namchieh.rusmorph.data.repository.KnowledgeRetrievalResult
import org.namchieh.rusmorph.ui.WordDetailUiState

class AgentContextBuilderTest {
    private val detail = WordDetailUiState(
        id = "write", lesson = 1, sequence = 1, displayForm = "писа́ть",
        normalizedLemma = "писать", searchForms = (1..20).map { "форма$it" },
        chineseMeaning = "写", partsOfSpeech = listOf("动词"), gender = null,
        declensionClass = null, endingType = null, pluralStressPattern = null,
        aspect = "未完成体", conjugationClass = null, phoneticAlternation = null,
        relatedKnowledge = emptyList(), sources = emptyList(),
    )
    private val retrieval = KnowledgeRetrievalResult(detail, emptyList(), emptyList(), emptyList())

    @Test fun templates_areGrounded() {
        assertTrue(AgentQuestionTemplateFactory.create(AgentQuestionType.ETYMOLOGY, "писать").contains("词源"))
        assertTrue(AgentQuestionTemplateFactory.create(AgentQuestionType.DERIVATION, "писать").contains("派生"))
        assertTrue(AgentQuestionTemplateFactory.create(AgentQuestionType.MORPHOLOGY, "писать").contains("不得伪造"))
    }

    @Test fun context_isBounded_andDoesNotInventMissingFields() {
        val request = AgentContextBuilder().build(detail, retrieval, AgentQuestionType.MORPHOLOGY, "")
        assertEquals("писать", request.normalizedWord)
        assertEquals(12, request.entryContext.searchForms.size)
        assertNull(request.entryContext.phoneticAlternation)
        assertTrue(request.knowledgeContext.isEmpty())
        assertFalse(request.question.isBlank())
    }

    @Test fun customQuestion_mustBeProvidedByCaller() {
        assertEquals("", AgentQuestionTemplateFactory.create(AgentQuestionType.CUSTOM, "писать"))
    }
}
