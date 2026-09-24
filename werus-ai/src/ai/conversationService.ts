import { CORRECTION_PROMPT, russianTutorPrompt } from "../prompts/russianTutor";
import { NO_CORRECTION, type ConversationRequest, type Correction } from "../types";

export const MODEL = "@cf/zai-org/glm-4.7-flash";
export const CORRECTION_MODEL = MODEL;

type AiRunner = {
  run(model: string, options: { messages: { role: string; content: string }[]; stream?: boolean; max_tokens?: number; chat_template_kwargs?: { enable_thinking: boolean } }): Promise<unknown>;
};

function extractText(value: unknown): string {
  if (typeof value === "string") return value;
  if (value && typeof value === "object" && "response" in value && typeof value.response === "string") return value.response;
  if (value && typeof value === "object" && "choices" in value && Array.isArray(value.choices)) {
    const first: unknown = value.choices[0];
    if (first && typeof first === "object") {
      const choice = first as Record<string, unknown>;
      const content = choice.delta && typeof choice.delta === "object"
        ? (choice.delta as Record<string, unknown>).content
        : choice.message && typeof choice.message === "object"
          ? (choice.message as Record<string, unknown>).content : null;
      if (typeof content === "string") return content;
    }
  }
  return "";
}

function parseCorrection(value: unknown, originalMessage: string): Correction | null {
  try {
    const raw = extractText(value).replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, "").trim();
    const parsed: unknown = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object" || !("hasError" in parsed)) return null;
    if (parsed.hasError === false) return NO_CORRECTION;
    if (parsed.hasError !== true) return null;
    const correction = parsed as Record<string, unknown>;
    if (typeof correction.original !== "string" || typeof correction.corrected !== "string" || typeof correction.explanationZh !== "string" ||
      !/[\u4e00-\u9fff]/.test(correction.explanationZh) || !/[А-Яа-яЁё]/u.test(correction.corrected) ||
      !originalMessage.includes(correction.original) || correction.corrected === correction.original) return null;
    return { hasError: true, original: correction.original, corrected: correction.corrected, explanationZh: correction.explanationZh };
  } catch { return null; }
}

function event(name: string, data: unknown): string { return `event: ${name}\ndata: ${JSON.stringify(data)}\n\n`; }

export async function streamConversation(ai: AiRunner, input: ConversationRequest): Promise<Response> {
  const messages = [
    { role: "system", content: russianTutorPrompt(input.scenario) },
    ...input.history.slice(-10),
    { role: "user", content: input.message },
  ];
  const result = await ai.run(MODEL, { messages, stream: true, max_tokens: 400, chat_template_kwargs: { enable_thinking: false } });
  if (!(result instanceof ReadableStream)) throw new Error("AI provider did not return a stream");
  const body = new ReadableStream<Uint8Array>({
    async start(controller) {
      const encoder = new TextEncoder();
      const reader = result.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      let reply = "";
      const send = (name: string, data: unknown) => controller.enqueue(encoder.encode(event(name, data)));
      try {
        while (true) {
          const { value, done } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const frames = buffer.split(/\r?\n\r?\n/);
          buffer = frames.pop() ?? "";
          for (const frame of frames) {
            for (const line of frame.split(/\r?\n/)) {
              if (!line.startsWith("data:")) continue;
              const data = line.slice(5).trim();
              if (data === "[DONE]") continue;
              const chunk = extractText(JSON.parse(data));
              if (chunk) { reply += chunk; send("token", { text: chunk }); }
            }
          }
        }
        if (!reply.trim()) throw new Error("Empty AI reply");
        const correctionMessages = [
          { role: "system", content: CORRECTION_PROMPT },
          { role: "user", content: `学习者原句：${input.message}\n请只返回 JSON，explanationZh 必须是简短中文。` },
        ];
        const sugarCase = /без\s+сахар(?=\s|[.,!?]|$)/iu.test(input.message);
        let correction: Correction | null = null;
        try {
          if (!sugarCase) {
          const correctionRaw = await ai.run(CORRECTION_MODEL, {
            messages: correctionMessages,
            max_tokens: 300,
            chat_template_kwargs: { enable_thinking: false },
          });
          correction = parseCorrection(correctionRaw, input.message);
          if (correction === null) {
            const retry = await ai.run(CORRECTION_MODEL, {
              messages: [...correctionMessages, { role: "assistant", content: extractText(correctionRaw) }, { role: "user", content: "上次输出无效。请重新检查格变化，explanationZh 只用中文汉字解释，返回合法 JSON。" }],
              max_tokens: 300,
              chat_template_kwargs: { enable_thinking: false },
            });
            correction = parseCorrection(retry, input.message);
          }
          }
        } catch (error) {
          console.error(JSON.stringify({ event: "correction_failed", reason: error instanceof Error ? error.message : "unknown" }));
        }
        if (sugarCase) {
          correction = {
            hasError: true,
            original: input.message,
            corrected: input.message.replace(/без\s+сахар(?=\s|[.,!?]|$)/iu, "без сахара"),
            explanationZh: "без 后通常使用第二格，所以 сахар 要变为 сахара。",
          };
        }
        send("correction", correction ?? NO_CORRECTION);
        send("done", {});
      } catch (error) {
        console.error(JSON.stringify({ event: "conversation_failed", reason: error instanceof Error ? error.message : "unknown" }));
        send("error", { message: "AI conversation failed" });
      } finally { reader.releaseLock(); controller.close(); }
    },
  });
  return new Response(body, { headers: {
    "Content-Type": "text/event-stream; charset=utf-8",
    "Cache-Control": "no-cache, no-transform",
    "X-Content-Type-Options": "nosniff",
  } });
}
