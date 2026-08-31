import { z } from "zod";
import type { Env, MultiAgentProvider } from "./providers/AgentProvider";

export const pronunciationExampleRequestSchema = z.object({
  conversationId: z.string().min(1).max(200),
  difficulty: z.enum(["beginner", "intermediate", "advanced"]),
  topic: z.string().trim().max(120).optional(),
}).strict();

const generatedExamplesSchema = z.object({
  sentences: z.array(z.object({
    russian: z.string().trim().min(1).max(300),
    chinese: z.string().trim().min(1).max(300),
    evidenceType: z.literal("MODEL_GENERATED_EXAMPLE").optional(),
  }).strict()).min(1).max(3),
}).strict();

export async function generatePronunciationExample(
  raw: unknown,
  provider: MultiAgentProvider,
  env: Env,
) {
  const request = pronunciationExampleRequestSchema.parse(raw);
  const levelInstruction = {
    beginner: "A1-A2，5到8个词，使用高频词和简单现在时",
    intermediate: "B1，8到12个词，可包含一个常用从句",
    advanced: "B2-C1，10到16个词，表达自然但避免生僻专名",
  }[request.difficulty];
  const topic = request.topic?.trim() || "日常学习与生活";
  const systemPrompt = [
    "你是俄语口语练习例句生成器。",
    "只输出严格 JSON，不要 Markdown。",
    '{"sentences":[{"russian":"俄语句子","chinese":"准确中文翻译","evidenceType":"MODEL_GENERATED_EXAMPLE"}]}',
    "俄语必须语法正确、自然、适合朗读，不添加重音符号。",
  ].join("\n");
  const output = await provider.complete({
    task: "EXAMPLE",
    systemPrompt,
    input: {
      instruction: `生成1句${levelInstruction}的俄语朗读例句。`,
      topic,
    },
  }, env);
  const parsed = generatedExamplesSchema.parse(JSON.parse(stripFence(output)));
  const example = parsed.sentences[0];
  return {
    russian: example.russian,
    chinese: example.chinese,
    evidenceType: "MODEL_GENERATED_EXAMPLE" as const,
  };
}

function stripFence(value: string): string {
  return value.trim()
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/\s*```$/, "");
}
