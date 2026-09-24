package org.namchieh.rusmorph.data.repository

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.namchieh.rusmorph.agent.*

class AgentRepositoryTest {
    private lateinit var server: MockWebServer
    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    private val request = AgentRequest(
        "11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222", "e",
        "писа́ть", "писать", AgentQuestionType.MORPHOLOGY, "请解释", AgentEntryContext(), emptyList(), AgentClientMetadata("test"),
    )

    @Test fun parsesLegacyAskSuccess() = runTest {
        enqueue("""{"requestId":"r","conversationId":"c","answer":"回答"}""")
        assertTrue(repository().ask(request) is AgentResult.Success)
    }

    @Test fun mapsNormalizedProviderErrors() = runTest {
        val cases = listOf(
            429 to ("PROVIDER_RATE_LIMITED" to AgentError.RateLimited),
            402 to ("PROVIDER_BALANCE_INSUFFICIENT" to AgentError.BalanceInsufficient),
            503 to ("SERVICE_NOT_CONFIGURED" to AgentError.ServiceNotConfigured),
        )
        for ((status, pair) in cases) {
            server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody("""{"error":{"code":"${pair.first}","message":"safe","requestId":"r","retryable":false}}"""))
            val result = repository().routeCommand("слово", AgentSessionContext())
            assertEquals(pair.second, (result as MultiAgentResult.Failure).error)
        }
    }

    @Test fun sendsTwoStageDtosAndMapsWordCard() = runTest {
        enqueue(routeResponse())
        val repository = repository()
        val routed = repository.routeCommand("查一下 зна́чит", AgentSessionContext()) as MultiAgentResult.Routed
        assertEquals(AgentOutputMode.WORD_CARD, routed.plan.outputMode)
        val routeRequest = server.takeRequest()
        assertEquals("/v1/route", routeRequest.path)
        assertTrue(routeRequest.body.readUtf8().contains("currentCardIds"))

        enqueue(composeResponse())
        val local = LocalLexiconEntry("e", "зна́чит", "значит", meanings = listOf("意味着"), partsOfSpeech = listOf("动词"), lesson = 1, aspect = "未完成体")
        val result = repository.orchestrate(OrchestrateRequest("查一下 зна́чит", AgentSessionContext(), routed.plan, List(25) { local.copy(entryId = "e$it") })) as MultiAgentResult.Completed
        assertEquals("e", result.response.card!!.entryId)
        val composeRequest = server.takeRequest()
        assertEquals("/v1/compose", composeRequest.path)
        val sent = composeRequest.body.readUtf8()
        assertTrue(sent.contains("localResults")); assertFalse(sent.contains("e20"))
    }

    private fun enqueue(body: String) = server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body))
    private fun repository() = DefaultAgentRepository.create(
        baseUrl = server.url("/").toString(),
        allowCleartextEndpoints = true,
    )
    private fun routeResponse() = """{"requestId":"r","plan":{"intent":"LOOKUP_WORD","outputMode":"WORD_CARD","interpretedQuery":{"raw":"查一下 зна́чит","words":["зна́чит"],"meaning":null,"lessonIds":[],"partsOfSpeech":[],"topic":null,"referenceTarget":null},"retrieval":{"fuzzy":false,"limit":10,"requiresLocalSearch":true},"agents":["LEXICON_RETRIEVAL","CARD_COMPOSER"],"needsClarification":false,"clarificationQuestion":null}}"""
    private fun composeResponse() = """{"requestId":"c","outputMode":"WORD_CARD","cards":[{"cardId":"c1","entryId":"e","word":{"display":"зна́чит","normalized":"значит","matchedForm":null,"stressNote":null},"meanings":[{"textZh":"意味着","partOfSpeech":"动词"}],"morphology":{"gender":null,"declensionClass":null,"endingType":null,"pluralStressPattern":null,"aspect":"未完成体","conjugationClass":null,"phoneticAlternation":null,"explanation":null},"etymology":null,"generatedSentences":[],"sourceExamples":[],"distinctions":[],"commonErrors":[],"evidence":[{"type":"LEXICON_FIELD","title":"本地词表","field":null,"excerpt":null,"sourceDocument":null}],"warnings":[],"localState":{"favorite":false,"archiveIds":[],"reviewStatus":"NOT_ADDED"}}],"deck":null,"plainAnswer":null,"clarification":null,"warnings":[],"grounding":{"usedLocalLexicon":true,"usedLocalKnowledge":false,"usedModelKnowledge":true}}"""
}
