import { describe, expect, it } from "vitest";
import { generateVariantQuestion, variantQuestionRequestSchema, variantQuestionResponseSchema } from "../src/VariantQuestionService";
import type { MultiAgentProvider } from "../src/providers/AgentProvider";

const request = {
  conversationId: "conversation",
  sourceQuestionId: "TEM4_2018_8",
  targetGrammarPointId: "SYN_0008",
  grammar: { titleZh: "定语从句", titleRu: "Определительное придаточное", explanation: "Rule" },
  referenceQuestion: {
    source: "TEM4" as const,
    year: "2018",
    stem: "Stem",
    options: { A: "a", B: "b", C: "c", D: "d" },
    correctAnswer: "B" as const,
    analysis: "Analysis",
  },
};

const valid = {
  stem: "Variant stem",
  options: { A: "a", B: "b", C: "c", D: "d" },
  answer: "C",
  analysis: "Variant analysis",
  target_point_id: "SYN_0008",
};

function provider(output: unknown): MultiAgentProvider {
  return {
    ask: async () => { throw new Error("unused"); },
    complete: async () => JSON.stringify(output),
  };
}

describe("grammar variant structured output", () => {
  it("accepts a valid four-option variant", async () => {
    await expect(generateVariantQuestion(request, provider(valid), {})).resolves.toEqual(valid);
  });

  it("rejects missing options and invalid answers", () => {
    expect(variantQuestionResponseSchema.safeParse({ ...valid, options: { A: "a", B: "b", C: "c" } }).success).toBe(false);
    expect(variantQuestionResponseSchema.safeParse({ ...valid, answer: "E" }).success).toBe(false);
  });

  it("rejects malformed requests and a wrong target point", async () => {
    expect(variantQuestionRequestSchema.safeParse({ ...request, sourceQuestionId: "" }).success).toBe(false);
    await expect(generateVariantQuestion(request, provider({ ...valid, target_point_id: "SYN_0006" }), {})).rejects.toMatchObject({ code: "INVALID_MODEL_RESPONSE" });
  });
});
