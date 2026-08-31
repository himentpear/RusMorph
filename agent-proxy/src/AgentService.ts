import type { Env } from "./providers/AgentProvider";
import { DeepSeekProvider } from "./providers/DeepSeekProvider";
import { HttpError } from "./errors";
import { ROUTER_PROMPT } from "./prompts/routerPrompt";
import { CARD_COMPOSER_PROMPT } from "./prompts/cardComposerPrompt";
import { GENERAL_COMMAND_PROMPT } from "./prompts/generalCommandPrompt";
import {
  agentCommandPlanSchema, composeAugmentationResponseSchema, composeModelResponseSchema, composeRequestSchema,
  generalCommandResponseSchema, routeRequestSchema, routeResponseSchema, DEFAULT_DECK_CARDS,
  type AgentCommandPlan,
  type ComposeAugmentationResponse, type ComposeModelResponse, type LocalLexiconResult,
} from "./protocol";

export async function handleRoute(
  raw: unknown,
  env: Env,
  provider: Pick<DeepSeekProvider, "completeJson"> = new DeepSeekProvider(),
): Promise<unknown> {
  const request = routeRequestSchema.parse(raw);
  let plan: AgentCommandPlan;
  if (env.AGENT_PROVIDER === "mock" || (!request.context.localSearchMiss && canRouteDeterministically(request.command))) {
    plan = mockRoute(request.command, request.context);
  } else {
    try {
      plan = await provider.completeJson(
        { systemPrompt: ROUTER_PROMPT, input: { command: request.command, context: request.context }, temperature: 0, maxTokens: 1_200 },
        agentCommandPlanSchema,
        env,
      );
    } catch (error) {
      // Routing produces only a local-search plan. If the model is temporarily
      // unavailable, use the deterministic parser so Room stays authoritative
      // and a transient provider fault cannot block local lookup.
      if (error instanceof HttpError && error.status >= 500) {
        console.warn(JSON.stringify({ event: "router_model_fallback", code: error.code }));
        plan = mockRoute(request.command, request.context);
      } else {
        throw error;
      }
    }
  }
  plan = applyDeterministicConstraints(plan, request.command, request.context.deckLimit);
  return routeResponseSchema.parse({ requestId: request.requestId, plan });
}

export async function handleCompose(
  raw: unknown,
  env: Env,
  provider: Pick<DeepSeekProvider, "completeJson"> = new DeepSeekProvider(),
): Promise<unknown> {
  const request = composeRequestSchema.parse(raw);
  let model: ComposeModelResponse;
  let usedModelKnowledge = false;
  if (request.plan.intent === "GENERAL_QUESTION") {
    const general = await provider.completeJson(
      {
        systemPrompt: GENERAL_COMMAND_PROMPT,
        input: { command: request.command, context: { locale: "zh-CN" } },
        temperature: 0.2,
        maxTokens: 1_200,
      },
      generalCommandResponseSchema,
      env,
    );
    model = {
      outputMode: "PLAIN_ANSWER",
      cards: [],
      deck: null,
      plainAnswer: general.reply,
      thinkingSummary: general.thinkingSummary,
      clarification: null,
      warnings: general.warnings,
    };
    usedModelKnowledge = true;
  } else if (request.plan.needsClarification) {
    model = { outputMode: "CLARIFICATION", cards: [], deck: null, plainAnswer: null, thinkingSummary: "需要补充上下文后才能继续。", clarification: request.plan.clarificationQuestion, warnings: [] };
  } else if (request.plan.retrieval.requiresLocalSearch && request.localResults.length === 0) {
    model = { outputMode: "CLARIFICATION", cards: [], deck: null, plainAnswer: null, thinkingSummary: "已完成本地检索，但没有可靠命中。", clarification: "本地词库没有找到匹配词条。", warnings: [{ code: "NO_LOCAL_MATCH", message: "未使用模型伪造本地命中" }] };
  } else if (env.AGENT_PROVIDER === "mock") {
    model = mockCompose(request.requestId, request.plan, request.localResults, envWarning());
  } else if (canComposeFromLocalFacts(request.plan)) {
    model = mockCompose(request.requestId, request.plan, request.localResults, []);
  } else {
    try {
      const augmentation = composeAugmentationResponseSchema.parse(await provider.completeJson({
        systemPrompt: CARD_COMPOSER_PROMPT,
        input: { command: request.command, plan: request.plan, localResults: request.localResults, knowledgeContext: request.knowledgeContext },
        temperature: 0.1,
        maxTokens: 2_000,
      }, composeAugmentationResponseSchema, env));
      model = mergeGeneratedContent(
        request.requestId,
        request.plan,
        request.localResults,
        augmentation,
      );
      usedModelKnowledge = true;
    } catch (error) {
      // Keep the factual card usable when generation is temporarily down. This
      // deliberately adds no model-made examples, analysis, or etymology.
      if (error instanceof HttpError && error.status >= 500) {
        console.warn(JSON.stringify({ event: "composer_model_fallback", code: error.code }));
        model = localFallbackCompose(request.requestId, request.plan, request.localResults, error.code, error.message);
      } else {
        throw error;
      }
    }
  }
  return {
    requestId: request.requestId,
    ...composeModelResponseSchema.parse(model),
    grounding: {
      usedLocalLexicon: request.localResults.length > 0,
      usedLocalKnowledge: request.knowledgeContext.length > 0,
      usedModelKnowledge,
    },
  };
}

function canRouteDeterministically(command: string): boolean {
  const trimmed = command.trim();
  if (!trimmed) return false;
  if (extractWords(trimmed).length > 0) return true;
  if (/第?[一二三四五六七八九十\d]+课/.test(trimmed)) return true;
  if (/(?:阳性|阴性|中性|共性|音变|语音交替|复数重音|变格|变位|名词|动词|形容词|副词|数词|代词|前置词|连接词)/.test(trimmed)) return true;
  if (/(?:收藏|归档|造句|例句|比较|辨析|拼错|类似)/.test(trimmed)) return true;
  return /^[\u3400-\u9fff]{1,20}$/.test(trimmed);
}

function canComposeFromLocalFacts(planValue: AgentCommandPlan): boolean {
  return [
    "LOOKUP_WORD",
    "LOOKUP_MEANING",
    "FUZZY_LOOKUP",
    "LOOKUP_BY_LESSON",
    "LOOKUP_BY_PART_OF_SPEECH",
    "CREATE_WORD_DECK",
  ].includes(planValue.intent);
}

function localFallbackCompose(
  requestId: string,
  planValue: AgentCommandPlan,
  entries: LocalLexiconResult[],
  providerCode: string,
  providerMessage: string,
): ComposeModelResponse {
  const warning = {
    code: "AI_GENERATION_UNAVAILABLE",
    message: `AI 生成内容暂时不可用（${providerCode}: ${providerMessage}），以下仅显示本地词库事实。`,
  };
  const model = mockCompose(requestId, planValue, entries, []);
  return { ...model, cards: model.cards.map(card => ({ ...card, warnings: [warning] })), warnings: [warning] };
}

function mockRoute(command: string, context: RouteContext): AgentCommandPlan {
  const lessonMatch = /第?([一二三四五六七八九十\d]+)课/.exec(command)?.[1];
  const lesson = lessonMatch ? chineseNumber(lessonMatch) : undefined;
  const part = ["动词", "名词", "形容词", "副词", "数词", "代词"].find(value => command.includes(value));
  const words = extractWords(command);
  const missingReference = /这个|它|第[一二三四五六七八九十\d]+个/.test(command) && words.length === 0 && !context.currentEntryId && context.currentCardIds.length === 0;
  if (missingReference) return plan(command, "GENERAL_QUESTION", "CLARIFICATION", [], null, [], false, false, true, "请先选择一个词条或卡片。");
  if (/收藏/.test(command)) return plan(command, "SAVE_CARD", "LOCAL_ACTION_RESULT", [], null, [], false, false, false, null, "current");
  if (/归档/.test(command)) return plan(command, "ADD_TO_ARCHIVE", "LOCAL_ACTION_RESULT", [], null, [], false, false, false, null, "current");
  if (/比较|辨析/.test(command)) return plan(command, "COMPARE_WORDS", "CARD_DECK", words, null, [], false, true, false, null);
  if (/造句|例句/.test(command)) return plan(command, "GENERATE_SENTENCE", "WORD_CARD", words, null, [], false, !context.currentEntryId, false, null, context.currentEntryId ? "current" : null);
  if (lesson) return plan(command, "LOOKUP_BY_LESSON", "CARD_DECK", [], null, part ? [part] : [], false, true, false, null, null, [lesson]);
  if (part) return plan(command, "LOOKUP_BY_PART_OF_SPEECH", "CARD_DECK", [], null, [part], false, true, false, null);
  if (isGeneralOutsideLexicon(command, words)) {
    return plan(command, "GENERAL_QUESTION", "PLAIN_ANSWER", [], null, [], false, false, false, null);
  }
  const candidate = words[0] ?? command.trim();
  const looksMeaning = /^[\u3400-\u9fff]+$/.test(candidate);
  const fuzzy = /可能|好像|类似|拼错/.test(command);
  return plan(command, fuzzy ? "FUZZY_LOOKUP" : looksMeaning ? "LOOKUP_MEANING" : "LOOKUP_WORD", "WORD_CARD", looksMeaning ? [] : [candidate], looksMeaning ? candidate : null, [], fuzzy, true, false, null);
}

function plan(raw: string, intent: AgentCommandPlan["intent"], outputMode: AgentCommandPlan["outputMode"], words: string[], meaning: string | null, partsOfSpeech: string[], fuzzy: boolean, requiresLocalSearch: boolean, needsClarification: boolean, clarificationQuestion: string | null, referenceTarget: string | null = null, lessonIds: number[] = []): AgentCommandPlan {
  return {
    intent, outputMode,
    interpretedQuery: {
      raw, words, meaning, lessonIds, partsOfSpeech,
      morphologyFilters: emptyMorphologyFilters(),
      topic: null, referenceTarget,
    },
    retrieval: { fuzzy, limit: DEFAULT_DECK_CARDS, requiresLocalSearch },
    agents: intent === "GENERAL_QUESTION"
      ? ["GENERAL_COMMAND"]
      : requiresLocalSearch
        ? ["LEXICON_RETRIEVAL", "CARD_COMPOSER"]
        : ["LOCAL_LIBRARY"],
    needsClarification, clarificationQuestion,
  };
}

function emptyMorphologyFilters(): AgentCommandPlan["interpretedQuery"]["morphologyFilters"] {
  return {
    genders: [],
    declensionClasses: [],
    endingTypes: [],
    aspects: [],
    conjugationClasses: [],
    phoneticAlternations: [],
    hasPhoneticAlternation: null,
    hasPluralStressPattern: null,
  };
}

function applyDeterministicConstraints(planValue: AgentCommandPlan, command: string, deckLimit: number): AgentCommandPlan {
  const current = planValue.interpretedQuery.morphologyFilters ?? emptyMorphologyFilters();
  const genders = [
    ...current.genders,
    ...(["阳性", "阴性", "中性", "共性"].filter(value => command.includes(value))),
  ].filter((value, index, all) => all.indexOf(value) === index);
  const partsOfSpeech = [
    ...planValue.interpretedQuery.partsOfSpeech,
    ...(["名词", "动词", "形容词", "副词", "数词", "代词", "前置词", "连接词"].filter(value => command.includes(value))),
  ].filter((value, index, all) => all.indexOf(value) === index);
  const explicitNoAlternation = /无(?:语音)?音变|没有(?:语音)?音变|无语音交替/.test(command);
  const explicitAlternation = /音变|语音交替/.test(command);
  const explicitPluralStress = /复数重音(?:变化|转移)|复数.*重音/.test(command);
  const morphologyFilters = {
    ...current,
    genders,
    hasPhoneticAlternation: explicitNoAlternation ? false : explicitAlternation ? true : current.hasPhoneticAlternation,
    hasPluralStressPattern: explicitPluralStress ? true : current.hasPluralStressPattern,
  };
  const hasMorphologyConstraint =
    morphologyFilters.genders.length > 0 ||
    morphologyFilters.declensionClasses.length > 0 ||
    morphologyFilters.endingTypes.length > 0 ||
    morphologyFilters.aspects.length > 0 ||
    morphologyFilters.conjugationClasses.length > 0 ||
    morphologyFilters.phoneticAlternations.length > 0 ||
    morphologyFilters.hasPhoneticAlternation !== null ||
    morphologyFilters.hasPluralStressPattern !== null;
  const isSetQuery = planValue.interpretedQuery.words.length === 0 && (partsOfSpeech.length > 0 || hasMorphologyConstraint);
  return {
    ...planValue,
    intent: isSetQuery
      ? planValue.interpretedQuery.lessonIds.length > 0
        ? "LOOKUP_BY_LESSON"
        : "LOOKUP_BY_PART_OF_SPEECH"
      : planValue.intent,
    outputMode: isSetQuery ? "CARD_DECK" : planValue.outputMode,
    interpretedQuery: {
      ...planValue.interpretedQuery,
      partsOfSpeech,
      morphologyFilters,
    },
    retrieval: planValue.retrieval.requiresLocalSearch || isSetQuery
      ? {
          ...planValue.retrieval,
          fuzzy: isSetQuery ? false : planValue.retrieval.fuzzy,
          limit: deckLimit,
          requiresLocalSearch: true,
        }
      : planValue.retrieval,
    agents: isSetQuery ? ["LEXICON_RETRIEVAL", "CARD_COMPOSER"] : planValue.agents,
  };
}

function mockCompose(
  requestId: string,
  planValue: AgentCommandPlan,
  entries: LocalLexiconResult[],
  warnings = envWarning(),
): ComposeModelResponse {
  const cards = entries.map(entry => localCard(entry, warnings));
  const deck = planValue.outputMode === "CARD_DECK" ? {
    deckId: `deck-${requestId}`, title: deckTitle(planValue), description: null,
    deckType: deckType(planValue), generationRule: { lessonIds: planValue.interpretedQuery.lessonIds, partsOfSpeech: planValue.interpretedQuery.partsOfSpeech, topic: planValue.interpretedQuery.topic, comparedEntryIds: planValue.intent === "COMPARE_WORDS" ? entries.map(it => it.entryId) : [] },
    cardIds: cards.map(it => it.cardId), comparison: planValue.intent === "COMPARE_WORDS" ? { entryIds: entries.map(it => it.entryId), summary: "Mock 辨析仅用于联调" } : null,
    localState: { favorite: false, archiveId: null, saved: false },
  } as const : null;
  return {
    outputMode: planValue.outputMode,
    cards,
    deck,
    plainAnswer: null,
    thinkingSummary: entries.length
      ? `已根据本地词库整理 ${entries.length} 个可靠结果。`
      : null,
    clarification: null,
    warnings,
  };
}

function localCard(entry: LocalLexiconResult, warnings: ReturnType<typeof envWarning> = []) {
  return {
    cardId: `${entry.entryId}:generated:v1`, entryId: entry.entryId,
    word: { display: entry.displayForm, normalized: entry.normalizedWord, matchedForm: entry.matchedForm, stressNote: null },
    meanings: entry.meaningZh ? [{ textZh: entry.meaningZh, partOfSpeech: entry.partsOfSpeech[0] ?? null }] : [],
    morphology: { ...entry.localFields, explanation: null }, etymology: null,
    generatedSentences: [], sourceExamples: [], distinctions: [], commonErrors: [],
    evidence: [
      { type: "LEXICON_FIELD" as const, title: "本地词表", field: null, excerpt: null, sourceDocument: null },
      ...Object.entries(entry.annotations).slice(0, 10).map(([field, values]) => ({
        type: "LEXICON_FIELD" as const,
        title: "本地词表注释",
        field,
        excerpt: values.join("；").slice(0, 1_000) || null,
        sourceDocument: null,
      })),
    ],
    warnings, localState: { favorite: false, archiveIds: [], reviewStatus: "NOT_ADDED" },
  };
}

function mergeGeneratedContent(
  requestId: string,
  planValue: AgentCommandPlan,
  entries: LocalLexiconResult[],
  augmentation: ComposeAugmentationResponse,
): ComposeModelResponse {
  const generatedByEntryId = new Map(
    augmentation.cards
      .filter(card => entries.some(entry => entry.entryId === card.entryId))
      .map(card => [card.entryId, card]),
  );
  const base = mockCompose(requestId, planValue, entries, []);
  const cards = base.cards.map(card => {
    const generated = generatedByEntryId.get(card.entryId);
    if (!generated) return card;
    return {
      ...card,
      word: { ...card.word, stressNote: generated.stressNote },
      morphology: { ...card.morphology, explanation: generated.morphologyExplanation },
      etymology: generated.etymology,
      generatedSentences: generated.generatedSentences,
      distinctions: generated.distinctions,
      commonErrors: generated.commonErrors,
      // The compose request currently carries no structured textbook examples.
      // Model output can therefore never claim SOURCE_EXAMPLE evidence.
      evidence: [
        ...card.evidence,
        ...generated.evidence.filter(item => item.type !== "SOURCE_EXAMPLE"),
      ],
      warnings: generated.warnings,
    };
  });
  return {
    ...base,
    cards,
    deck: base.deck ? { ...base.deck, cardIds: cards.map(card => card.cardId) } : null,
    plainAnswer: augmentation.plainAnswer,
    thinkingSummary: augmentation.thinkingSummary ?? base.thinkingSummary,
    warnings: augmentation.warnings,
  };
}

function isGeneralOutsideLexicon(command: string, words: string[]): boolean {
  if (words.length > 0) return false;
  if (/(?:第.+课|词|名词|动词|形容词|副词|变格|变位|重音|音变|语音交替|例句|造句|比较|辨析|收藏|归档)/.test(command)) {
    return false;
  }
  return /(?:你好|谢谢|帮助|怎么使用|如何使用|介绍(?:一下)?应用|设置|界面|反馈|总结|翻译一段|你能|可以做什么)/.test(command)
    || (command.length > 12 && /[？?]/.test(command));
}

function extractWords(command: string): string[] {
  const russian = command.match(/[А-Яа-яЁё\u0301-]+/g) ?? [];
  return russian.filter(word => !["и"].includes(word.toLowerCase())).slice(0, 10);
}
function chineseNumber(value: string): number | undefined { const mapped: Record<string, number> = { 一:1, 二:2, 三:3, 四:4, 五:5, 六:6, 七:7, 八:8, 九:9, 十:10 }; const n = mapped[value] ?? Number(value); return Number.isInteger(n) && n > 0 ? n : undefined; }
function deckTitle(planValue: AgentCommandPlan): string { return planValue.intent === "COMPARE_WORDS" ? "词语辨析" : planValue.interpretedQuery.lessonIds.length ? `第 ${planValue.interpretedQuery.lessonIds.join("、")} 课` : planValue.interpretedQuery.partsOfSpeech.join("、") || "词卡组"; }
function deckType(planValue: AgentCommandPlan): "LESSON" | "PART_OF_SPEECH" | "COMPARISON" | "TOPIC" | "FUZZY_RESULTS" { if (planValue.intent === "COMPARE_WORDS") return "COMPARISON"; if (planValue.interpretedQuery.lessonIds.length) return "LESSON"; if (planValue.interpretedQuery.partsOfSpeech.length) return "PART_OF_SPEECH"; if (planValue.intent === "FUZZY_LOOKUP") return "FUZZY_RESULTS"; return "TOPIC"; }
function envWarning() { return [{ code: "MOCK_PROVIDER", message: "MockProvider 仅用于联调" }]; }
type RouteContext = {
  currentEntryId: string | null;
  currentCardIds: string[];
  currentDeckId?: string | null;
  locale?: string;
  localSearchMiss?: boolean;
  deckLimit: number;
};
