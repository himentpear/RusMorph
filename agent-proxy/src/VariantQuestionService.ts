import { z } from "zod";
import type { Env, MultiAgentProvider } from "./providers/AgentProvider";
import { HttpError } from "./errors";

const optionSchema = z.object({
  A: z.string().trim().min(1).max(500),
  B: z.string().trim().min(1).max(500),
  C: z.string().trim().min(1).max(500),
  D: z.string().trim().min(1).max(500),
}).strict();

export const variantQuestionRequestSchema = z.object({
  conversationId: z.string().min(1).max(200),
  sourceQuestionId: z.string().min(1).max(200),
  targetGrammarPointId: z.string().min(1).max(200),
  grammar: z.object({
    titleZh: z.string().trim().min(1).max(500),
    titleRu: z.string().trim().min(1).max(500),
    explanation: z.string().trim().min(1).max(8_000),
  }).strict(),
  referenceQuestion: z.object({
    source: z.literal("TEM4"),
    year: z.string().trim().min(1).max(40),
    stem: z.string().trim().min(1).max(2_000),
    options: optionSchema,
    correctAnswer: z.enum(["A", "B", "C", "D"]),
    analysis: z.string().trim().min(1).max(8_000),
  }).strict(),
}).strict();

export const variantQuestionResponseSchema = z.object({
  stem: z.string().trim().min(1).max(2_000),
  options: optionSchema,
  answer: z.enum(["A", "B", "C", "D"]),
  analysis: z.string().trim().min(1).max(8_000),
  target_point_id: z.string().trim().min(1).max(200),
}).strict();

export async function generateVariantQuestion(raw: unknown, provider: MultiAgentProvider, env: Env) {
  const request = variantQuestionRequestSchema.parse(raw);
  const systemPrompt = [
    "你是俄语专四语法题命题器，只返回严格 JSON，不要 Markdown。",
    '{"stem":"...","options":{"A":"...","B":"...","C":"...","D":"..."},"answer":"B","analysis":"...","target_point_id":"..."}',
    "生成一道四选一单选题，必须且只能有一个正确答案。",
    "保持参考题的核心语法能力、相近难度和类似干扰项逻辑。",
    "必须改变句子语义、人物、场景、词汇和表层表达；禁止只替换姓名或单个名词。",
    "target_point_id 必须与请求完全一致。",
  ].join("\n");
  const output = await provider.complete({
    task: "GRAMMAR_VARIANT",
    systemPrompt,
    input: {
      TARGET_GRAMMAR: {
        point_id: request.targetGrammarPointId,
        title_zh: request.grammar.titleZh,
        title_ru: request.grammar.titleRu,
        explanation: request.grammar.explanation,
      },
      REFERENCE_QUESTION: request.referenceQuestion,
      REFERENCE_ANALYSIS: request.referenceQuestion.analysis,
      SOURCE: { type: "TEM4", year: request.referenceQuestion.year },
    },
  }, env);
  const parsed = variantQuestionResponseSchema.parse(JSON.parse(stripFence(output)));
  if (parsed.target_point_id !== request.targetGrammarPointId) {
    throw new HttpError(502, "INVALID_MODEL_RESPONSE", "AI 返回的语法点与请求不一致，请重试。", true);
  }
  return parsed;
}

function stripFence(value: string): string {
  return value.trim().replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, "");
}
