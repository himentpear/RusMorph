import { describe, expect, it, vi } from "vitest";
import { route } from "../src/router";
import { agentRequestSchema } from "../src/schema";

const allow = { ip: { check: async () => true }, conversation: { check: async () => true } };

describe("worker boundary", () => {
  it("accepts an optional structured learning context without changing legacy requests", () => {
    const base = {
      requestId: "00000000-0000-4000-8000-000000000001", conversationId: "00000000-0000-4000-8000-000000000002",
      entryId: "word-1", word: "говорить", normalizedWord: "говорить", questionType: "CUSTOM", question: "解释",
      entryContext: { meaningZh: "说", partsOfSpeech: ["动词"], searchForms: ["говорить"] }, knowledgeContext: [],
      client: { appVersion: "test", locale: "zh-CN", platform: "android" },
    };
    expect(agentRequestSchema.safeParse(base).success).toBe(true);
    expect(agentRequestSchema.safeParse({ ...base, learningContext: { type: "LESSON", courseId: "university-russian-1", lessonId: "ur1-lesson-12", label: "Урок 12" } }).success).toBe(true);
  });
  it("reports degraded DeepSeek health without calling the provider", async () => {
    const response = await route(new Request("https://worker.test/health"), {});
    expect(await response.json()).toMatchObject({ status: "degraded", provider: "deepseek", configured: false, model: "deepseek-v4-flash", speech: { configured: false } });
  });

  it("reports Workers AI speech health without exposing provider secrets", async () => {
    const AI = { run: async () => ({}) };
    const response = await route(new Request("https://worker.test/health"), { AGENT_PROVIDER: "cloudflare", AI, DEEPSEEK_API_KEY: "must-not-leak" });
    const text = await response.text();
    expect(JSON.parse(text)).toMatchObject({ status: "ok", provider: "cloudflare", configured: true, speech: { configured: true, scoring: "workers_ai_timestamp_recognition_proxy", audio_retention: "none" } });
    expect(text).not.toContain("must-not-leak");
  });
  it("reports configured health without exposing a secret", async () => {
    const response = await route(new Request("https://worker.test/health"), { DEEPSEEK_API_KEY: "hidden-test-value" });
    const text = await response.text(); expect(text).toContain('"configured":true'); expect(text).not.toContain("hidden-test-value");
  });
  it("keeps local routing available when the AI service is not configured", async () => {
    const request = new Request("https://worker.test/v1/route", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ requestId: "r", conversationId: "c", command: "знать", context: { currentEntryId: null, currentCardIds: [], currentDeckId: null, locale: "zh-CN" } }) });
    const response = await route(request, {}, allow); const text = await response.text();
    expect(response.status).toBe(200); expect(text).toContain("LOOKUP_WORD"); expect(text).not.toContain("Authorization");
  });
  it("rejects unsupported methods and oversized payload", async () => {
    expect((await route(new Request("https://worker.test/v1/route"), {})).status).toBe(405);
    const request = new Request("https://worker.test/v1/route", { method: "POST", headers: { "content-type": "application/json" }, body: "{}" });
    expect((await route(request, { MAX_REQUEST_BYTES: "1" }, allow)).status).toBe(413);
  });
  it("generates a structured pronunciation example", async () => {
    const request = new Request("https://worker.test/v1/pronunciation-example", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        conversationId: "practice-1",
        difficulty: "beginner",
        topic: "学习",
      }),
    });
    const response = await route(request, { AGENT_PROVIDER: "mock" }, allow);
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({
      russian: "Это тестовый пример.",
      chinese: "这是测试例句。",
      evidenceType: "MODEL_GENERATED_EXAMPLE",
    });
  });

  it("uses the native AI limiter for generated endpoints", async () => {
    const limit = vi.fn(async () => ({ success: false }));
    const request = new Request("https://worker.test/v1/pronunciation-example", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ conversationId: "limited", difficulty: "beginner" }),
    });
    const response = await route(request, { AGENT_PROVIDER: "mock", AI_RATE_LIMITER: { limit } }, allow);
    expect(response.status).toBe(429);
    expect(limit).toHaveBeenCalledWith({ key: "ai:local" });
  });
});
