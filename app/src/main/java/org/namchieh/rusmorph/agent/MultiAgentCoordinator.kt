package org.namchieh.rusmorph.agent

import org.namchieh.rusmorph.data.local.EntryAnnotationEntity
import org.namchieh.rusmorph.data.local.LexiconEntryWithDetails
import org.namchieh.rusmorph.data.repository.AgentRepository
import org.namchieh.rusmorph.data.repository.SearchRepository
import org.namchieh.rusmorph.data.settings.AppSettings

class MultiAgentCoordinator(
    private val agentRepository: AgentRepository,
    private val searchRepository: SearchRepository,
) {
    suspend fun execute(command: String, session: AgentSessionContext): MultiAgentResult {
        val normalized = command.trim()
        val deckLimit = session.deckLimit.coerceIn(1, AppSettings.MAX_DECK_LIMIT)
        val directQuery = directLookupQuery(normalized)
        if (directQuery != null) {
            val indexed = searchRepository.search(directQuery, limit = deckLimit)
            if (indexed.isNotEmpty()) {
                val plan = AgentCommandPlan(
                    intent = AgentIntent.LOOKUP,
                    outputMode = if (indexed.size == 1) AgentOutputMode.WORD_CARD else AgentOutputMode.CARD_DECK,
                    queries = listOf(LexiconQuery(text = directQuery, maxResults = deckLimit, allowFuzzy = false)),
                    requiresLocalRetrieval = true,
                )
                return MultiAgentResult.Completed(localIndexedResponse(plan, indexed.map { it.toLocalEntry() }))
            }
        }
        val routed = agentRepository.routeCommand(
            normalized,
            session.copy(localSearchMiss = directQuery != null),
        )
        val routedPlan = (routed as? MultiAgentResult.Routed)?.plan ?: run {
            if (routed is MultiAgentResult.Failure && isSimpleLookup(normalized)) {
                AgentCommandPlan(
                    intent = AgentIntent.LOOKUP,
                    outputMode = AgentOutputMode.WORD_CARD,
                    queries = listOf(LexiconQuery(text = normalized.removePrefix("查一下").trim(), maxResults = 10, allowFuzzy = true)),
                    requiresLocalRetrieval = true,
                )
            } else return routed
        }
        val plan = routedPlan.withResultLimit(deckLimit)
        val localEntries = retrieve(plan, session)
        if (plan.requiresLocalRetrieval && localEntries.isEmpty() && plan.queries.any { !it.morphologyFilters.isEmpty }) {
            return MultiAgentResult.Completed(noLocalMorphologyMatches(plan))
        }
        val composed = agentRepository.orchestrate(OrchestrateRequest(normalized, session, plan, localEntries))
        return if (composed is MultiAgentResult.Failure && localEntries.isNotEmpty()) {
            MultiAgentResult.Completed(localIndexedResponse(
                plan,
                localEntries,
                AgentWarning("AI_UNAVAILABLE", "当前展示本地词表信息，AI 扩展内容暂不可用。"),
            ))
        } else composed
    }

    suspend fun retrieve(plan: AgentCommandPlan, session: AgentSessionContext): List<LocalLexiconEntry> {
        if (!plan.requiresLocalRetrieval) return emptyList()
        val results = buildList {
            if (plan.intent == AgentIntent.SIMILAR && session.currentEntryId != null) {
                val current = searchRepository.entriesByIds(listOf(session.currentEntryId)).firstOrNull()
                val partOfSpeech = current?.partsOfSpeech?.firstOrNull()?.partOfSpeech
                addAll(searchRepository.browseEntries(
                    partOfSpeech = partOfSpeech,
                    limit = session.deckLimit.coerceIn(1, AppSettings.MAX_DECK_LIMIT),
                )
                    .filterNot { it.entry.id == session.currentEntryId })
            }
            if (plan.queries.isEmpty() && session.currentEntryId != null) {
                if (plan.intent != AgentIntent.SIMILAR) addAll(searchRepository.entriesByIds(listOf(session.currentEntryId)))
            }
            plan.queries.forEach { query ->
                val lessons = query.lessons.ifEmpty { listOfNotNull(query.lesson) }.ifEmpty { listOf(null) }
                lessons.forEach { lesson ->
                    val entries = if (query.text.isNullOrBlank()) {
                        if (query.morphologyFilters.isEmpty) {
                            searchRepository.browseEntries(query.partOfSpeech, lesson, query.maxResults)
                        } else {
                            searchRepository.filteredEntries(query.partOfSpeech, lesson, query.morphologyFilters, query.maxResults)
                        }
                    } else if (query.allowFuzzy) {
                        searchRepository.fuzzySearch(query.text, query.partOfSpeech, lesson, 200)
                            .filter { it.matchesMorphology(query.morphologyFilters) }
                            .take(query.maxResults)
                    } else {
                        searchRepository.search(query.text, query.partOfSpeech, lesson, 200)
                            .filter { it.matchesMorphology(query.morphologyFilters) }
                            .take(query.maxResults)
                    }
                    addAll(entries)
                }
            }
        }.distinctBy { it.entry.id }
            .take(session.deckLimit.coerceIn(1, AppSettings.MAX_DECK_LIMIT))
        return results.map { it.toLocalEntry() }
    }
}

private fun LexiconEntryWithDetails.matchesMorphology(filters: MorphologyFilters): Boolean {
    fun String?.present() = !isNullOrBlank() && this != "8"
    fun String?.oneOf(values: List<String>) =
        values.isEmpty() || values.any { requested -> this?.trim()?.equals(requested.trim(), ignoreCase = true) == true }
    return entry.gender.oneOf(filters.genders) &&
        entry.declensionClass.oneOf(filters.declensionClasses) &&
        entry.endingType.oneOf(filters.endingTypes) &&
        entry.aspect.oneOf(filters.aspects) &&
        entry.conjugationClass.oneOf(filters.conjugationClasses) &&
        entry.phoneticAlternation.oneOf(filters.phoneticAlternations) &&
        (filters.hasPhoneticAlternation == null || entry.phoneticAlternation.present() == filters.hasPhoneticAlternation) &&
        (filters.hasPluralStressPattern == null || entry.pluralStressPattern.present() == filters.hasPluralStressPattern)
}

private fun noLocalMorphologyMatches(plan: AgentCommandPlan): MultiAgentResponse {
    val hasAlternationConstraint = plan.queries.any { it.morphologyFilters.hasPhoneticAlternation == true }
    val message = if (hasAlternationConstraint) {
        "本地词库没有找到同时满足这些条件的词条；当前词条的语音交替字段可能尚未完成标注。"
    } else {
        "本地词库没有找到同时满足这些形态条件的词条。"
    }
    val warning = AgentWarning("NO_LOCAL_MORPHOLOGY_MATCH", message)
    return MultiAgentResponse(
        phase = "COMPLETE",
        plan = plan,
        clarification = message,
        warnings = listOf(warning),
    )
}

private fun LexiconEntryWithDetails.toLocalEntry(): LocalLexiconEntry = LocalLexiconEntry(
    entryId = entry.id,
    word = entry.displayForm,
    normalizedWord = entry.normalizedLemma,
    matchedForm = searchForms.firstOrNull()?.normalizedSearchForm,
    meanings = listOfNotNull(entry.chineseMeaning?.takeIf(String::isNotBlank)),
    partsOfSpeech = partsOfSpeech.map { it.partOfSpeech }.filter(String::isNotBlank).distinct(),
    lesson = entry.lesson,
    gender = entry.gender,
    declensionClass = entry.declensionClass,
    endingType = entry.endingType,
    pluralStressPattern = entry.pluralStressPattern,
    aspect = entry.aspect,
    conjugationClass = entry.conjugationClass,
    phoneticAlternation = entry.phoneticAlternation,
    annotations = annotations
        .groupBy(EntryAnnotationEntity::fieldName, EntryAnnotationEntity::value)
        .mapValues { (_, values) -> values.distinct() },
    sourceExamples = emptyList(),
    sources = sources.map { "${it.sourceWorkbook} · ${it.sourceSheet} · ${it.sourceRow}" },
)

private fun isSimpleLookup(command: String): Boolean {
    val candidate = command.removePrefix("查一下").trim()
    return candidate.matches(Regex("[А-Яа-яЁё\\u0301-]{1,80}")) || candidate.matches(Regex("[\\u3400-\\u9fff]{1,12}"))
}

private fun directLookupQuery(command: String): String? {
    val candidate = listOf("查一下", "查询", "搜索", "查找")
        .firstOrNull(command::startsWith)
        ?.let(command::removePrefix)
        ?.trim()
        ?: command.trim()
    if (candidate.isBlank()) return null
    if (candidate.isGeneralInstruction()) return null
    val isRussian = candidate.matches(Regex("[А-Яа-яЁё\\u0301-]{1,80}"))
    val isChineseMeaning = candidate.matches(Regex("[\\u3400-\\u9fff]{1,12}")) &&
        !Regex("(第.+课|名词|动词|形容词|副词|比较|造句|解释|为什么|怎么)").containsMatchIn(candidate)
    return candidate.takeIf { isRussian || isChineseMeaning }
}

private fun String.isGeneralInstruction(): Boolean =
    Regex("(你好|谢谢|帮助|怎么使用|如何使用|介绍.*应用|设置|界面|反馈|可以做什么|你能做什么)")
        .containsMatchIn(this)

private fun localIndexedResponse(
    plan: AgentCommandPlan,
    entries: List<LocalLexiconEntry>,
    warning: AgentWarning? = null,
): MultiAgentResponse {
    val warnings = listOfNotNull(warning)
    val cards = entries.map { entry ->
        WordCard(
            id = "local-${entry.entryId}", entryId = entry.entryId, word = entry.word,
            normalizedWord = entry.normalizedWord, matchedForm = entry.matchedForm,
            meanings = entry.meanings, partsOfSpeech = entry.partsOfSpeech,
            stress = entry.word.takeIf { it.contains('\u0301') },
            morphology = listOfNotNull(entry.gender, entry.declensionClass, entry.endingType, entry.aspect, entry.conjugationClass, entry.phoneticAlternation).joinToString("；").ifBlank { null },
            evidence = listOf(CardEvidence(EvidenceType.LEXICON_FIELD, "本地词表")) + entry.sources.map { CardEvidence(EvidenceType.LEXICON_FIELD, "本地来源", excerpt = it) },
            warnings = warnings,
        )
    }
    return if (plan.outputMode == AgentOutputMode.WORD_CARD && cards.size == 1) {
        MultiAgentResponse(
            "COMPLETE",
            plan,
            card = cards.first(),
            warnings = warnings,
            thinkingSummary = "已完成本地精确检索，并核对词形、释义和注释索引。",
            plainAnswer = "找到 1 个匹配词条。",
        )
    } else {
        MultiAgentResponse(
            "COMPLETE",
            plan,
            deck = CardDeck("local-index", "本地检索结果", cards, warnings),
            warnings = warnings,
            thinkingSummary = "已完成本地精确检索，并按匹配优先级整理结果。",
            plainAnswer = "找到 ${cards.size} 个匹配词条。",
        )
    }
}

private fun AgentCommandPlan.withResultLimit(limit: Int): AgentCommandPlan {
    if (!requiresLocalRetrieval) return this
    return copy(queries = queries.map { it.copy(maxResults = limit) })
}
