package org.namchieh.rusmorph.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.namchieh.rusmorph.agent.*
import org.namchieh.rusmorph.data.repository.KnowledgeRetrievalResult
import org.namchieh.rusmorph.ui.screen.agent.AgentContent
import org.namchieh.rusmorph.ui.screen.detail.AgentQuestionActions
import org.namchieh.rusmorph.ui.theme.RusMorphTheme

class AgentComposeTest {
    @get:Rule val compose = createComposeRule()

    private val detail = WordDetailUiState(
        id = "write", lesson = 1, sequence = 1, displayForm = "писа́ть",
        chineseMeaning = "写", partsOfSpeech = listOf("动词"), gender = null,
        declensionClass = null, endingType = null, pluralStressPattern = null,
        aspect = "未完成体", conjugationClass = null, phoneticAlternation = null,
        relatedKnowledge = emptyList(), sources = emptyList(),
    )
    private fun prepared(availability: AgentAvailability = AgentAvailability.AVAILABLE) = AgentPreparedData(
        detail, KnowledgeRetrievalResult(detail, emptyList(), emptyList(), emptyList()),
        AgentQuestionType.MORPHOLOGY, AgentQuestionTemplateFactory.create(AgentQuestionType.MORPHOLOGY, detail.displayForm),
        "", availability,
    )

    @Test fun detailShowsFourActions_andEtymologyClickCarriesType() {
        var selected: AgentQuestionType? = null
        compose.setContent { RusMorphTheme { AgentQuestionActions("write") { _, type -> selected = type } } }
        listOf("其词源", "其派生", "特殊变格／变位／音变", "自定义提问").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("其词源").performClick()
        assertEquals(AgentQuestionType.ETYMOLOGY, selected)
    }

    @Test fun agentShowsWordAndNoKnowledgeWarning() {
        setAgentContent(prepared())
        compose.onNodeWithText("писа́ть").assertIsDisplayed()
        compose.onNodeWithText("当前本地资料没有与该词直接关联的历史语法解释，AI 可能仅依据词表字段和通用语言学知识回答。").assertIsDisplayed()
    }

    @Test fun sendingDisablesSubmit_andOffersCancel() {
        setAgentContent(prepared(), sending = true)
        compose.onNodeWithText("正在提问").assertIsNotEnabled()
        compose.onNodeWithText("取消").assertIsDisplayed()
    }

    @Test fun successShowsEvidence_andErrorShowsRetry() {
        val response = AgentResponse("r", "c", "回答", evidence = listOf(AgentEvidence("LEXICON_FIELD", "本地词表")))
        setAgentContent(prepared(), response = response)
        compose.onNodeWithText("证据来源").assertIsDisplayed()
        compose.onNodeWithText("• 本地词表").assertIsDisplayed()
        setAgentContent(prepared(), error = AgentError.ServerError)
        compose.onNodeWithText("重试").assertIsDisplayed()
    }

    @Test fun unconfiguredAgentStillShowsLocalEntry() {
        setAgentContent(prepared(AgentAvailability.NOT_CONFIGURED))
        compose.onNodeWithText("писа́ть").assertIsDisplayed()
        compose.onNodeWithText("AI 服务尚未配置，本地查词仍可使用。").assertIsDisplayed()
        compose.onNodeWithText("提问").assertIsNotEnabled()
    }

    private fun setAgentContent(
        data: AgentPreparedData,
        sending: Boolean = false,
        response: AgentResponse? = null,
        error: AgentError? = null,
    ) {
        compose.setContent {
            RusMorphTheme {
                AgentContent(data, sending, response, error, {}, {}, {}, {}, PaddingValues(0.dp))
            }
        }
    }
}
