import { describe, expect, it } from "vitest";
import { route } from "../src/router";
import { parseProviderOutput, normalizeEvidenceType, createAgentFallbackResponse } from "../src/prompt";
import { agentResponseSchema, type AgentRequest } from "../src/schema";

const allow = { ip: { check: async () => true }, conversation: { check: async () => true } };

const sampleRequest: AgentRequest = {
  requestId: "00000000-0000-4000-8000-000000000001",
  conversationId: "00000000-0000-4000-8000-000000000002",
  entryId: "word-1",
  word: "дом",
  normalizedWord: "дом",
  questionType: "CUSTOM",
  question: "词源是什么？",
  entryContext: { meaningZh: "房子", partsOfSpeech: ["名词"], searchForms: ["дом"] },
  knowledgeContext: [],
  client: { appVersion: "test", locale: "zh-CN", platform: "android" },
};

describe("parseProviderOutput normalizer and protocol firewall", () => {
  it("handles standard object arrays for answerSections and evidence", () => {
    const raw = JSON.stringify({
      answer: "дом 来自原始斯拉夫语 *domъ。",
      answerSections: [{ title: "词源", content: "原始印欧语 *dṓm" }],
      evidence: [{ type: "LEXICON_FIELD", title: "本地词表", excerpt: "名词" }],
      warnings: [],
      grounding: { hasLexiconEvidence: true, hasKnowledgeEvidence: false, usedGeneralModelKnowledge: true },
    });
    const parsed = parseProviderOutput(raw, sampleRequest);
    const validated = agentResponseSchema.safeParse(parsed);
    expect(validated.success).toBe(true);
    expect(parsed.answerSections).toEqual([{ title: "词源", content: "原始印欧语 *dṓm" }]);
    expect(parsed.evidence).toHaveLength(1);
    expect(parsed.grounding).toEqual({
      hasLexiconEvidence: true,
      hasKnowledgeEvidence: false,
      usedGeneralModelKnowledge: true,
    });
  });

  it("normalizes answerSections string array into objects", () => {
    const raw = JSON.stringify({
      answer: "详细解答",
      answerSections: ["第一部分内容", "第二部分内容"],
      evidence: [],
      grounding: { hasLexiconEvidence: true },
    });
    const parsed = parseProviderOutput(raw, sampleRequest);
    expect(agentResponseSchema.safeParse(parsed).success).toBe(true);
    expect(parsed.answerSections).toEqual([
      { title: "部分 1", content: "第一部分内容" },
      { title: "部分 2", content: "第二部分内容" },
    ]);
  });

  it("normalizes evidence string array into MODEL_KNOWLEDGE objects", () => {
    const raw = JSON.stringify({
      answer: "详细解答",
      answerSections: [],
      evidence: ["法斯默尔词源词典", "本地知识库第2课"],
    });
    const parsed = parseProviderOutput(raw, sampleRequest);
    expect(agentResponseSchema.safeParse(parsed).success).toBe(true);
    expect(parsed.evidence).toEqual([
      { type: "MODEL_KNOWLEDGE", title: "依据 1", excerpt: "法斯默尔词源词典", field: undefined, sourceDocument: undefined, sectionPath: undefined },
      { type: "MODEL_KNOWLEDGE", title: "依据 2", excerpt: "本地知识库第2课", field: undefined, sourceDocument: undefined, sectionPath: undefined },
    ]);
  });

  it("does not falsify grounding on empty {} or missing fields", () => {
    const raw = JSON.stringify({
      answer: "解答内容",
      grounding: {},
    });
    const parsed = parseProviderOutput(raw, sampleRequest);
    expect(agentResponseSchema.safeParse(parsed).success).toBe(true);
    expect(parsed.grounding).toEqual({
      hasLexiconEvidence: false,
      hasKnowledgeEvidence: false,
      usedGeneralModelKnowledge: false,
    });
  });

  it("normalizes illegal evidence.type to canonical enum", () => {
    expect(normalizeEvidenceType("model")).toBe("MODEL_KNOWLEDGE");
    expect(normalizeEvidenceType("knowledge")).toBe("LOCAL_KNOWLEDGE");
    expect(normalizeEvidenceType("dictionary")).toBe("LEXICON_FIELD");
    expect(normalizeEvidenceType("unknown_arbitrary_type")).toBe("MODEL_KNOWLEDGE");
    expect(normalizeEvidenceType(null)).toBe("MODEL_KNOWLEDGE");

    const raw = JSON.stringify({
      answer: "解答",
      evidence: [
        { type: "dictionary", title: "字典" },
        { type: "knowledge", title: "知识库" },
        { type: "invalid_enum", title: "未知" },
      ],
    });
    const parsed = parseProviderOutput(raw, sampleRequest);
    expect(agentResponseSchema.safeParse(parsed).success).toBe(true);
    expect(parsed.evidence[0].type).toBe("LEXICON_FIELD");
    expect(parsed.evidence[1].type).toBe("LOCAL_KNOWLEDGE");
    expect(parsed.evidence[2].type).toBe("MODEL_KNOWLEDGE");
  });

  it("returns structured fallback instead of exposing raw non-JSON text", () => {
    const raw = "这不是JSON，是一段纯文本回复。";
    const parsed = parseProviderOutput(raw, sampleRequest);
    expect(agentResponseSchema.safeParse(parsed).success).toBe(true);
    expect(parsed.answer).toBe("模型返回格式异常，请重新尝试。");
    expect(parsed.warnings).toContain("PROVIDER_JSON_PARSE_ERROR");
    expect(parsed.grounding).toEqual({
      hasLexiconEvidence: false,
      hasKnowledgeEvidence: false,
      usedGeneralModelKnowledge: false,
    });
  });

  it("strips Markdown code fences correctly", () => {
    const raw = "```json\n{\n  \"answer\": \"来自围栏内回答\",\n  \"answerSections\": [],\n  \"evidence\": []\n}\n```";
    const parsed = parseProviderOutput(raw, sampleRequest);
    expect(agentResponseSchema.safeParse(parsed).success).toBe(true);
    expect(parsed.answer).toBe("来自围栏内回答");
  });

  it("returns structured fallback when answer field is completely missing", () => {
    const raw = JSON.stringify({ randomField: 123 });
    const parsed = parseProviderOutput(raw, sampleRequest);
    expect(agentResponseSchema.safeParse(parsed).success).toBe(true);
    expect(parsed.answer).toBe("模型返回格式异常，请重新尝试。");
    expect(parsed.warnings).toContain("PROVIDER_ANSWER_MISSING");
  });
});

describe("/v1/ask endpoint four-tier routing and firewall", () => {
  it("returns HTTP 400 ONLY when client request schema is invalid", async () => {
    const req = new Request("https://worker.test/v1/ask", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ invalidField: true }),
    });
    const res = await route(req, {}, allow);
    expect(res.status).toBe(400);
    const body = await res.json() as any;
    expect(body.error.code).toBe("INVALID_REQUEST");
  });

  it("returns HTTP 200 with schema-validated response when provider returns valid response", async () => {
    const req = new Request("https://worker.test/v1/ask", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(sampleRequest),
    });
    const res = await route(req, { AGENT_PROVIDER: "mock" }, allow);
    expect(res.status).toBe(200);
    const body = await res.json() as any;
    expect(agentResponseSchema.safeParse(body).success).toBe(true);
    expect(body.answer).toBeDefined();
  });

  it("returns HTTP 503 when provider is unconfigured in deepseek mode", async () => {
    const req = new Request("https://worker.test/v1/ask", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(sampleRequest),
    });
    const res = await route(req, { AGENT_PROVIDER: "deepseek" }, allow);
    expect(res.status).toBe(503);
    const body = await res.json() as any;
    expect(body.error.code).toBe("SERVICE_NOT_CONFIGURED");
  });
});
