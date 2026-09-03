import { z } from "zod";
import type { Env, MultiAgentProvider } from "./providers/AgentProvider";

export const wordCardRequestSchema = z.object({
  word: z.string().trim().min(1).max(200),
  context: z.string().trim().max(1000).optional(),
}).strict();

const SYSTEM_PROMPT = `You are a Russian linguistic data generator for the WeRus learning application.

Your responsibility is to generate structured linguistic data, not prose explanations.

Rules:
1. Output valid JSON only.
2. Never output Markdown.
3. Never add comments outside JSON.
4. Preserve correct Russian lexical stress where reliably known. Use combining acute accent (\u0301) on the stressed vowel in display_form and morphology.
5. Do not invent grammatical forms.
6. When uncertain, use null and record the field in agent_meta.uncertain_fields.
7. A surface form must be linked to its lemma.
8. Distinguish lexical information from current surface-form analysis.
9. Chinese explanations must be concise and learner-oriented.
10. Morphology must follow the schema corresponding to the part of speech (noun, verb, adjective).
11. Avoid duplicate information across fields.
12. Do not place multiple facts inside one string when structured fields exist.
13. confidence must be between 0 and 1.
14. If confidence is below 0.75, set needs_review=true.
15. Return exactly one JSON object matching schema_version 2.0.`;

export async function generateWordCard(
  raw: unknown,
  provider: MultiAgentProvider,
  env: Env,
): Promise<Record<string, unknown>> {
  const request = wordCardRequestSchema.parse(raw);
  const userPrompt = `Generate complete linguistic data for the Russian word or form: "${request.word}".
${request.context ? `Context hints: ${request.context}` : ""}
Output the JSON object adhering to schema_version 2.0 directly without any markdown formatting.`;

  const output = await provider.complete({
    task: "MORPHOLOGY",
    systemPrompt: SYSTEM_PROMPT,
    input: {
      instruction: userPrompt,
      word: request.word,
    },
  }, env);

  const cleaned = stripFence(output);
  try {
    const parsed = JSON.parse(cleaned);
    if (parsed && typeof parsed === "object") {
      parsed.schema_version = "2.0";
      return parsed;
    }
  } catch (_e) {
    // If JSON parsing fails, extract substring between first { and last }
    const first = cleaned.indexOf("{");
    const last = cleaned.lastIndexOf("}");
    if (first >= 0 && last > first) {
      const sub = cleaned.substring(first, last + 1);
      const parsed = JSON.parse(sub);
      parsed.schema_version = "2.0";
      return parsed;
    }
  }

  throw new Error("Failed to parse structured JSON from model output");
}

function stripFence(value: string): string {
  return value.trim()
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/\s*```$/, "");
}
