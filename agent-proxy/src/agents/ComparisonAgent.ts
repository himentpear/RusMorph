import type { MultiAgentProvider, Env } from "../providers/AgentProvider";
import { COMPARISON_PROMPT } from "./prompts";
export class ComparisonAgent { constructor(private provider: MultiAgentProvider) {} complete(input: unknown, env: Env) { return this.provider.complete({ task: "COMPARISON", systemPrompt: COMPARISON_PROMPT, input }, env); } }
