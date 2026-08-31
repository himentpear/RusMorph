import { describe, expect, it, vi } from "vitest";
import { route } from "../src/router";

const HASH_2024 = "6557739a67283a8de383fc5c0997fbec7c5721a46f28f3235fc9607598d9016b";

function statement(firstValue: unknown = null) {
  const value = {
    bind: vi.fn(() => value),
    first: vi.fn(async () => firstValue),
    run: vi.fn(async () => ({ meta: { changes: 1 } })),
    all: vi.fn(async () => ({ results: [] })),
  };
  return value;
}

function dbWithFirstValues(values: unknown[]): D1Database {
  let index = 0;
  return {
    prepare: vi.fn(() => statement(values[index++] ?? null)),
    batch: vi.fn(async () => []),
  } as unknown as D1Database;
}

describe("review calibration boundary", () => {
  it("requires a session for reviewer identity", async () => {
    const response = await route(new Request("https://worker.test/api/review/me"), {
      REVIEW_DB: dbWithFirstValues([]),
    });
    expect(response.status).toBe(401);
    expect(await response.json()).toMatchObject({ error: { code: "REVIEW_LOGIN_REQUIRED" } });
  });

  it("rejects an incorrect invite code", async () => {
    const response = await route(new Request("https://worker.test/api/review/login", {
      method: "POST",
      headers: { "content-type": "application/json", origin: "https://worker.test" },
      body: JSON.stringify({ displayName: "测试老师", inviteCode: "0000" }),
    }), { REVIEW_DB: dbWithFirstValues([]), REVIEW_INVITE_CODE_SHA256: HASH_2024 });
    expect(response.status).toBe(401);
    expect(await response.json()).toMatchObject({ error: { code: "INVALID_INVITE_CODE" } });
  });

  it("enforces the native login rate limit", async () => {
    const limit = vi.fn(async () => ({ success: false }));
    const response = await route(new Request("https://worker.test/api/review/login", {
      method: "POST",
      headers: { "content-type": "application/json", origin: "https://worker.test" },
      body: JSON.stringify({ displayName: "测试老师", inviteCode: "2024" }),
    }), {
      REVIEW_DB: dbWithFirstValues([]),
      REVIEW_INVITE_CODE_SHA256: HASH_2024,
      REVIEW_AUTH_RATE_LIMITER: { limit },
    });
    expect(response.status).toBe(429);
    expect(limit).toHaveBeenCalledOnce();
  });

  it("blocks cross-origin mutations before touching storage", async () => {
    const db = dbWithFirstValues([]);
    const response = await route(new Request("https://worker.test/api/review/login", {
      method: "POST",
      headers: { "content-type": "application/json", origin: "https://evil.test" },
      body: JSON.stringify({ displayName: "测试老师", inviteCode: "2024" }),
    }), { REVIEW_DB: db, REVIEW_INVITE_CODE_SHA256: HASH_2024 });
    expect(response.status).toBe(403);
    expect(vi.mocked(db.prepare)).not.toHaveBeenCalled();
  });

  it("reports missing review configuration and redirects the review root", async () => {
    const login = await route(new Request("https://worker.test/api/review/login", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ displayName: "测试老师", inviteCode: "2024" }),
    }), {});
    expect(login.status).toBe(503);
    expect(await login.json()).toMatchObject({ error: { code: "REVIEW_NOT_CONFIGURED" } });

    const redirect = await route(new Request("https://worker.test/review"), {});
    expect(redirect.status).toBe(308);
    expect(redirect.headers.get("location")).toBe("https://worker.test/review/");
  });

  it("reports review configuration in health without exposing the invite hash", async () => {
    const response = await route(new Request("https://worker.test/health"), {
      REVIEW_DB: dbWithFirstValues([]),
      REVIEW_AUDIO: {} as R2Bucket,
      REVIEW_INVITE_CODE_SHA256: HASH_2024,
      REVIEW_AUDIO_RETENTION_DAYS: "90",
    });
    const text = await response.text();
    expect(JSON.parse(text)).toMatchObject({ review: { configured: true, audio_retention_days: 90 } });
    expect(text).not.toContain(HASH_2024);
  });

  it("rejects incomplete per-word ratings before machine analysis", async () => {
    const reviewer = { id: "reviewer-1", display_name: "测试老师" };
    const task = {
      id: "task-1", created_by: "reviewer-1", target_text: "Привет мир", target_translation: "你好，世界",
      read_prompt_text: "Привет мир", read_prompt_translation: "你好，世界", difficulty: "beginner",
      topic: "问候", sample_mode: "correct", status: "open", created_at: new Date().toISOString(),
    };
    const db = dbWithFirstValues([reviewer, task]);
    const form = new FormData();
    form.set("task_id", "task-1");
    form.set("audio", new File([new Uint8Array([1, 2])], "sample.webm", { type: "audio/webm" }));
    form.set("consent", "true");
    form.set("content_match", "exact");
    form.set("overall_score", "90");
    form.set("word_ratings", JSON.stringify([{ index: 0, word: "Привет", rating: "correct" }]));
    const response = await route(new Request("https://worker.test/api/review/submissions", {
      method: "POST",
      headers: { origin: "https://worker.test", cookie: "__Host-rusmorph_review=test-token" },
      body: form,
    }), { REVIEW_DB: db, REVIEW_AUDIO: {} as R2Bucket });
    expect(response.status).toBe(400);
    expect(await response.json()).toMatchObject({ error: { code: "INCOMPLETE_WORD_RATINGS" } });
  });
});
