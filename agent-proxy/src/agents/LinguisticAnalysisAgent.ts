import type { MultiAgentProvider, Env } from "../providers/AgentProvider";
import { LINGUISTIC_PROMPT } from "./prompts";
export class LinguisticAnalysisAgent { constructor(private provider: MultiAgentProvider) {} complete(input: unknown, env: Env) { return this.provider.complete({ task: "LINGUISTIC", systemPrompt: LINGUISTIC_PROMPT, input }, env); } }
