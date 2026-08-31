import type { MultiAgentProvider, Env, ProviderAgentRequest } from "./AgentProvider";
import type { AgentRequest, AgentResponse } from "../schema";
import { HttpError } from "../errors";
import { parseProviderOutput } from "../prompt";
export class DifyProvider implements MultiAgentProvider {
  async ask(request: AgentRequest, env: Env): Promise<AgentResponse> {
    if (!env.DIFY_BASE_URL || !env.DIFY_API_KEY) throw new HttpError(502, "PROVIDER_ERROR", "Provider is not configured");
    const response = await fetch(`${env.DIFY_BASE_URL.replace(/\/$/, "")}/chat-messages`, {
      method: "POST", headers: { "content-type": "application/json", authorization: `Bearer ${env.DIFY_API_KEY}` },
      body: JSON.stringify({ inputs: { word: request.word, normalized_word: request.normalizedWord, question_type: request.questionType, entry_context_json: JSON.stringify(request.entryContext), knowledge_context_json: JSON.stringify(request.knowledgeContext) }, query: request.question, response_mode: "blocking", user: `${env.DIFY_USER_PREFIX ?? "rusmorph"}-${request.conversationId}` }),
    });
    if (!response.ok) throw new HttpError(502, "PROVIDER_ERROR", "Upstream provider failed");
    const data = await response.json() as { answer?: string };
    if (!data.answer) throw new HttpError(502, "PROVIDER_ERROR", "Provider response was invalid");
    return parseProviderOutput(data.answer, request);
  }
  async complete(request: ProviderAgentRequest, env: Env): Promise<string> {
    if (!env.DIFY_BASE_URL || !env.DIFY_API_KEY) throw new HttpError(502, "PROVIDER_ERROR", "Provider is not configured");
    const response = await fetch(`${env.DIFY_BASE_URL.replace(/\/$/, "")}/chat-messages`, {
      method: "POST", headers: { "content-type": "application/json", authorization: `Bearer ${env.DIFY_API_KEY}` },
      body: JSON.stringify({ inputs: { agent_task: request.task, structured_input_json: JSON.stringify(request.input), system_prompt: request.systemPrompt }, query: "Return only valid JSON.", response_mode: "blocking", user: "rusmorph-multi-agent" }),
    });
    if (!response.ok) throw new HttpError(502, "PROVIDER_ERROR", "Upstream provider failed");
    const data = await response.json() as { answer?: string };
    if (!data.answer) throw new HttpError(502, "PROVIDER_ERROR", "Provider response was invalid");
    return data.answer;
  }
}
