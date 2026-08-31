import { describe, expect, it, vi } from "vitest";
import { z } from "zod";
import { DeepSeekProvider } from "../src/providers/DeepSeekProvider";
import { HttpError } from "../src/errors";

const schema = z.object({ status: z.literal("ok") }).strict();
const request = { systemPrompt: "只输出 JSON。示例：{\"status\":\"ok\"}", input: { test: true }, temperature: 0 };
const env = { DEEPSEEK_API_KEY: "unit-test-secret" };
const json = (content: string | null, extra: object = {}) => new Response(JSON.stringify({ choices: [{ message: { content, reasoning_content: "must-not-leak" }, finish_reason: "stop" }], ...extra }), { status: 200, headers: { "content-type": "application/json" } });

describe("DeepSeekProvider", () => {
  it("uses endpoint, bearer auth and the standard deepseek-v4-flash request shape", async () => {
    const fetcher = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => json('{"status":"ok"}'));
    await new DeepSeekProvider(fetcher as typeof fetch).completeJson(request, schema, env);
    const [url, init] = fetcher.mock.calls[0]!; const body = JSON.parse(String(init?.body));
    expect(url).toBe("https://api.deepseek.com/chat/completions"); expect(init?.headers).toMatchObject({ authorization: "Bearer unit-test-secret" });
    expect(body.model).toBe("deepseek-v4-flash"); expect(body).not.toHaveProperty("thinking"); expect(body).not.toHaveProperty("reasoning_effort"); expect(body.response_format).toEqual({ type: "json_object" });
  });
  it("prefers the configured DeepSeek key over an available Workers AI binding", async () => {
    const fetcher = vi.fn(async () => json('{"status":"ok"}'));
    const run = vi.fn(async () => { throw new Error("Workers AI must not be used"); });
    await new DeepSeekProvider(fetcher as typeof fetch).completeJson(request, schema, { ...env, AI: { run } });
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(run).not.toHaveBeenCalled();
  });
  it("uses a configured full OpenAI-compatible endpoint without appending the path twice", async () => {
    const fetcher = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => json('{"status":"ok"}'));
    const endpoint = "https://gateway.ai.cloudflare.com/v1/example/gateway/compat/chat/completions";
    await new DeepSeekProvider(fetcher as typeof fetch).completeJson(request, schema, { ...env, DEEPSEEK_BASE_URL: endpoint });
    expect(fetcher.mock.calls[0]![0]).toBe(endpoint);
  });
  it("trims surrounding whitespace from the configured secret before building the authorization header", async () => {
    const fetcher = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => json('{"status":"ok"}'));
    await new DeepSeekProvider(fetcher as typeof fetch).completeJson(request, schema, { DEEPSEEK_API_KEY: "  unit-test-secret\n" });
    expect(fetcher.mock.calls[0]![1]?.headers).toMatchObject({ authorization: "Bearer unit-test-secret" });
  });
  it("strips a JSON code fence", async () => {
    const provider = new DeepSeekProvider((async () => json('```json\n{"status":"ok"}\n```')) as typeof fetch);
    await expect(provider.completeJson(request, schema, env)).resolves.toEqual({ status: "ok" });
  });
  it("extracts a JSON object after reasoning text", async () => {
    const provider = new DeepSeekProvider((async () => json('<think>reasoning</think>\n{"status":"ok"}')) as typeof fetch);
    await expect(provider.completeJson(request, schema, env)).resolves.toEqual({ status: "ok" });
  });
  it("retries empty content exactly once", async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(json(null)).mockResolvedValueOnce(json('{"status":"ok"}'));
    await expect(new DeepSeekProvider(fetcher).completeJson(request, schema, env)).resolves.toEqual({ status: "ok" }); expect(fetcher).toHaveBeenCalledTimes(2);
  });
  it("returns EMPTY_MODEL_CONTENT after two empty responses", async () => {
    const fetcher = vi.fn(async () => json(""));
    await expectCode(new DeepSeekProvider(fetcher).completeJson(request, schema, env), "EMPTY_MODEL_CONTENT"); expect(fetcher).toHaveBeenCalledTimes(2);
  });
  it("retries malformed JSON once", async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(json("not json")).mockResolvedValueOnce(json('{"status":"ok"}'));
    await new DeepSeekProvider(fetcher).completeJson(request, schema, env); expect(fetcher).toHaveBeenCalledTimes(2);
  });
  it("rejects a schema mismatch without returning model content", async () => {
    await expectCode(new DeepSeekProvider((async () => json('{"status":"wrong"}')) as typeof fetch).completeJson(request, schema, env), "INVALID_MODEL_RESPONSE");
  });
  for (const [status, code] of [[401,"PROVIDER_AUTHENTICATION_FAILED"],[402,"PROVIDER_BALANCE_INSUFFICIENT"],[404,"PROVIDER_MODEL_UNAVAILABLE"],[429,"PROVIDER_RATE_LIMITED"],[503,"PROVIDER_BUSY"]] as const) {
    it(`maps ${status} to ${code}`, async () => { await expectCode(new DeepSeekProvider((async () => new Response("private upstream body", { status })) as typeof fetch).completeJson(request, schema, env), code); });
  }
  it("returns a bounded provider detail for invalid requests", async () => {
    const response = new Response(JSON.stringify({ error: { type: "invalid_request_error", message: "unsupported field" } }), { status: 400 });
    try {
      await new DeepSeekProvider((async () => response) as typeof fetch).completeJson(request, schema, env);
      throw new Error("expected rejection");
    } catch (error) {
      expect(error).toBeInstanceOf(HttpError);
      expect((error as HttpError).code).toBe("PROVIDER_INVALID_REQUEST");
      expect((error as HttpError).message).toContain("invalid_request_error: unsupported field");
    }
  });
  it("maps AbortError to PROVIDER_TIMEOUT", async () => {
    const fetcher = ((_url: string, init?: RequestInit) => new Promise<Response>((_resolve, reject) => init?.signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError"))))) as typeof fetch;
    await expectCode(new DeepSeekProvider(fetcher).completeJson(request, schema, { ...env, DEEPSEEK_TIMEOUT_MS: "1" }), "PROVIDER_TIMEOUT");
  });
  it("maps non-HTTP fetch failures to PROVIDER_NETWORK_ERROR", async () => {
    await expectCode(new DeepSeekProvider((async () => { throw new TypeError("network error"); }) as typeof fetch).completeJson(request, schema, env), "PROVIDER_NETWORK_ERROR");
  });
  it("includes a safe network detail without leaking the configured key", async () => {
    try {
      await new DeepSeekProvider((async () => { throw new TypeError(`socket closed ${env.DEEPSEEK_API_KEY}`); }) as typeof fetch).completeJson(request, schema, env);
      throw new Error("expected rejection");
    } catch (error) {
      expect(error).toBeInstanceOf(HttpError);
      expect((error as HttpError).message).toContain("socket closed [redacted]");
      expect((error as HttpError).message).not.toContain(env.DEEPSEEK_API_KEY);
    }
  });
  it("does not place reasoning content in the validated result", async () => {
    const result = await new DeepSeekProvider((async () => json('{"status":"ok"}')) as typeof fetch).completeJson(request, schema, env);
    expect(JSON.stringify(result)).not.toContain("must-not-leak");
  });
});

async function expectCode(promise: Promise<unknown>, code: string) {
  try { await promise; throw new Error("expected rejection"); } catch (error) { expect(error).toBeInstanceOf(HttpError); expect((error as HttpError).code).toBe(code); }
}
