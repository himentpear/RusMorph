package org.namchieh.rusmorph.data.repository

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import org.namchieh.rusmorph.BuildConfig
import org.namchieh.rusmorph.agent.*
import org.namchieh.rusmorph.data.diagnostics.AgentDiagnostics
import org.namchieh.rusmorph.data.diagnostics.NoopAgentDiagnostics
import org.namchieh.rusmorph.data.remote.*
import org.namchieh.rusmorph.data.settings.AppSettings
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

interface AgentRepository {
    val availability: AgentAvailability
    val diagnostics: AgentDiagnostics get() = NoopAgentDiagnostics
    suspend fun ask(request: AgentRequest): AgentResult
    suspend fun generatePronunciationExample(
        difficulty: String,
        topic: String?,
    ): PronunciationExampleResult = PronunciationExampleResult.Failure(AgentError.ServiceNotConfigured)
    suspend fun checkWorkerConnection(): Boolean = false
    suspend fun routeCommand(command: String, session: AgentSessionContext): MultiAgentResult = MultiAgentResult.Failure(AgentError.NoNetwork)
    suspend fun orchestrate(request: OrchestrateRequest): MultiAgentResult = MultiAgentResult.Failure(AgentError.NoNetwork)
}

class DefaultAgentRepository(
    private val api: AgentApi?,
    override val availability: AgentAvailability,
    override val diagnostics: AgentDiagnostics = NoopAgentDiagnostics,
    private val gson: Gson = GsonBuilder().serializeNulls().create(),
) : AgentRepository {
    private val conversationId = UUID.randomUUID().toString()
    private var lastWirePlan: AgentCommandPlanDto? = null

    override suspend fun checkWorkerConnection(): Boolean {
        if (api == null) return false
        return try { traced("GET /health", "health-${UUID.randomUUID()}") { api.health() }.isSuccessful }
        catch (_: Exception) { false }
    }

    override suspend fun ask(request: AgentRequest): AgentResult {
        if (api == null) return AgentResult.Failure(AgentError.ServiceNotConfigured)
        return try {
            val response = traced("/v1/ask", request.requestId) { api.ask(request) }
            if (response.isSuccessful) response.body()?.takeIf { it.answer.isNotBlank() }?.let(AgentResult::Success)
                ?: AgentResult.Failure(AgentError.InvalidResponse)
            else AgentResult.Failure(errorFor(response))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SocketTimeoutException) { AgentResult.Failure(AgentError.Timeout) }
        catch (_: IOException) { AgentResult.Failure(AgentError.NoNetwork) }
        catch (_: Exception) { AgentResult.Failure(AgentError.Unknown) }
    }

    override suspend fun generatePronunciationExample(
        difficulty: String,
        topic: String?,
    ): PronunciationExampleResult {
        if (api == null) return PronunciationExampleResult.Failure(AgentError.ServiceNotConfigured)
        return try {
            val response = traced(
                "/v1/pronunciation-example",
                "example-${UUID.randomUUID()}",
            ) {
                api.pronunciationExample(
                    PronunciationExampleRequestDto(
                        conversationId = conversationId,
                        difficulty = difficulty,
                        topic = topic?.trim()?.takeIf(String::isNotBlank)?.take(120),
                    )
                )
            }
            if (!response.isSuccessful) {
                PronunciationExampleResult.Failure(errorFor(response))
            } else {
                response.body()
                    ?.takeIf { it.russian.isNotBlank() && it.chinese.isNotBlank() }
                    ?.let(PronunciationExampleResult::Success)
                    ?: PronunciationExampleResult.Failure(AgentError.InvalidResponse)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SocketTimeoutException) {
            PronunciationExampleResult.Failure(AgentError.Timeout)
        } catch (_: IOException) {
            PronunciationExampleResult.Failure(AgentError.NoNetwork)
        } catch (_: Exception) {
            PronunciationExampleResult.Failure(AgentError.InvalidResponse)
        }
    }

    override suspend fun routeCommand(command: String, session: AgentSessionContext): MultiAgentResult {
        if (api == null) return MultiAgentResult.Failure(AgentError.ServiceNotConfigured)
        return try {
            val request = RouteRequestDto(
                requestId = UUID.randomUUID().toString(),
                conversationId = conversationId,
                command = command.take(1_000),
                context = RouteContextDto(
                    session.currentEntryId,
                    session.visibleCardIds.take(AppSettings.MAX_DECK_LIMIT),
                    session.currentDeckId,
                    localSearchMiss = session.localSearchMiss,
                    deckLimit = session.deckLimit.coerceIn(1, AppSettings.MAX_DECK_LIMIT),
                ),
            )
            val response = traced("/v1/route", request.requestId) { api.route(request) }
            if (!response.isSuccessful) return MultiAgentResult.Failure(errorFor(response))
            val body = response.body() ?: return MultiAgentResult.Failure(AgentError.InvalidResponse)
            lastWirePlan = body.plan
            MultiAgentResult.Routed(body.plan.toDomain())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SocketTimeoutException) { MultiAgentResult.Failure(AgentError.Timeout) }
        catch (_: IOException) { MultiAgentResult.Failure(AgentError.NoNetwork) }
        catch (_: Exception) { MultiAgentResult.Failure(AgentError.InvalidResponse) }
    }

    override suspend fun orchestrate(request: OrchestrateRequest): MultiAgentResult {
        localAction(request)?.let { return MultiAgentResult.Completed(MultiAgentResponse("COMPLETE", request.plan, localAction = it)) }
        if (api == null) return MultiAgentResult.Failure(AgentError.ServiceNotConfigured)
        return try {
            val wirePlan = lastWirePlan ?: request.plan.toWire(request.command)
            val requestId = UUID.randomUUID().toString()
            val response = traced("/v1/compose", requestId) { api.compose(
                ComposeRequestDto(
                    requestId = requestId,
                    conversationId = conversationId,
                    command = request.command.take(1_000),
                    plan = wirePlan,
                    localResults = request.localEntries
                        .take(request.plan.resultLimit())
                        .map(LocalLexiconEntry::toWire),
                ),
            ) }
            if (!response.isSuccessful) return MultiAgentResult.Failure(errorFor(response))
            val body = response.body() ?: return MultiAgentResult.Failure(AgentError.InvalidResponse)
            MultiAgentResult.Completed(body.toDomain(request.plan))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SocketTimeoutException) { MultiAgentResult.Failure(AgentError.Timeout) }
        catch (_: IOException) { MultiAgentResult.Failure(AgentError.NoNetwork) }
        catch (_: Exception) { MultiAgentResult.Failure(AgentError.InvalidResponse) }
    }

    private fun errorFor(response: Response<*>): AgentError {
        val code = runCatching {
            response.errorBody()?.charStream()?.use { gson.fromJson(it, AgentErrorEnvelopeDto::class.java) }?.error?.code
        }.getOrNull()
        return AgentNetworkErrorMapper.fromCode(code, response.code())
    }

    private suspend fun <T> traced(operation: String, requestId: String, block: suspend () -> Response<T>): Response<T> {
        val startedAt = System.currentTimeMillis()
        diagnostics.started(operation, requestId)
        try {
            val response = block()
            diagnostics.finished(
                operation, requestId, response.code(),
                if (response.isSuccessful) null else "HTTP_${response.code()}",
                System.currentTimeMillis() - startedAt,
            )
            return response
        } catch (error: SocketTimeoutException) {
            diagnostics.finished(operation, requestId, null, "CLIENT_TIMEOUT", System.currentTimeMillis() - startedAt)
            throw error
        } catch (error: IOException) {
            diagnostics.finished(operation, requestId, null, "CLIENT_NETWORK", System.currentTimeMillis() - startedAt)
            throw error
        } catch (error: Exception) {
            val type = error.javaClass.simpleName
                .replace(Regex("[^A-Za-z0-9_]"), "")
                .take(48)
                .ifBlank { "Unknown" }
            diagnostics.finished(operation, requestId, null, "CLIENT_EXCEPTION_$type", System.currentTimeMillis() - startedAt)
            throw error
        }
    }

    companion object {
        fun create(baseUrl: String, diagnostics: AgentDiagnostics = NoopAgentDiagnostics): DefaultAgentRepository {
            val normalized = EndpointPolicy.normalized(baseUrl, BuildConfig.ALLOW_CLEARTEXT_ENDPOINTS).orEmpty()
            if (normalized.isBlank()) return DefaultAgentRepository(null, AgentAvailability.NOT_CONFIGURED, diagnostics)
            val gson = GsonBuilder().serializeNulls().create()
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .build()
            val api = Retrofit.Builder().baseUrl(normalized).client(client).addConverterFactory(GsonConverterFactory.create(gson)).build().create(AgentApi::class.java)
            return DefaultAgentRepository(api, AgentAvailability.AVAILABLE, diagnostics, gson)
        }
    }
}

sealed interface PronunciationExampleResult {
    data class Success(val example: PronunciationExampleDto) : PronunciationExampleResult
    data class Failure(val error: AgentError) : PronunciationExampleResult
}

object AgentNetworkErrorMapper {
    fun fromCode(code: String?, status: Int): AgentError = when (code) {
        "SERVICE_NOT_CONFIGURED" -> AgentError.ServiceNotConfigured
        "PROVIDER_CREDENTIAL_FORMAT_INVALID" -> AgentError.ServiceNotConfigured
        "PROVIDER_AUTHENTICATION_FAILED" -> AgentError.AuthenticationFailed
        "PROVIDER_BALANCE_INSUFFICIENT" -> AgentError.BalanceInsufficient
        "PROVIDER_RATE_LIMITED", "RATE_LIMITED" -> AgentError.RateLimited
        "PROVIDER_BUSY" -> AgentError.ProviderBusy
        "PROVIDER_MODEL_UNAVAILABLE" -> AgentError.ProviderBusy
        "PROVIDER_NETWORK_ERROR" -> AgentError.NoNetwork
        "PROVIDER_TIMEOUT" -> AgentError.Timeout
        "INVALID_MODEL_RESPONSE", "EMPTY_MODEL_CONTENT" -> AgentError.InvalidResponse
        else -> fromStatus(status)
    }
    fun fromStatus(status: Int): AgentError = when (status) {
        401 -> AgentError.UnauthorizedProxy
        402 -> AgentError.BalanceInsufficient
        429 -> AgentError.RateLimited
        408, 504 -> AgentError.Timeout
        503 -> AgentError.ProviderBusy
        in 500..599 -> AgentError.ServerError
        else -> AgentError.InvalidResponse
    }
}

private fun AgentCommandPlanDto.toDomain(): AgentCommandPlan {
    val intentValue = when (intent) {
        "LOOKUP_BY_LESSON" -> AgentIntent.LESSON_DECK
        "LOOKUP_BY_PART_OF_SPEECH" -> AgentIntent.PART_OF_SPEECH_DECK
        "GENERATE_SENTENCE" -> AgentIntent.EXAMPLE
        "ETYMOLOGY" -> AgentIntent.ETYMOLOGY
        "STRESS_EXPLANATION", "MORPHOLOGY_EXPLANATION" -> AgentIntent.MORPHOLOGY
        "COMPARE_WORDS" -> AgentIntent.COMPARE
        "SAVE_CARD" -> AgentIntent.SAVE_CARD
        "SAVE_DECK" -> AgentIntent.SAVE_DECK
        "CREATE_ARCHIVE", "ADD_TO_ARCHIVE" -> AgentIntent.ARCHIVE_CARD
        "CREATE_WORD_DECK" -> AgentIntent.PART_OF_SPEECH_DECK
        "GENERAL_QUESTION" -> AgentIntent.GENERAL
        else -> if (needsClarification) AgentIntent.UNKNOWN else AgentIntent.LOOKUP
    }
    val mode = when (outputMode) {
        "CARD_DECK" -> AgentOutputMode.CARD_DECK
        "CLARIFICATION" -> AgentOutputMode.CLARIFICATION
        "PLAIN_ANSWER", "LOCAL_ACTION_RESULT" -> AgentOutputMode.TEXT
        else -> AgentOutputMode.WORD_CARD
    }
    val morphology = interpretedQuery.morphologyFilters?.toDomain() ?: MorphologyFilters()
    val lessons = interpretedQuery.lessonIds
    val partOfSpeech = interpretedQuery.partsOfSpeech.firstOrNull()
    val queries = if (interpretedQuery.words.isNotEmpty()) interpretedQuery.words.map {
        LexiconQuery(
            text = it,
            lessons = lessons,
            partOfSpeech = partOfSpeech,
            morphologyFilters = morphology,
            maxResults = retrieval.limit.coerceAtMost(AppSettings.MAX_DECK_LIMIT),
            allowFuzzy = retrieval.fuzzy,
        )
    } else listOfNotNull(
        interpretedQuery.meaning?.let {
            LexiconQuery(
                text = it,
                lessons = lessons,
                partOfSpeech = partOfSpeech,
                morphologyFilters = morphology,
                maxResults = retrieval.limit.coerceAtMost(AppSettings.MAX_DECK_LIMIT),
                allowFuzzy = retrieval.fuzzy,
            )
        },
        if (lessons.isNotEmpty() || partOfSpeech != null || !morphology.isEmpty) LexiconQuery(
            lessons = lessons,
            partOfSpeech = partOfSpeech,
            morphologyFilters = morphology,
            maxResults = retrieval.limit.coerceAtMost(AppSettings.MAX_DECK_LIMIT),
            allowFuzzy = retrieval.fuzzy,
        ) else null,
    )
    return AgentCommandPlan(intentValue, mode, queries, requiresLocalRetrieval = retrieval.requiresLocalSearch, clarification = clarificationQuestion)
}

private fun AgentCommandPlan.toWire(command: String): AgentCommandPlanDto {
    val query = queries.firstOrNull()
    val intentValue = when (intent) {
        AgentIntent.LESSON_DECK -> "LOOKUP_BY_LESSON"
        AgentIntent.PART_OF_SPEECH_DECK -> "LOOKUP_BY_PART_OF_SPEECH"
        AgentIntent.EXAMPLE -> "GENERATE_SENTENCE"
        AgentIntent.ETYMOLOGY -> "ETYMOLOGY"
        AgentIntent.MORPHOLOGY -> "MORPHOLOGY_EXPLANATION"
        AgentIntent.COMPARE -> "COMPARE_WORDS"
        AgentIntent.SAVE_CARD -> "SAVE_CARD"
        AgentIntent.SAVE_DECK -> "SAVE_DECK"
        AgentIntent.ARCHIVE_CARD -> "ADD_TO_ARCHIVE"
        AgentIntent.GENERAL -> "GENERAL_QUESTION"
        else -> "LOOKUP_WORD"
    }
    val mode = when (outputMode) { AgentOutputMode.CARD_DECK, AgentOutputMode.COMPARISON -> "CARD_DECK"; AgentOutputMode.CLARIFICATION -> "CLARIFICATION"; AgentOutputMode.TEXT -> "PLAIN_ANSWER"; else -> "WORD_CARD" }
    return AgentCommandPlanDto(
        intentValue,
        mode,
        InterpretedQueryDto(
            command,
            queries.mapNotNull { it.text },
            null,
            queries.flatMap { it.lessons.ifEmpty { listOfNotNull(it.lesson) } }.distinct(),
            listOfNotNull(query?.partOfSpeech),
            query?.morphologyFilters?.toWire(),
            null,
            null,
        ),
        RetrievalPlanDto(
            query?.allowFuzzy ?: false,
            (query?.maxResults ?: AppSettings.DEFAULT_DECK_LIMIT).coerceAtMost(AppSettings.MAX_DECK_LIMIT),
            requiresLocalRetrieval,
        ),
        if (intent == AgentIntent.GENERAL) listOf("GENERAL_COMMAND")
        else listOf("LEXICON_RETRIEVAL", "CARD_COMPOSER"),
        outputMode == AgentOutputMode.CLARIFICATION,
        clarification,
    )
}

private fun MorphologyFiltersDto.toDomain() = MorphologyFilters(
    genders = genders,
    declensionClasses = declensionClasses,
    endingTypes = endingTypes,
    aspects = aspects,
    conjugationClasses = conjugationClasses,
    phoneticAlternations = phoneticAlternations,
    hasPhoneticAlternation = hasPhoneticAlternation,
    hasPluralStressPattern = hasPluralStressPattern,
)

private fun MorphologyFilters.toWire() = MorphologyFiltersDto(
    genders = genders,
    declensionClasses = declensionClasses,
    endingTypes = endingTypes,
    aspects = aspects,
    conjugationClasses = conjugationClasses,
    phoneticAlternations = phoneticAlternations,
    hasPhoneticAlternation = hasPhoneticAlternation,
    hasPluralStressPattern = hasPluralStressPattern,
)

private fun LocalLexiconEntry.toWire() = LocalLexiconResultDto(
    entryId,
    word,
    normalizedWord,
    meanings.firstOrNull(),
    partsOfSpeech,
    lesson,
    matchedForm,
    LocalFieldsDto(
        gender,
        declensionClass,
        endingType,
        pluralStressPattern,
        aspect,
        conjugationClass,
        phoneticAlternation,
    ),
    annotations,
)

private fun ComposeResponseDto.toDomain(plan: AgentCommandPlan): MultiAgentResponse {
    val domainCards = cards.map(WordCardDto::toDomain)
    val domainDeck = deck?.let { CardDeck(it.deckId, it.title, domainCards, warnings.map { warning -> AgentWarning(warning.code, warning.message) }) }
    return MultiAgentResponse(
        "COMPLETE",
        plan,
        card = domainCards.singleOrNull().takeIf { outputMode == "WORD_CARD" },
        deck = domainDeck,
        clarification = clarification,
        warnings = warnings.map { AgentWarning(it.code, it.message) },
        plainAnswer = plainAnswer,
        thinkingSummary = thinkingSummary,
    )
}

private fun AgentCommandPlan.resultLimit(): Int =
    queries.maxOfOrNull(LexiconQuery::maxResults)
        ?.coerceIn(1, AppSettings.MAX_DECK_LIMIT)
        ?: AppSettings.DEFAULT_DECK_LIMIT

private fun WordCardDto.toDomain(): WordCard = WordCard(
    id = cardId, entryId = entryId, word = word.display, normalizedWord = word.normalized, matchedForm = word.matchedForm,
    meanings = meanings.map { it.textZh }, partsOfSpeech = meanings.mapNotNull { it.partOfSpeech }.distinct(), stress = word.stressNote,
    morphology = listOfNotNull(morphology.gender, morphology.declensionClass, morphology.endingType, morphology.pluralStressPattern, morphology.aspect, morphology.conjugationClass, morphology.phoneticAlternation, morphology.explanation).joinToString("；").ifBlank { null },
    etymology = etymology?.summary,
    generatedSentences = generatedSentences.map { GeneratedSentence(it.russian, it.chinese, EvidenceType.MODEL_GENERATED_EXAMPLE) },
    sourceExamples = sourceExamples.map { GeneratedSentence(it.russian, it.chinese, EvidenceType.SOURCE_EXAMPLE) },
    distinctions = distinctions, commonErrors = commonErrors,
    evidence = evidence.map { CardEvidence(runCatching { EvidenceType.valueOf(it.type) }.getOrDefault(EvidenceType.MODEL_KNOWLEDGE), it.title, it.field, it.excerpt, it.sourceDocument) },
    warnings = warnings.map { AgentWarning(it.code, it.message) }, favorite = localState.favorite, archiveIds = localState.archiveIds, reviewStatus = localState.reviewStatus,
)

private fun localAction(request: OrchestrateRequest): LocalAction? = when (request.plan.intent) {
    AgentIntent.SAVE_CARD -> LocalAction(LocalActionType.FAVORITE, cardIndex = request.plan.selectedCardIndexes.firstOrNull() ?: 0)
    AgentIntent.SAVE_DECK -> LocalAction(LocalActionType.SAVE_DECK)
    AgentIntent.ARCHIVE_CARD -> LocalAction(LocalActionType.ARCHIVE, cardIndex = request.plan.selectedCardIndexes.firstOrNull() ?: 0, archiveName = Regex("(?:放到|归档到)\\s*([^，。]+)").find(request.command)?.groupValues?.getOrNull(1))
    AgentIntent.DELETE_ARCHIVE -> LocalAction(LocalActionType.DELETE_ARCHIVE, archiveName = Regex("删除\\s*([^，。]+)").find(request.command)?.groupValues?.getOrNull(1), requiresConfirmation = true)
    else -> null
}

class FakeAgentRepository(private val response: AgentResult = AgentResult.Success(AgentResponse("fake", "fake", "Mock Agent 回答"))) : AgentRepository {
    override val availability = AgentAvailability.AVAILABLE
    override suspend fun ask(request: AgentRequest): AgentResult = response
}
