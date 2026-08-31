import { describe, expect, it } from "vitest";
import { route } from "../src/router";
import { handleCompose, handleRoute } from "../src/AgentService";
import { HttpError } from "../src/errors";

const allow = { ip: { check: async () => true }, conversation: { check: async () => true } };
type RouteContext = { currentEntryId: string | null; currentCardIds: string[]; currentDeckId: string | null; locale: string; localSearchMiss?: boolean; deckLimit?: number };
const context: RouteContext = { currentEntryId: null, currentCardIds: [], currentDeckId: null, locale: "zh-CN" };
async function post(path: string, body: unknown, env: Record<string, string> = { AGENT_PROVIDER: "mock" }) {
  const response = await route(new Request(`https://worker.test${path}`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body) }), env, allow);
  return { status: response.status, body: await response.json() as Record<string, any> };
}
const routeBody = (command: string, override: RouteContext = context) => ({ requestId: "route-1", conversationId: "conversation-1", command, context: override });
const entry = { entryId: "e1", displayForm: "зна́чит", normalizedWord: "значит", meaningZh: "意味着", partsOfSpeech: ["动词"], lesson: 1, matchedForm: null, localFields: { gender: null, declensionClass: null, endingType: null, pluralStressPattern: null, aspect: "未完成体", conjugationClass: null, phoneticAlternation: null } };

describe("two-stage orchestration", () => {
  it("routes a single Russian word without inventing an entryId", async () => {
    const result = await post("/v1/route", routeBody("查一下 зна́чит"));
    expect(result.status).toBe(200); expect(result.body.plan.intent).toBe("LOOKUP_WORD"); expect(result.body.plan.outputMode).toBe("WORD_CARD");
    expect(JSON.stringify(result.body.plan)).not.toContain("entryId");
  });
  it("routes lesson and part of speech to a card deck", async () => {
    const result = await post("/v1/route", routeBody("第一课的动词"));
    expect(result.body.plan.intent).toBe("LOOKUP_BY_LESSON"); expect(result.body.plan.outputMode).toBe("CARD_DECK");
    expect(result.body.plan.interpretedQuery.lessonIds).toEqual([1]); expect(result.body.plan.interpretedQuery.partsOfSpeech).toEqual(["动词"]);
    expect(result.body.plan.retrieval.limit).toBe(50);
  });
  it("respects a larger user-selected deck limit", async () => {
    const result = await post("/v1/route", routeBody(
      "第一课的动词",
      { ...context, deckLimit: 150 },
    ));
    expect(result.body.plan.retrieval.limit).toBe(150);
  });
  it("routes non-lexicon commands to the dedicated general command agent", async () => {
    const routed = await post("/v1/route", routeBody("怎么使用这个应用？"));
    expect(routed.body.plan.intent).toBe("GENERAL_QUESTION");
    expect(routed.body.plan.retrieval.requiresLocalSearch).toBe(false);
    expect(routed.body.plan.agents).toEqual(["GENERAL_COMMAND"]);
    const provider = {
      completeJson: async () => ({
        thinkingSummary: "识别为应用使用问题。",
        reply: "可以直接输入俄语词、中文释义或课号条件。",
        warnings: [],
      }),
    } as any;
    const result = await handleCompose({
      requestId: "general-compose",
      conversationId: "conversation-1",
      command: "怎么使用这个应用？",
      plan: routed.body.plan,
      localResults: [],
      knowledgeContext: [],
    }, { AGENT_PROVIDER: "deepseek" }, provider) as any;
    expect(result.outputMode).toBe("PLAIN_ANSWER");
    expect(result.thinkingSummary).toBe("识别为应用使用问题。");
    expect(result.plainAnswer).toContain("俄语词");
  });
  it("routes intersecting morphology constraints without asking the model to invent matches", async () => {
    const provider = { completeJson: async () => { throw new Error("deterministic route must not call the model"); } } as any;
    const result = await handleRoute(routeBody("给出阳性的音变名词"), { AGENT_PROVIDER: "deepseek" }, provider) as any;
    expect(result.plan.outputMode).toBe("CARD_DECK");
    expect(result.plan.interpretedQuery.partsOfSpeech).toEqual(["名词"]);
    expect(result.plan.interpretedQuery.morphologyFilters.genders).toEqual(["阳性"]);
    expect(result.plan.interpretedQuery.morphologyFilters.hasPhoneticAlternation).toBe(true);
    expect(result.plan.retrieval.fuzzy).toBe(false);
  });
  it("routes comparisons to a deck", async () => {
    const result = await post("/v1/route", routeBody("比较 впро́чем 和 ита́к"));
    expect(result.body.plan.intent).toBe("COMPARE_WORDS"); expect(result.body.plan.outputMode).toBe("CARD_DECK");
  });
  it("uses current context and clarifies a missing reference", async () => {
    const current = await post("/v1/route", routeBody("用这个词造句", { ...context, currentEntryId: "e1" }));
    expect(current.body.plan.intent).toBe("GENERATE_SENTENCE"); expect(current.body.plan.needsClarification).toBe(false);
    const missing = await post("/v1/route", routeBody("收藏这个"));
    expect(missing.body.plan.outputMode).toBe("CLARIFICATION");
  });
  it("recognizes explicitly fuzzy wording", async () => {
    const result = await post("/v1/route", routeBody("这个词可能拼错了 знаит"));
    expect(result.body.plan.intent).toBe("FUZZY_LOOKUP"); expect(result.body.plan.retrieval.fuzzy).toBe(true);
  });
  it("uses the model only to expand queries after Android reports a local miss", async () => {
    let called = false;
    const provider = {
      completeJson: async () => {
        called = true;
        return {
          intent: "FUZZY_LOOKUP",
          outputMode: "WORD_CARD",
          interpretedQuery: {
            raw: "песат",
            words: ["писать"],
            meaning: null,
            lessonIds: [],
            partsOfSpeech: [],
            morphologyFilters: {
              genders: [], declensionClasses: [], endingTypes: [], aspects: [],
              conjugationClasses: [], phoneticAlternations: [],
              hasPhoneticAlternation: null, hasPluralStressPattern: null,
            },
            topic: null,
            referenceTarget: null,
          },
          retrieval: { fuzzy: true, limit: 10, requiresLocalSearch: true },
          agents: ["LEXICON_RETRIEVAL", "CARD_COMPOSER"],
          needsClarification: false,
          clarificationQuestion: null,
        };
      },
    } as any;
    const result = await handleRoute(
      routeBody("песат", { ...context, localSearchMiss: true }),
      { AGENT_PROVIDER: "deepseek" },
      provider,
    ) as any;
    expect(called).toBe(true);
    expect(result.plan.interpretedQuery.words).toEqual(["писать"]);
  });
  it("falls back to a deterministic local route plan when the AI provider fails", async () => {
    const unavailableProvider = {
      completeJson: async () => { throw new HttpError(502, "PROVIDER_NETWORK_ERROR", "unavailable", true); },
    } as any;
    const result = await handleRoute(routeBody("查一个 писать"), { AGENT_PROVIDER: "deepseek" }, unavailableProvider);
    expect((result as any).plan.intent).toBe("LOOKUP_WORD");
    expect((result as any).plan.interpretedQuery.words).toEqual(["писать"]);
  });
  it("returns a factual local card when generation is temporarily unavailable", async () => {
    const routed = await post("/v1/route", routeBody("用 писать 造句"));
    const unavailableProvider = {
      completeJson: async () => { throw new HttpError(502, "PROVIDER_NETWORK_ERROR", "unavailable", true); },
    } as any;
    const result = await handleCompose({ requestId: "compose-fallback", conversationId: "conversation-1", command: "用 писать 造句", plan: routed.body.plan, localResults: [entry], knowledgeContext: [] }, { AGENT_PROVIDER: "deepseek" }, unavailableProvider) as any;
    expect(result.cards[0].entryId).toBe("e1");
    expect(result.cards[0].generatedSentences).toEqual([]);
    expect(result.warnings[0].code).toBe("AI_GENERATION_UNAVAILABLE");
    expect(result.warnings[0].message).toContain("PROVIDER_NETWORK_ERROR");
    expect(result.grounding.usedModelKnowledge).toBe(false);
  });
  it("merges generated examples into a card without allowing model changes to local facts", async () => {
    const routed = await post("/v1/route", routeBody("用 писать 造句"));
    const provider = {
      completeJson: async () => ({
        cards: [{
          entryId: "e1",
          stressNote: null,
          morphologyExplanation: null,
          etymology: null,
          generatedSentences: [{
            russian: "Я пишу письмо.",
            chinese: "我在写信。",
            evidenceType: "MODEL_GENERATED_EXAMPLE",
          }],
          distinctions: [],
          commonErrors: [],
          evidence: [{
            type: "MODEL_GENERATED_EXAMPLE",
            title: "模型生成例句",
            field: null,
            excerpt: null,
            sourceDocument: null,
          }],
          warnings: [],
        }],
        plainAnswer: null,
        warnings: [],
      }),
    } as any;
    const result = await handleCompose(
      { requestId: "compose-generated", conversationId: "conversation-1", command: "用 писать 造句", plan: routed.body.plan, localResults: [entry], knowledgeContext: [] },
      { AGENT_PROVIDER: "deepseek" },
      provider,
    ) as any;
    expect(result.cards[0].entryId).toBe("e1");
    expect(result.cards[0].meanings[0].textZh).toBe(entry.meaningZh);
    expect(result.cards[0].generatedSentences[0].evidenceType).toBe("MODEL_GENERATED_EXAMPLE");
    expect(result.grounding.usedModelKnowledge).toBe(true);
  });
  it("composes ordinary lookup cards from local facts without calling the model", async () => {
    const routed = await post("/v1/route", routeBody("查一个 писать"));
    const provider = { completeJson: async () => { throw new Error("local lookup must not call the model"); } } as any;
    const result = await handleCompose(
      { requestId: "local-compose", conversationId: "conversation-1", command: "查一个 писать", plan: routed.body.plan, localResults: [entry], knowledgeContext: [] },
      { AGENT_PROVIDER: "deepseek" },
      provider,
    ) as any;
    expect(result.cards[0].entryId).toBe("e1");
    expect(result.grounding.usedModelKnowledge).toBe(false);
    expect(result.warnings).toEqual([]);
  });
  it("composes a grounded local word card", async () => {
    const routed = await post("/v1/route", routeBody("查一下 зна́чит"));
    const result = await post("/v1/compose", { requestId: "compose-1", conversationId: "conversation-1", command: "查一下 зна́чит", plan: routed.body.plan, localResults: [entry], knowledgeContext: [] });
    expect(result.status).toBe(200); expect(result.body.cards[0].entryId).toBe("e1"); expect(result.body.cards[0].word.display).toBe("зна́чит");
    expect(result.body.grounding.usedLocalLexicon).toBe(true);
  });
  it("rejects excessive local and knowledge context", async () => {
    const routed = await post("/v1/route", routeBody("查一下 зна́чит"));
    const base = { requestId: "compose-1", conversationId: "conversation-1", command: "查一下 зна́чит", plan: routed.body.plan };
    expect((await post(
      "/v1/compose",
      { ...base, localResults: Array(201).fill(entry), knowledgeContext: [] },
      { AGENT_PROVIDER: "mock", MAX_REQUEST_BYTES: "262144" },
    )).status).toBe(400);
    const chunk = { chunkId: "c", title: "t", category: "x", content: "x", sourceDocument: "d" };
    expect((await post("/v1/compose", { ...base, localResults: [entry], knowledgeContext: Array(5).fill(chunk) })).status).toBe(400);
  });
});
