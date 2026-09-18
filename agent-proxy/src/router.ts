import { ZodError } from "zod";
import { agentRequestSchema, agentResponseSchema } from "./schema";
import { errorResponse, HttpError } from "./errors";
import { authorize, MemoryRateLimiter, type RateLimiter } from "./security";
import type { MultiAgentProvider, Env } from "./providers/AgentProvider";
import { MockProvider } from "./providers/MockProvider";
import { DifyProvider } from "./providers/DifyProvider";
import { OpenAICompatibleProvider } from "./providers/OpenAICompatibleProvider";
import { DeepSeekProvider } from "./providers/DeepSeekProvider";
import { createAgentFallbackResponse } from "./prompt";
import { handleCompose, handleRoute } from "./AgentService";
import {
  generatePronunciationExample,
  pronunciationExampleRequestSchema,
} from "./PronunciationExampleService";
import { analyzePronunciation, transcribeSpeech } from "./SpeechService";
import { handleReviewRequest } from "./ReviewService";

const ipLimiter = new MemoryRateLimiter(30, 60_000);
const conversationLimiter = new MemoryRateLimiter(8, 30_000);

function providerFor(env: Env): MultiAgentProvider {
  switch (env.AGENT_PROVIDER ?? "deepseek") {
    case "deepseek": return new DeepSeekProvider();
    case "cloudflare": return new DeepSeekProvider();
    case "mock": return new MockProvider();
    case "dify": return new DifyProvider();
    case "openai-compatible": return new OpenAICompatibleProvider();
    default: throw new HttpError(500, "INTERNAL_ERROR", "Unknown provider configuration");
  }
}

export async function route(
  request: Request,
  env: Env,
  rates: { ip: RateLimiter; conversation: RateLimiter } = { ip: ipLimiter, conversation: conversationLimiter },
): Promise<Response> {
  const generatedRequestId = crypto.randomUUID();
  try {
    const url = new URL(request.url);
    if (url.pathname === "/health") {
      if (request.method !== "GET") throw new HttpError(405, "METHOD_NOT_ALLOWED", "Method not allowed");
      const cloudflare = env.AGENT_PROVIDER === "cloudflare";
      const configured = cloudflare ? Boolean(env.AI) : Boolean(env.DEEPSEEK_API_KEY || env.AI);
      return Response.json({
        status: configured ? "ok" : "degraded",
        provider: cloudflare ? "cloudflare" : "deepseek",
        configured,
        model: cloudflare ? (env.CLOUDFLARE_AI_MODEL ?? "@cf/meta/llama-3.1-8b-instruct-fp8-fast") : (env.DEEPSEEK_MODEL_FAST ?? "deepseek-v4-flash"),
        speech: {
          configured: Boolean(env.AI),
          provider: "workers_ai",
          model: env.SPEECH_ASR_MODEL ?? "@cf/openai/whisper-large-v3-turbo",
          scoring: "workers_ai_timestamp_recognition_proxy",
          audio_retention: "none",
        },
        review: {
          configured: Boolean(env.REVIEW_DB && env.REVIEW_AUDIO && env.REVIEW_INVITE_CODE_SHA256),
          audio_retention_days: Number(env.REVIEW_AUDIO_RETENTION_DAYS ?? 90),
        },
      });
    }
    if (url.pathname === "/review" && request.method === "GET") {
      return Response.redirect(`${url.origin}/review/`, 308);
    }
    if (url.pathname.startsWith("/review/") && request.method === "GET" && env.ASSETS) {
      const asset = await env.ASSETS.fetch(request);
      const headers = new Headers(asset.headers);
      headers.set("content-security-policy", "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; media-src 'self' blob:; img-src 'self' data:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
      headers.set("permissions-policy", "microphone=(self), camera=(), geolocation=()");
      headers.set("referrer-policy", "no-referrer");
      headers.set("x-content-type-options", "nosniff");
      headers.set("x-frame-options", "DENY");
      return new Response(asset.body, { status: asset.status, statusText: asset.statusText, headers });
    }
    if (url.pathname.startsWith("/api/review/")) {
      return await handleReviewRequest(request, env, providerFor(env));
    }
    if (url.pathname === "/api/asr/transcribe" || url.pathname === "/api/pronunciation/analyze") {
      if (request.method !== "POST") throw new HttpError(405, "METHOD_NOT_ALLOWED", "Method not allowed");
      if (!await enforceSpeechRate(request, env, rates.ip)) {
        throw new HttpError(429, "RATE_LIMITED", "请求过于频繁，请稍后再试。", true);
      }
      const result = url.pathname === "/api/asr/transcribe"
        ? await transcribeSpeech(request, env)
        : await analyzePronunciation(request, env);
      return Response.json(result);
    }
    if (url.pathname === "/v1/route" || url.pathname === "/v1/compose") {
      if (request.method !== "POST") throw new HttpError(405, "METHOD_NOT_ALLOWED", "Method not allowed");
      const raw = await readJson(request, env);
      await enforceRates(request, raw, rates, env);
      const result = url.pathname === "/v1/route" ? await handleRoute(raw, env) : await handleCompose(raw, env);
      return Response.json(result);
    }
    if (url.pathname === "/v1/pronunciation-example") {
      if (request.method !== "POST") throw new HttpError(405, "METHOD_NOT_ALLOWED", "Method not allowed");
      const raw = await readJson(request, env);
      pronunciationExampleRequestSchema.parse(raw);
      await enforceRates(request, raw, rates, env);
      return Response.json(await generatePronunciationExample(raw, providerFor(env), env));
    }
    if (url.pathname !== "/v1/ask") throw new HttpError(404, "NOT_FOUND", "Not found");
    if (request.method !== "POST") throw new HttpError(405, "METHOD_NOT_ALLOWED", "Method not allowed");
    const raw = await readJson(request, env);
    const requestResult = agentRequestSchema.safeParse(raw);
    if (!requestResult.success) {
      return errorResponse(new HttpError(400, "INVALID_REQUEST", "请求格式无效。"), generatedRequestId);
    }
    try {
      await enforceRates(request, raw, rates, env);
      const providerResponse = await providerFor(env).ask(requestResult.data, env);
      const responseResult = agentResponseSchema.safeParse(providerResponse);
      if (!responseResult.success) {
        console.error(JSON.stringify({
          event: "agent_response_invalid",
          requestId: requestResult.data.requestId,
          issues: responseResult.error.issues,
        }));
        return Response.json(createAgentFallbackResponse(requestResult.data, "RESPONSE_SCHEMA_MISMATCH"), { status: 200 });
      }
      return Response.json(responseResult.data);
    } catch (error) {
      if (error instanceof HttpError) return errorResponse(error, generatedRequestId);
      console.error(JSON.stringify({
        event: "agent_service_failure",
        requestId: requestResult.data.requestId,
        error: error instanceof Error ? error.message : String(error),
      }));
      return errorResponse(new HttpError(502, "AGENT_PROVIDER_ERROR", "AI 服务暂时不可用。", true), generatedRequestId);
    }
  } catch (error) {
    if (error instanceof HttpError) return errorResponse(error, generatedRequestId);
    if (error instanceof ZodError) return errorResponse(new HttpError(400, "INVALID_REQUEST", "请求格式无效。"), generatedRequestId);
    return errorResponse(new HttpError(500, "INTERNAL_ERROR", "服务暂时不可用。", true), generatedRequestId);
  }
}

async function readJson(request: Request, env: Env): Promise<unknown> {
  if (!request.headers.get("content-type")?.toLowerCase().startsWith("application/json")) throw new HttpError(400, "INVALID_REQUEST", "Content-Type must be application/json");
  authorize(request, env);
  const maxBytes = boundedNumber(env.MAX_REQUEST_BYTES, 65_536, 1, 262_144);
  const declared = Number(request.headers.get("content-length") ?? 0);
  if (declared > maxBytes) throw new HttpError(413, "PAYLOAD_TOO_LARGE", "请求内容过大。");
  const text = await request.text();
  if (new TextEncoder().encode(text).byteLength > maxBytes) throw new HttpError(413, "PAYLOAD_TOO_LARGE", "请求内容过大。");
  try { return JSON.parse(text); } catch { throw new HttpError(400, "INVALID_REQUEST", "JSON 格式无效。"); }
}

async function enforceRates(request: Request, raw: unknown, rates: { ip: RateLimiter; conversation: RateLimiter }, env: Env): Promise<void> {
  const ip = request.headers.get("cf-connecting-ip") ?? "local";
  const conversationId = typeof raw === "object" && raw !== null && "conversationId" in raw ? String((raw as { conversationId: unknown }).conversationId) : "legacy";
  const nativeAllowed = env.AI_RATE_LIMITER
    ? (await env.AI_RATE_LIMITER.limit({ key: `ai:${ip}` })).success
    : true;
  if (!nativeAllowed || !await rates.ip.check(`ip:${ip}`) || !await rates.conversation.check(`conversation:${conversationId}`)) throw new HttpError(429, "RATE_LIMITED", "请求过于频繁，请稍后再试。", true);
}

async function enforceSpeechRate(request: Request, env: Env, fallback: RateLimiter): Promise<boolean> {
  const ip = request.headers.get("cf-connecting-ip") ?? "local";
  if (env.SPEECH_RATE_LIMITER) return (await env.SPEECH_RATE_LIMITER.limit({ key: `speech:${ip}` })).success;
  return fallback.check(`speech-ip:${ip}`);
}

function boundedNumber(raw: string | undefined, fallback: number, min: number, max: number): number {
  const value = Number(raw ?? fallback);
  return Number.isFinite(value) ? Math.min(max, Math.max(min, value)) : fallback;
}
