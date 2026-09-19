import type { AgentRequest, AgentResponse } from "../schema";
export interface Env {
  AGENT_PROVIDER?: string; ENVIRONMENT?: string; ALLOWED_ORIGINS?: string; MAX_REQUEST_BYTES?: string; PROVIDER_TIMEOUT_MS?: string;
  DIFY_BASE_URL?: string; DIFY_API_KEY?: string; DIFY_USER_PREFIX?: string;
  OPENAI_COMPATIBLE_BASE_URL?: string; OPENAI_COMPATIBLE_API_KEY?: string; OPENAI_COMPATIBLE_MODEL?: string;
  PROXY_ACCESS_TOKEN?: string;
  DEEPSEEK_API_KEY?: string;
  DEEPSEEK_BASE_URL?: string;
  DEEPSEEK_MODEL_FAST?: string;
  DEEPSEEK_MODEL_REASONING?: string;
  DEEPSEEK_TIMEOUT_MS?: string;
  DEEPSEEK_ENABLE_REASONING?: string;
  CLOUDFLARE_AI_MODEL?: string;
  AI?: { run(model: string, input: unknown, options?: unknown): Promise<unknown> };
  SPEECH_RATE_LIMITER?: { limit(options: { key: string }): Promise<{ success: boolean }> };
  AI_RATE_LIMITER?: { limit(options: { key: string }): Promise<{ success: boolean }> };
  REVIEW_AUTH_RATE_LIMITER?: { limit(options: { key: string }): Promise<{ success: boolean }> };
  REVIEW_DB?: D1Database;
  REVIEW_AUDIO?: R2Bucket;
  ASSETS?: Fetcher;
  REVIEW_INVITE_CODE_SHA256?: string;
  REVIEW_ADMIN_NAMES?: string;
  REVIEW_SESSION_DAYS?: string;
  REVIEW_AUDIO_RETENTION_DAYS?: string;
  SPEECH_ASR_MODEL?: string;
  SPEECH_TIMEOUT_MS?: string;
  MAX_AUDIO_BYTES?: string;
  MAX_COMMAND_LENGTH?: string;
  MAX_LOCAL_RESULTS?: string;
  MAX_KNOWLEDGE_CHUNKS?: string;
}
export interface AgentProvider { ask(request: AgentRequest, env: Env): Promise<AgentResponse>; }
export interface ProviderAgentRequest { task: string; systemPrompt: string; input: unknown; }
export interface MultiAgentProvider extends AgentProvider {
  complete(request: ProviderAgentRequest, env: Env): Promise<string>;
}
