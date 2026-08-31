declare const process: { env: Record<string, string | undefined>; exit(code?: number): never };
export {};

const apiKey = process.env.DEEPSEEK_API_KEY;
if (!apiKey) {
  console.log("SKIPPED: DEEPSEEK_API_KEY is not configured");
  process.exit(0);
}

const model = process.env.DEEPSEEK_MODEL_FAST ?? "deepseek-v4-flash";
const baseUrl = (process.env.DEEPSEEK_BASE_URL ?? "https://api.deepseek.com").replace(/\/$/, "");
const response = await fetch(`${baseUrl}/chat/completions`, {
  method: "POST",
  headers: { "content-type": "application/json", authorization: `Bearer ${apiKey}` },
  body: JSON.stringify({
    model,
    messages: [
      { role: "system", content: "只输出 JSON，不得输出 Markdown 或解释文字。" },
      { role: "user", content: "请只返回 JSON 对象 {\"status\":\"ok\",\"language\":\"zh-CN\"}" },
    ],
    thinking: { type: "disabled" },
    response_format: { type: "json_object" },
    temperature: 0,
    max_tokens: 100,
    stream: false,
  }),
});
if (!response.ok) throw new Error(`DeepSeek smoke test failed with HTTP ${response.status}`);
const body = await response.json() as { choices?: Array<{ message?: { content?: string | null } }>; usage?: { prompt_tokens?: number; completion_tokens?: number; total_tokens?: number } };
const content = body.choices?.[0]?.message?.content?.trim();
if (!content) throw new Error("DeepSeek smoke test returned empty content");
const parsed = JSON.parse(content.replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, "")) as { status?: string; language?: string };
if (parsed.status !== "ok" || parsed.language !== "zh-CN") throw new Error("DeepSeek smoke test returned an unexpected JSON object");
console.log(JSON.stringify({ model, httpStatus: response.status, status: "ok", usage: body.usage ?? null }));
