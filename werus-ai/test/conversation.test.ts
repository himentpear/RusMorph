import { describe, expect, it } from "vitest";
import { createHandler } from "../src/index";

const encoder = new TextEncoder();
const request = (body: unknown) => new Request("https://example.test/api/conversation", {
  method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
});
const valid = { sessionId: "test-session", scenario: "cafe", message: "Я хочу кофе без сахар.", history: [] };

function ai(fail = false) {
  return {
    async run(_model: string, options: { stream?: boolean }) {
      if (fail) throw new Error("provider failed");
      if (options.stream) return new ReadableStream<Uint8Array>({
        start(controller) {
          controller.enqueue(encoder.encode('data: {"choices":[{"delta":{"content":"Здравствуйте! "}}]}\n\ndata: {"choices":[{"delta":{"content":"Что будете заказывать?"}}]}\n\ndata: [DONE]\n\n'));
          controller.close();
        },
      });
      return { choices: [{ message: { content: '{"hasError":true,"original":"без сахар","corrected":"без сахара","explanationZh":"без 后通常使用第二格。"}' } }] };
    },
  };
}

describe("POST /api/conversation", () => {
  it("streams Russian reply and separate Chinese correction", async () => {
    const response = await createHandler(ai())(request(valid));
    expect(response.status).toBe(200);
    expect(response.headers.get("Content-Type")).toContain("text/event-stream");
    const text = await response.text();
    expect(text).toContain('event: token');
    expect(text).toContain('Здравствуйте');
    expect(text).toContain('event: correction');
    expect(text).toContain('без сахара');
    expect(text).toContain('event: done');
  });
  it("rejects empty messages", async () => {
    expect((await createHandler(ai())(request({ ...valid, message: " " }))).status).toBe(400);
  });
  it("rejects invalid JSON", async () => {
    const response = await createHandler(ai())(new Request("https://example.test/api/conversation", { method: "POST", body: "{" }));
    expect(response.status).toBe(400);
  });
  it("returns 502 when provider cannot start", async () => {
    expect((await createHandler(ai(true))(request(valid))).status).toBe(502);
  });
  it("answers CORS preflight", async () => {
    const response = await createHandler(ai())(new Request("https://example.test/api/conversation", { method: "OPTIONS" }));
    expect(response.status).toBe(204);
    expect(response.headers.get("Access-Control-Allow-Origin")).toBe("*");
  });
});
