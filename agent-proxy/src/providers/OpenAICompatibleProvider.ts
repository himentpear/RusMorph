import type { MultiAgentProvider, Env, ProviderAgentRequest } from "./AgentProvider";
import type { AgentRequest, AgentResponse } from "../schema";
import { HttpError } from "../errors";
import { parseProviderOutput, providerMessages } from "../prompt";
export class OpenAICompatibleProvider implements MultiAgentProvider {
  async ask(request: AgentRequest, env: Env): Promise<AgentResponse> {
    if (!env.OPENAI_COMPATIBLE_BASE_URL || !env.OPENAI_COMPATIBLE_API_KEY || !env.OPENAI_COMPATIBLE_MODEL) throw new HttpError(502, "PROVIDER_ERROR", "Provider is not configured");
    const response = await fetch(`${env.OPENAI_COMPATIBLE_BASE_URL.replace(/\/$/, "")}/chat/completions`, {
      method: "POST", headers: { "content-type": "application/json", authorization: `Bearer ${env.OPENAI_COMPATIBLE_API_KEY}` },
      body: JSON.stringify({ model: env.OPENAI_COMPATIBLE_MODEL, messages: providerMessages(request), temperature: 0.2, response_format: { type: "json_object" } }),
    });
    if (!response.ok) throw new HttpError(502, "PROVIDER_ERROR", "Upstream provider failed");
    const data = await response.json() as { choices?: Array<{ message?: { content?: string } }> };
    const content = data.choices?.[0]?.message?.content;
    if (!content) throw new HttpError(502, "PROVIDER_ERROR", "Provider response was invalid");
    return parseProviderOutput(content, request);
  }
  async complete(request: ProviderAgentRequest, env: Env): Promise<string> {
    if (!env.OPENAI_COMPATIBLE_BASE_URL || !env.OPENAI_COMPATIBLE_API_KEY || !env.OPENAI_COMPATIBLE_MODEL) throw new HttpError(502, "PROVIDER_ERROR", "Provider is not configured");
    const response = await fetch(`${env.OPENAI_COMPATIBLE_BASE_URL.replace(/\/$/, "")}/chat/completions`, {
      method: "POST", headers: { "content-type": "application/json", authorization: `Bearer ${env.OPENAI_COMPATIBLE_API_KEY}` },
      body: JSON.stringify({ model: env.OPENAI_COMPATIBLE_MODEL, messages: [{ role: "system", content: request.systemPrompt }, { role: "user", content: JSON.stringify(request.input) }], temperature: 0.1, response_format: { type: "json_object" } }),
    });
    if (!response.ok) throw new HttpError(502, "PROVIDER_ERROR", "Upstream provider failed");
    const data = await response.json() as { choices?: Array<{ message?: { content?: string } }> };
    const content = data.choices?.[0]?.message?.content;
    if (!content) throw new HttpError(502, "PROVIDER_ERROR", "Provider response was invalid");
    return content;
  }
}
