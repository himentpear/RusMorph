import type { MultiAgentProvider, Env } from "../providers/AgentProvider";
import { commandPlanSchema, type AgentCommandPlan } from "./schemas";
import { ROUTER_PROMPT } from "./prompts";
export class RouterAgent {
  constructor(private provider: MultiAgentProvider) {}
  async route(command: string, session: unknown, env: Env): Promise<AgentCommandPlan> {
    const text = await this.provider.complete({ task: "ROUTER", systemPrompt: ROUTER_PROMPT, input: { command, session } }, env);
    return commandPlanSchema.parse(JSON.parse(stripFence(text)));
  }
}
export function stripFence(text: string): string { return text.trim().replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, ""); }
