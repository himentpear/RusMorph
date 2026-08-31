import type { MultiAgentProvider, Env } from "../providers/AgentProvider";
import { EXAMPLE_PROMPT } from "./prompts";
export class ExampleAgent { constructor(private provider: MultiAgentProvider) {} complete(input: unknown, env: Env) { return this.provider.complete({ task: "EXAMPLE", systemPrompt: EXAMPLE_PROMPT, input }, env); } }
