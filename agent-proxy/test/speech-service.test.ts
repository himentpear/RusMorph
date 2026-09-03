import { describe, expect, it, vi } from "vitest";
import { route } from "../src/router";
import type { Env } from "../src/providers/AgentProvider";

const allow = { ip: { check: async () => true }, conversation: { check: async () => true } };

function whisperResult() {
  return {
    transcription_info: { language: "ru", language_probability: 0.99, duration: 2.4, duration_after_vad: 2.1 },
    text: "Я люблю русский язык.",
    segments: [{
      start: 0.1, end: 2.2, text: "Я люблю русский язык.", avg_logprob: -0.08, no_speech_prob: 0.01,
      words: [
        { word: "Я", start: 0.10, end: 0.32 },
        { word: "люблю", start: 0.38, end: 0.82 },
        { word: "русский", start: 0.95, end: 1.48 },
        { word: "язык.", start: 1.60, end: 2.08 },
      ],
    }],
  };
}

function transcriptionResult(text: string, words: string[], language = "ru") {
  return {
    transcription_info: { language, language_probability: 0.99, duration: 2.4, duration_after_vad: 2.1 },
    text,
    segments: [{
      start: 0.1, end: 2.2, text, avg_logprob: -0.05, no_speech_prob: 0.01,
      words: words.map((word, index) => ({ word, start: 0.1 + index * 0.45, end: 0.42 + index * 0.45 })),
    }],
  };
}

function multipart(fields: Record<string, string> = {}): Request {
  const form = new FormData();
  form.append("audio", new File([new Uint8Array([1, 2, 3, 4])], "speech.m4a", { type: "audio/mp4" }));
  for (const [key, value] of Object.entries(fields)) form.append(key, value);
  return new Request("https://worker.test/api/pronunciation/analyze", { method: "POST", body: form });
}

describe("Workers AI speech routes", () => {
  it("uses the Cloudflare-native limiter before invoking paid AI", async () => {
    const run = vi.fn(async () => whisperResult());
    const limit = vi.fn(async () => ({ success: false }));
    const form = new FormData();
    form.append("audio", new File([new Uint8Array([1, 2])], "speech.m4a", { type: "audio/mp4" }));
    const response = await route(
      new Request("https://worker.test/api/asr/transcribe", { method: "POST", body: form }),
      { AI: { run }, SPEECH_RATE_LIMITER: { limit } },
      allow,
    );
    expect(response.status).toBe(429);
    expect(limit).toHaveBeenCalledWith({ key: "speech:local" });
    expect(run).not.toHaveBeenCalled();
  });

  it("returns ASR text and word timestamps", async () => {
    const run = vi.fn(async () => whisperResult());
    const form = new FormData();
    form.append("audio", new File([new Uint8Array([1, 2])], "speech.m4a", { type: "audio/mp4" }));
    form.append("language", "ru");
    form.append("search_mode", "true");
    const response = await route(new Request("https://worker.test/api/asr/transcribe", { method: "POST", body: form }), { AI: { run } }, allow);
    expect(response.status).toBe(200);
    const body = await response.json() as { transcript: string; duration_ms: number; words: Array<{ start_ms: number; end_ms: number }> };
    expect(body.transcript).toBe("Я люблю русский язык.");
    expect(body.duration_ms).toBe(2400);
    expect(body.words).toHaveLength(4);
    expect(body.words[3]).toMatchObject({ start_ms: 1600, end_ms: 2080 });
    expect(run).toHaveBeenCalledWith("@cf/openai/whisper-large-v3-turbo", expect.objectContaining({ audio: "AQI=", language: "ru", beam_size: 1, vad_filter: true }));
  });

  it("returns four clickable word clips and proxy scoring", async () => {
    const env: Env = { AI: { run: async () => whisperResult() } };
    const response = await route(multipart({ target_text: "Я люблю русский язык", difficulty: "beginner" }), env, allow);
    expect(response.status).toBe(200);
    const body = await response.json() as { score_available: boolean; words: Array<{ score: number | null; start_ms: number; end_ms: number }>; warnings: string[] };
    expect(body.score_available).toBe(true);
    expect(body.words).toHaveLength(4);
    expect(body.words.every(word => word.score !== null && word.end_ms > word.start_ms)).toBe(true);
    expect(body.warnings.join(" ")).toContain("可懂度代理评分");
  });

  it("does not prime ASR with the target sentence during pronunciation grading", async () => {
    const run = vi.fn(async () => whisperResult());
    await route(multipart({ target_text: "Я люблю русский язык" }), { AI: { run } }, allow);
    expect(run).toHaveBeenCalledWith("@cf/openai/whisper-large-v3-turbo", expect.not.objectContaining({ initial_prompt: expect.anything() }));
  });

  it("refuses to score English speech even when the ASR confidence is high", async () => {
    const env: Env = { AI: { run: async () => transcriptionResult("hello how are you", ["hello", "how", "are", "you"], "en") } };
    const response = await route(multipart({ target_text: "Я люблю русский язык" }), env, allow);
    const body = await response.json() as { score_available: boolean; overall_score: number | null; reason_code: string };
    expect(body.score_available).toBe(false);
    expect(body.overall_score).toBeNull();
    expect(body.reason_code).toBe("NON_RUSSIAN_SPEECH");
  });

  it("refuses an unrelated Russian sentence and a single coincidental word", async () => {
    const unrelated = await route(
      multipart({ target_text: "Я люблю русский язык" }),
      { AI: { run: async () => transcriptionResult("Сегодня хорошая погода дома", ["Сегодня", "хорошая", "погода", "дома"]) } }, allow,
    );
    const unrelatedBody = await unrelated.json() as { score_available: boolean; overall_score: number | null; reason_code: string };
    expect(unrelatedBody).toMatchObject({ score_available: false, overall_score: null, reason_code: "CONTENT_MISMATCH" });

    const partial = await route(
      multipart({ target_text: "Я люблю русский язык" }),
      { AI: { run: async () => transcriptionResult("Я сегодня читаю книгу", ["Я", "сегодня", "читаю", "книгу"]) } }, allow,
    );
    const partialBody = await partial.json() as { score_available: boolean; overall_score: number | null; reason_code: string };
    expect(partialBody).toMatchObject({ score_available: false, overall_score: null, reason_code: "CONTENT_MISMATCH" });
  });

  it("rejects missing AI binding, missing target, and oversized audio", async () => {
    expect((await route(multipart({ target_text: "Привет" }), {}, allow)).status).toBe(503);
    expect((await route(multipart(), { AI: { run: async () => whisperResult() } }, allow)).status).toBe(400);
    const form = new FormData();
    form.append("audio", new File([new Uint8Array(2049)], "large.m4a", { type: "audio/mp4" }));
    const response = await route(new Request("https://worker.test/api/asr/transcribe", { method: "POST", body: form }), { MAX_AUDIO_BYTES: "1024", AI: { run: async () => whisperResult() } }, allow);
    expect(response.status).toBe(413);
  });

  it("reliably scores single-word pronunciation and slight vowel variations", async () => {
    const exactEnv: Env = { AI: { run: async () => transcriptionResult("студент", ["студент"]) } };
    const exactResp = await route(multipart({ target_text: "студент", difficulty: "beginner" }), exactEnv, allow);
    expect(exactResp.status).toBe(200);
    const exactBody = await exactResp.json() as { score_available: boolean; overall_score: number; words: any[] };
    expect(exactBody.score_available).toBe(true);
    expect(exactBody.overall_score).toBeGreaterThanOrEqual(90);

    const closeEnv: Env = { AI: { run: async () => transcriptionResult("стадент", ["стадент"]) } };
    const closeResp = await route(multipart({ target_text: "студент", difficulty: "beginner" }), closeEnv, allow);
    expect(closeResp.status).toBe(200);
    const closeBody = await closeResp.json() as { score_available: boolean; overall_score: number; words: any[] };
    expect(closeBody.score_available).toBe(true);
    expect(closeBody.overall_score).toBeGreaterThanOrEqual(75);
    expect(closeBody.words[0].score).toBeGreaterThanOrEqual(75);
  });
});
