import { z, type ZodSchema } from "zod";
import type { AgentRequest, AgentResponse } from "../schema";
import { parseProviderOutput, SYSTEM_PROMPT } from "../prompt";
import { HttpError } from "../errors";
import type { Env, MultiAgentProvider, ProviderAgentRequest } from "./AgentProvider";

export interface DeepSeekCompletionRequest {
  systemPrompt: string;
  input: unknown;
  temperature?: number;
  maxTokens?: number;
  model?: "fast" | "reasoning";
}

export interface DeepSeekChatResponse {
  id?: string;
  choices?: Array<{
    index?: number;
    message?: { role?: string; content?: string | null; reasoning_content?: string | null; tool_calls?: unknown[] };
    finish_reason?: string | null;
  }>;
  usage?: { prompt_tokens?: number; completion_tokens?: number; total_tokens?: number };
}

type Fetcher = typeof fetch;

export class DeepSeekProvider implements MultiAgentProvider {
  // Cloudflare's native fetch must not be detached from its runtime context.
  // Keep an injectable wrapper for tests while invoking the global normally.
  constructor(private readonly fetcher: Fetcher = (input, init) => fetch(input, init)) {}

  async completeJson<T>(request: DeepSeekCompletionRequest, schema: ZodSchema<T>, env: Env): Promise<T> {
    if (!env.AI && !env.DEEPSEEK_API_KEY?.trim()) throw new HttpError(503, "SERVICE_NOT_CONFIGURED", "AI 服务尚未配置，本地查词仍可使用。", false);
    const deadline = Date.now() + providerTimeoutMs(env);
    let retryInstruction: string | undefined;
    for (let attempt = 0; attempt < 2; attempt += 1) {
      const remainingMs = deadline - Date.now();
      if (remainingMs <= 0) throw providerTimeoutError();
      const content = await this.requestContent(request, env, retryInstruction, remainingMs);
      if (!content.trim()) {
        if (attempt === 0) { retryInstruction = RETRY_INSTRUCTION; continue; }
        throw new HttpError(502, "EMPTY_MODEL_CONTENT", "AI 返回内容为空，请重新尝试。", true);
      }
      let parsed: unknown;
      try { parsed = JSON.parse(extractJsonObject(stripCodeFence(content))); }
      catch {
        if (attempt === 0) { retryInstruction = RETRY_INSTRUCTION; continue; }
        throw new HttpError(502, "INVALID_MODEL_RESPONSE", "AI 返回格式异常，请重新尝试。", true);
      }
      const result = schema.safeParse(parsed);
      if (!result.success) {
        const issueSummary = result.error.issues
          .slice(0, 8)
          .map(issue => `${issue.path.join(".") || "<root>"}:${issue.code}`)
          .join(",");
        console.warn(JSON.stringify({
          event: "deepseek_schema_mismatch",
          provider: "deepseek",
          issueSummary,
          attempt: attempt + 1,
        }));
        if (attempt === 0) {
          retryInstruction = `${RETRY_INSTRUCTION} 必须修复这些字段路径：${issueSummary}。`;
          continue;
        }
        throw new HttpError(502, "INVALID_MODEL_RESPONSE", "AI 返回格式异常，请重新尝试。", true);
      }
      return result.data;
    }
    throw new HttpError(502, "INVALID_MODEL_RESPONSE", "AI 返回格式异常，请重新尝试。", true);
  }

  async complete(request: ProviderAgentRequest, env: Env): Promise<string> {
    const value = await this.completeJson({ systemPrompt: request.systemPrompt, input: request.input }, passthroughSchema, env);
    return JSON.stringify(value);
  }

  async ask(request: AgentRequest, env: Env): Promise<AgentResponse> {
    const systemPrompt = [
      SYSTEM_PROMPT,
      "你必须输出严格符合以下结构的单个合法 JSON 对象，不得输出 Markdown 代码块或其他多余文字：",
      JSON.stringify({
        answer: "清晰详细的俄语语言学与形态学解答",
        answerSections: [{ title: "小节标题", content: "小节内容" }],
        evidence: [{ type: "LEXICON_FIELD | LOCAL_KNOWLEDGE | MODEL_KNOWLEDGE", title: "依据名称", excerpt: "依据摘录（可选）" }],
        warnings: [],
        grounding: { hasLexiconEvidence: true, hasKnowledgeEvidence: false, usedGeneralModelKnowledge: true },
      }),
    ].join("\n\n");

    const value = await this.completeJson({
      systemPrompt,
      input: {
        word: request.word,
        normalizedWord: request.normalizedWord,
        question: request.question,
        questionType: request.questionType,
        entryContext: request.entryContext,
        knowledgeContext: request.knowledgeContext,
        learningContext: request.learningContext,
      },
    }, passthroughSchema, env);
    return parseProviderOutput(JSON.stringify(value), request);
  }

  private async requestContent(request: DeepSeekCompletionRequest, env: Env, retryInstruction: string | undefined, timeoutMs: number): Promise<string> {
    const reasoning = request.model === "reasoning" && env.DEEPSEEK_ENABLE_REASONING === "true";
    const model = reasoning ? (env.DEEPSEEK_MODEL_REASONING ?? "deepseek-v4-pro") : (env.DEEPSEEK_MODEL_FAST ?? "deepseek-v4-flash");
    const cloudflareOnly = env.AGENT_PROVIDER === "cloudflare";
    const apiKey = cloudflareOnly ? undefined : env.DEEPSEEK_API_KEY?.trim();
    if (!env.AI && !apiKey) throw new HttpError(503, "SERVICE_NOT_CONFIGURED", "AI 服务尚未配置，本地查词仍可使用。", false);
    // Header values must be visible ASCII without embedded whitespace. This
    // distinguishes a malformed secret from an upstream network failure
    // without ever logging or returning the secret itself.
    if (apiKey && !/^[\x21-\x7e]+$/.test(apiKey)) {
      throw new HttpError(503, "PROVIDER_CREDENTIAL_FORMAT_INVALID", "AI 密钥格式无效，请在 Worker Secret 中重新设置。", false);
    }
    const controller = new AbortController();
    const startedAt = Date.now();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const messages = [
        { role: "system", content: request.systemPrompt },
        { role: "user", content: JSON.stringify(request.input) },
        ...(retryInstruction ? [{ role: "user", content: retryInstruction }] : []),
      ];
      // Explicit Cloudflare mode must never use a stale DeepSeek secret.
      // In DeepSeek mode, a configured key remains authoritative.
      if (!apiKey && env.AI) {
        const bindingModel = env.CLOUDFLARE_AI_MODEL ?? "@cf/deepseek-ai/deepseek-r1-distill-qwen-32b";
        const raw = await Promise.race([
          env.AI.run(bindingModel, {
          messages,
          response_format: { type: "json_object" },
          temperature: request.temperature ?? 0.1,
          max_tokens: request.maxTokens ?? 2_000,
          }, { gateway: { id: "ruscodex", collectLog: false } }),
          rejectAfter(timeoutMs),
        ]);
        const content = bindingContent(raw);
        console.info(JSON.stringify({ event: "cloudflare_ai_request_succeeded", provider: "cloudflare_ai", model: bindingModel, latencyMs: Date.now() - startedAt }));
        return content;
      }
      const configuredBaseUrl = (env.DEEPSEEK_BASE_URL ?? "https://api.deepseek.com").replace(/\/$/, "");
      const endpoint = configuredBaseUrl.endsWith("/chat/completions") ? configuredBaseUrl : `${configuredBaseUrl}/chat/completions`;
      const response = await this.fetcher(endpoint, {
        method: "POST",
        headers: { "content-type": "application/json", authorization: `Bearer ${apiKey}` },
        body: JSON.stringify({
          model, messages,
          response_format: { type: "json_object" },
          temperature: request.temperature ?? 0.1,
          max_tokens: request.maxTokens ?? 2_000,
          stream: false,
        }),
        signal: controller.signal,
      });
      if (!response.ok) {
        const detail = await readProviderErrorDetail(response, apiKey);
        console.warn(JSON.stringify({
          event: "deepseek_request_failed",
          provider: "deepseek",
          model,
          status: response.status,
          latencyMs: Date.now() - startedAt,
          detail,
        }));
        throw mapProviderStatus(response.status, detail);
      }
      const data = await response.json() as DeepSeekChatResponse;
      console.info(JSON.stringify({
        event: "deepseek_request_succeeded",
        provider: "deepseek",
        model,
        status: response.status,
        latencyMs: Date.now() - startedAt,
        totalTokens: data.usage?.total_tokens,
      }));
      const choice = data.choices?.[0];
      if (!choice?.message) return "";
      if (choice.finish_reason === "length") throw new HttpError(502, "INVALID_MODEL_RESPONSE", "AI 返回内容被截断，请重新尝试。", true);
      return choice.message.content ?? "";
    } catch (error) {
      if (error instanceof HttpError) throw error;
      if (error instanceof Error && (error.name === "AbortError" || error.name === "ProviderTimeoutError")) throw providerTimeoutError();
      const detail = safeNetworkErrorDetail(error, apiKey);
      console.error(JSON.stringify({
        event: "deepseek_network_failed",
        provider: "deepseek",
        model,
        latencyMs: Date.now() - startedAt,
        errorName: error instanceof Error ? error.name : "UnknownError",
        detail,
      }));
      throw new HttpError(502, "PROVIDER_NETWORK_ERROR", `AI 服务网络暂时不可用：${detail}`, true);
    } finally { clearTimeout(timer); }
  }
}

function bindingContent(raw: unknown): string {
  if (typeof raw === "string") return raw;
  if (typeof raw !== "object" || raw === null) return "";
  const value = raw as { response?: unknown; choices?: Array<{ message?: { content?: unknown } }> };
  if (typeof value.response === "string") return value.response;
  const content = value.choices?.[0]?.message?.content;
  return typeof content === "string" ? content : "";
}

const RETRY_INSTRUCTION = "上一次响应为空或格式无效。请立即返回一个非空、合法、严格符合示例结构的 JSON 对象，不要输出 Markdown。";

function stripCodeFence(text: string): string {
  return text.trim().replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, "");
}

// Reasoning-capable models can prepend private reasoning or a short sentence
// before the requested JSON. The schema remains the final authority.
function extractJsonObject(text: string): string {
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  return start >= 0 && end > start ? text.slice(start, end + 1) : text;
}

function mapProviderStatus(status: number, detail?: string): HttpError {
  switch (status) {
    case 400: return new HttpError(502, "PROVIDER_INVALID_REQUEST", `AI 请求参数无效${detail ? `：${detail}` : ""}。`, false);
    case 401: return new HttpError(502, "PROVIDER_AUTHENTICATION_FAILED", "AI 服务认证失败，请联系维护者。", false);
    case 402: return new HttpError(502, "PROVIDER_BALANCE_INSUFFICIENT", "AI 服务额度不足，本地查词仍可正常使用。", false);
    case 404: return new HttpError(502, "PROVIDER_MODEL_UNAVAILABLE", "AI 模型当前不可用，请稍后重试。", true);
    case 422: return new HttpError(502, "PROVIDER_INVALID_PARAMETERS", "AI 请求参数无效。", false);
    case 429: return new HttpError(429, "PROVIDER_RATE_LIMITED", "AI 请求过于频繁，请稍后再试。", true);
    case 500: return new HttpError(502, "PROVIDER_INTERNAL_ERROR", "AI 服务暂时不可用。", true);
    case 503: return new HttpError(503, "PROVIDER_BUSY", "AI 服务当前繁忙，请稍后重试。", true);
    default: return new HttpError(502, "PROVIDER_INTERNAL_ERROR", "AI 服务暂时不可用。", status >= 500);
  }
}

function boundedNumber(raw: string | undefined, fallback: number, min: number, max: number): number {
  const value = Number(raw ?? fallback);
  return Number.isFinite(value) ? Math.min(max, Math.max(min, value)) : fallback;
}

function providerTimeoutMs(env: Env): number {
  return boundedNumber(env.DEEPSEEK_TIMEOUT_MS, 25_000, 1_000, 45_000);
}

function providerTimeoutError(): HttpError {
  return new HttpError(504, "PROVIDER_TIMEOUT", "AI 回答超时，已改用本地词库事实。", true);
}

function rejectAfter(timeoutMs: number): Promise<never> {
  return new Promise((_, reject) => {
    setTimeout(() => {
      const error = new Error("Provider timed out");
      error.name = "ProviderTimeoutError";
      reject(error);
    }, timeoutMs);
  });
}

function safeNetworkErrorDetail(error: unknown, apiKey: string | undefined): string {
  return redactProviderDetail(error instanceof Error ? error.message : "Unknown network error", apiKey);
}

async function readProviderErrorDetail(response: Response, apiKey: string | undefined): Promise<string | undefined> {
  const reader = response.body?.getReader();
  if (!reader) return undefined;
  const chunks: Uint8Array[] = [];
  let size = 0;
  try {
    while (size < 2_048) {
      const { done, value } = await reader.read();
      if (done) break;
      const remaining = 2_048 - size;
      const chunk = value.byteLength > remaining ? value.slice(0, remaining) : value;
      chunks.push(chunk);
      size += chunk.byteLength;
      if (chunk.byteLength < value.byteLength) break;
    }
  } finally {
    await reader.cancel().catch(() => undefined);
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  const text = new TextDecoder().decode(bytes);
  let detail = text;
  try {
    const parsed = JSON.parse(text) as { error?: { message?: unknown; type?: unknown }; message?: unknown };
    const message = parsed.error?.message ?? parsed.message;
    const type = parsed.error?.type;
    detail = [typeof type === "string" ? type : null, typeof message === "string" ? message : null].filter(Boolean).join(": ") || text;
  } catch {
    // Plain-text upstream errors are still useful after redaction.
  }
  return redactProviderDetail(detail, apiKey);
}

function redactProviderDetail(raw: string, apiKey: string | undefined): string {
  let detail = raw;
  if (apiKey) detail = detail.split(apiKey).join("[redacted]");
  return detail
    .replace(/Bearer\s+\S+/gi, "Bearer [redacted]")
    .replace(/[\r\n\t]+/g, " ")
    .trim()
    .slice(0, 160) || "Unknown network error";
}

const passthroughSchema = z.unknown();
