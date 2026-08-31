import type { MultiAgentProvider, Env } from "../providers/AgentProvider";
import { cardDeckSchema, wordCardSchema } from "./schemas";
import { CARD_COMPOSER_PROMPT } from "./prompts";
import { stripFence } from "./RouterAgent";
import { HttpError } from "../errors";
export class CardComposerAgent {
  constructor(private provider: MultiAgentProvider) {}
  async compose(input: unknown, deck: boolean, env: Env) {
    const text = await this.provider.complete({ task: "CARD_COMPOSER", systemPrompt: CARD_COMPOSER_PROMPT, input }, env);
    try {
      const parsed = JSON.parse(stripFence(text));
      return deck ? cardDeckSchema.parse(parsed) : wordCardSchema.parse(parsed);
    } catch {
      throw new HttpError(502, "INVALID_CARD_RESPONSE", "Card provider returned invalid structured data");
    }
  }
}
