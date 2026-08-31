import { z } from "zod";
import { HttpError } from "./errors";
import type { Env, MultiAgentProvider } from "./providers/AgentProvider";
import { analyzePronunciation } from "./SpeechService";
import { selectTextbookLine } from "./TextbookDialogueCorpus";

const SESSION_COOKIE = "__Host-rusmorph_review";
const MAX_REVIEW_AUDIO_BYTES = 2_097_152;

const loginSchema = z.object({
  inviteCode: z.string().min(4).max(64),
  displayName: z.string().trim().min(2).max(40),
}).strict();

const difficultySchema = z.enum(["beginner", "intermediate", "advanced"]);
const sampleModeSchema = z.enum(["correct", "minor_error", "omission", "substitution", "off_target", "noise"]);

const generateTaskSchema = z.object({
  difficulty: difficultySchema,
  topic: z.string().trim().min(1).max(120).optional(),
  lessonNumber: z.number().int().min(1).max(18).nullable().optional(),
  sampleMode: sampleModeSchema,
}).strict();

const updateTaskSchema = z.object({
  targetText: z.string().trim().min(1).max(300),
  targetTranslation: z.string().trim().min(1).max(300),
  readPromptText: z.string().trim().min(1).max(300),
  readPromptTranslation: z.string().trim().min(1).max(300),
}).strict();

const wordRatingSchema = z.object({
  index: z.number().int().min(0).max(99),
  word: z.string().min(1).max(80),
  rating: z.enum(["correct", "acceptable", "wrong", "substituted", "omitted"]),
}).strict();

const wordRatingsSchema = z.array(wordRatingSchema).min(1).max(100);
const contentMatchSchema = z.enum(["exact", "mostly", "partial", "off_target", "unintelligible"]);

interface ReviewerRow {
  id: string;
  display_name: string;
}

interface TaskRow {
  id: string;
  created_by: string;
  target_text: string;
  target_translation: string;
  read_prompt_text: string;
  read_prompt_translation: string;
  difficulty: "beginner" | "intermediate" | "advanced";
  topic: string;
  sample_mode: z.infer<typeof sampleModeSchema>;
  lesson_number: number | null;
  status: string;
  created_at: string;
}

interface SubmissionRow {
  id: string;
  task_id: string;
  reviewer_id: string;
  reviewer_name: string;
  target_text: string;
  sample_mode: string;
  lesson_number: number | null;
  content_match: string;
  overall_score: number;
  actual_read_text: string | null;
  word_ratings_json: string;
  notes: string;
  machine_result_json: string;
  audio_content_type: string;
  audio_bytes: number;
  created_at: string;
}

export async function handleReviewRequest(
  request: Request,
  env: Env,
  _provider: MultiAgentProvider,
): Promise<Response> {
  const url = new URL(request.url);
  const path = url.pathname;

  if (path === "/api/review/login" && request.method === "POST") {
    return login(request, env);
  }
  if (path === "/api/review/logout" && request.method === "POST") {
    return logout(request, env);
  }
  if (path === "/api/review/me" && request.method === "GET") {
    return me(request, env);
  }
  if (path === "/api/review/tasks/generate" && request.method === "POST") {
    return generateTask(request, env);
  }
  if (path.startsWith("/api/review/tasks/") && request.method === "PATCH") {
    return updateTask(request, env, decodeURIComponent(path.slice("/api/review/tasks/".length)));
  }
  if (path === "/api/review/submissions" && request.method === "POST") {
    return submitReview(request, env);
  }
  if (path === "/api/review/submissions" && request.method === "GET") {
    return listSubmissions(request, env);
  }
  if (path.startsWith("/api/review/submissions/") && request.method === "DELETE") {
    return deleteSubmission(request, env, decodeURIComponent(path.slice("/api/review/submissions/".length)));
  }
  if (path === "/api/review/stats" && request.method === "GET") {
    return stats(request, env);
  }
  if (path.startsWith("/api/review/audio/") && request.method === "GET") {
    return audio(request, env, decodeURIComponent(path.slice("/api/review/audio/".length)));
  }
  throw new HttpError(404, "NOT_FOUND", "复核接口不存在。");
}

async function login(request: Request, env: Env): Promise<Response> {
  assertSameOrigin(request);
  const db = reviewDb(env);
  const input = loginSchema.parse(await readReviewJson(request));
  const ip = request.headers.get("cf-connecting-ip") ?? "unknown";
  if (env.REVIEW_AUTH_RATE_LIMITER) {
    const allowed = await env.REVIEW_AUTH_RATE_LIMITER.limit({ key: `review-login:${ip}` });
    if (!allowed.success) throw new HttpError(429, "RATE_LIMITED", "登录尝试过于频繁，请一分钟后再试。", true);
  }
  const expectedHash = env.REVIEW_INVITE_CODE_SHA256;
  if (!expectedHash || !await constantTimeHashMatch(input.inviteCode, expectedHash)) {
    throw new HttpError(401, "INVALID_INVITE_CODE", "邀请码无效。");
  }

  const now = new Date();
  const nowIso = now.toISOString();
  const normalizedName = input.displayName.normalize("NFKC").toLocaleLowerCase("zh-CN");
  let reviewer = await db.prepare(
    "SELECT id, display_name FROM reviewers WHERE normalized_name = ? AND active = 1",
  ).bind(normalizedName).first<ReviewerRow>();
  if (!reviewer) {
    reviewer = { id: crypto.randomUUID(), display_name: input.displayName };
    await db.prepare(
      "INSERT INTO reviewers (id, display_name, normalized_name, active, created_at, last_seen_at) VALUES (?, ?, ?, 1, ?, ?)",
    ).bind(reviewer.id, reviewer.display_name, normalizedName, nowIso, nowIso).run();
  } else {
    await db.prepare("UPDATE reviewers SET display_name = ?, last_seen_at = ? WHERE id = ?")
      .bind(input.displayName, nowIso, reviewer.id).run();
    reviewer.display_name = input.displayName;
  }

  const token = randomToken();
  const tokenHash = await sha256Hex(token);
  const sessionDays = boundedNumber(env.REVIEW_SESSION_DAYS, 7, 1, 30);
  const expires = new Date(now.getTime() + sessionDays * 86_400_000);
  await db.batch([
    db.prepare("DELETE FROM review_sessions WHERE expires_at <= ?").bind(nowIso),
    db.prepare("INSERT INTO review_sessions (token_hash, reviewer_id, created_at, expires_at) VALUES (?, ?, ?, ?)")
      .bind(tokenHash, reviewer.id, nowIso, expires.toISOString()),
  ]);

  const response = Response.json({ reviewer: publicReviewer(reviewer), expiresAt: expires.toISOString() });
  response.headers.append("set-cookie", sessionCookie(token, sessionDays * 86_400));
  response.headers.set("cache-control", "no-store");
  return response;
}

async function logout(request: Request, env: Env): Promise<Response> {
  assertSameOrigin(request);
  const token = cookieValue(request, SESSION_COOKIE);
  if (token && env.REVIEW_DB) {
    await env.REVIEW_DB.prepare("DELETE FROM review_sessions WHERE token_hash = ?").bind(await sha256Hex(token)).run();
  }
  const response = Response.json({ success: true });
  response.headers.append("set-cookie", `${SESSION_COOKIE}=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Strict`);
  response.headers.set("cache-control", "no-store");
  return response;
}

async function me(request: Request, env: Env): Promise<Response> {
  const reviewer = await requireReviewer(request, env);
  return noStoreJson({ reviewer: publicReviewer(reviewer) });
}

async function generateTask(request: Request, env: Env): Promise<Response> {
  assertSameOrigin(request);
  const reviewer = await requireReviewer(request, env);
  const db = reviewDb(env);
  const input = generateTaskSchema.parse(await readReviewJson(request));
  let selected: { lessonNumber: number; text: string };
  try { selected = selectTextbookLine(input.lessonNumber ?? undefined); }
  catch {
    throw new HttpError(400, "LESSON_HAS_NO_DIALOGUE", "第 8 课为复习课，文档中没有可用于朗读的对话。");
  }
  const target = {
    russian: selected.text,
    chinese: `《大学俄语1》对话 · 第 ${selected.lessonNumber} 课`,
  };

  let readPrompt = target;
  if (input.sampleMode === "off_target") {
    let alternate = selectTextbookLine();
    while (alternate.lessonNumber === selected.lessonNumber) alternate = selectTextbookLine();
    readPrompt = {
      russian: alternate.text,
      chinese: `偏离样本提示 · 第 ${alternate.lessonNumber} 课`,
    };
  }

  const id = crypto.randomUUID();
  const now = new Date().toISOString();
  await db.prepare(
    `INSERT INTO review_tasks (
      id, created_by, target_text, target_translation, read_prompt_text, read_prompt_translation,
      difficulty, topic, sample_mode, lesson_number, source_type, status, created_at, updated_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'textbook_dialogue', 'open', ?, ?)`,
  ).bind(
    id, reviewer.id, target.russian, target.chinese, readPrompt.russian, readPrompt.chinese,
    input.difficulty, `第 ${selected.lessonNumber} 课`, input.sampleMode, selected.lessonNumber, now, now,
  ).run();

  return noStoreJson({ task: {
    id,
    targetText: target.russian,
    targetTranslation: target.chinese,
    readPromptText: readPrompt.russian,
    readPromptTranslation: readPrompt.chinese,
    difficulty: input.difficulty,
    topic: `第 ${selected.lessonNumber} 课`,
    lessonNumber: selected.lessonNumber,
    sourceType: "textbook_dialogue",
    sampleMode: input.sampleMode,
    instruction: instructionFor(input.sampleMode),
    words: russianWords(target.russian),
  } }, 201);
}

async function updateTask(request: Request, env: Env, taskId: string): Promise<Response> {
  assertSameOrigin(request);
  const reviewer = await requireReviewer(request, env);
  const db = reviewDb(env);
  const input = updateTaskSchema.parse(await readReviewJson(request));
  const result = await db.prepare(
    `UPDATE review_tasks SET target_text = ?, target_translation = ?, read_prompt_text = ?,
      read_prompt_translation = ?, updated_at = ? WHERE id = ? AND created_by = ? AND status = 'open'`,
  ).bind(
    input.targetText, input.targetTranslation, input.readPromptText,
    input.readPromptTranslation, new Date().toISOString(), taskId, reviewer.id,
  ).run();
  if (!result.meta.changes) throw new HttpError(404, "TASK_NOT_FOUND", "任务不存在或已经提交。");
  return noStoreJson({ success: true, words: russianWords(input.targetText) });
}

async function submitReview(request: Request, env: Env): Promise<Response> {
  assertSameOrigin(request);
  const reviewer = await requireReviewer(request, env);
  const db = reviewDb(env);
  const bucket = reviewAudio(env);
  const declared = Number(request.headers.get("content-length") ?? 0);
  if (declared > MAX_REVIEW_AUDIO_BYTES + 131_072) {
    throw new HttpError(413, "AUDIO_TOO_LARGE", "复核录音不能超过 2 MB。");
  }
  let form: FormData;
  try { form = await request.formData(); }
  catch { throw new HttpError(400, "INVALID_MULTIPART", "无法读取录音表单。"); }

  const taskId = requiredFormText(form, "task_id", 100);
  const audioFile = form.get("audio");
  if (!(audioFile instanceof File) || audioFile.size === 0) throw new HttpError(400, "EMPTY_AUDIO", "请先完成录音。");
  if (audioFile.size > MAX_REVIEW_AUDIO_BYTES) throw new HttpError(413, "AUDIO_TOO_LARGE", "复核录音不能超过 2 MB。");
  const contentType = audioFile.type || "application/octet-stream";
  if (!contentType.startsWith("audio/") && contentType !== "application/octet-stream") {
    throw new HttpError(415, "UNSUPPORTED_AUDIO", "录音格式不受支持。");
  }
  if (requiredFormText(form, "consent", 10) !== "true") {
    throw new HttpError(400, "CONSENT_REQUIRED", "必须确认录音用于评分校准。");
  }

  const task = await db.prepare(
    "SELECT * FROM review_tasks WHERE id = ? AND created_by = ? AND status = 'open'",
  ).bind(taskId, reviewer.id).first<TaskRow>();
  if (!task) throw new HttpError(404, "TASK_NOT_FOUND", "任务不存在或已经提交。");

  const contentMatch = contentMatchSchema.parse(requiredFormText(form, "content_match", 30));
  const overallScore = Number(requiredFormText(form, "overall_score", 3));
  if (!Number.isInteger(overallScore) || overallScore < 0 || overallScore > 100) {
    throw new HttpError(400, "INVALID_SCORE", "人工评分必须是 0 到 100 的整数。");
  }
  let wordRatings: z.infer<typeof wordRatingsSchema>;
  try { wordRatings = wordRatingsSchema.parse(JSON.parse(requiredFormText(form, "word_ratings", 20_000))); }
  catch { throw new HttpError(400, "INVALID_WORD_RATINGS", "逐词标注格式无效。"); }
  validateWordRatings(wordRatings, russianWords(task.target_text));

  const analysisForm = new FormData();
  analysisForm.set("audio", audioFile, audioFile.name || "review-audio");
  analysisForm.set("target_text", task.target_text);
  analysisForm.set("difficulty", task.difficulty);
  const machineResult = await analyzePronunciation(
    new Request("https://review.internal/api/pronunciation/analyze", { method: "POST", body: analysisForm }),
    env,
  );

  const submissionId = crypto.randomUUID();
  const datePrefix = new Date().toISOString().slice(0, 10);
  const objectKey = `review-audio/${datePrefix}/${submissionId}.${extensionFor(contentType)}`;
  const audioBytes = await audioFile.arrayBuffer();
  await bucket.put(objectKey, audioBytes, {
    httpMetadata: { contentType },
    customMetadata: { reviewerId: reviewer.id, taskId, submissionId },
  });

  const now = new Date().toISOString();
  const actualReadText = optionalFormText(form, "actual_read_text", 300);
  const notes = optionalFormText(form, "notes", 1_000) ?? "";
  try {
    await db.batch([
      db.prepare(
        `INSERT INTO review_submissions (
          id, task_id, reviewer_id, audio_key, audio_content_type, audio_bytes,
          content_match, overall_score, actual_read_text, word_ratings_json, notes,
          machine_result_json, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      ).bind(
        submissionId, taskId, reviewer.id, objectKey, contentType, audioFile.size,
        contentMatch, overallScore, actualReadText, JSON.stringify(wordRatings), notes,
        JSON.stringify(machineResult), now,
      ),
      db.prepare("UPDATE review_tasks SET status = 'submitted', updated_at = ? WHERE id = ?")
        .bind(now, taskId),
    ]);
  } catch (error) {
    await bucket.delete(objectKey);
    throw error;
  }

  return noStoreJson({
    success: true,
    submissionId,
    machineResult,
    humanScore: overallScore,
    scoreDelta: typeof machineResult.overall_score === "number"
      ? Math.round((machineResult.overall_score - overallScore) * 100) / 100
      : null,
  }, 201);
}

async function listSubmissions(request: Request, env: Env): Promise<Response> {
  await requireReviewer(request, env);
  const db = reviewDb(env);
  const result = await db.prepare(
    `SELECT s.id, s.task_id, s.reviewer_id, r.display_name AS reviewer_name,
      t.target_text, t.sample_mode, t.lesson_number, s.content_match, s.overall_score,
      s.actual_read_text, s.word_ratings_json, s.notes, s.machine_result_json,
      s.audio_content_type, s.audio_bytes, s.created_at
     FROM review_submissions s
     JOIN reviewers r ON r.id = s.reviewer_id
     JOIN review_tasks t ON t.id = s.task_id
     ORDER BY s.created_at DESC LIMIT 50`,
  ).all<SubmissionRow>();
  return noStoreJson({ submissions: result.results.map(row => ({
    id: row.id,
    taskId: row.task_id,
    reviewer: { id: row.reviewer_id, displayName: row.reviewer_name },
    targetText: row.target_text,
    sampleMode: row.sample_mode,
    lessonNumber: row.lesson_number,
    contentMatch: row.content_match,
    overallScore: row.overall_score,
    actualReadText: row.actual_read_text,
    wordRatings: safeJson(row.word_ratings_json, []),
    notes: row.notes,
    machineResult: safeJson(row.machine_result_json, {}),
    audioUrl: `/api/review/audio/${encodeURIComponent(row.id)}`,
    audioContentType: row.audio_content_type,
    audioBytes: row.audio_bytes,
    createdAt: row.created_at,
  })) });
}

async function deleteSubmission(request: Request, env: Env, submissionId: string): Promise<Response> {
  assertSameOrigin(request);
  const reviewer = await requireReviewer(request, env);
  const db = reviewDb(env);
  const bucket = reviewAudio(env);
  const submission = await db.prepare(
    "SELECT audio_key, task_id FROM review_submissions WHERE id = ? AND reviewer_id = ?",
  ).bind(submissionId, reviewer.id).first<{ audio_key: string; task_id: string }>();
  if (!submission) throw new HttpError(404, "SUBMISSION_NOT_FOUND", "样本不存在或不属于当前复核者。");

  // Delete the private recording first. If the following D1 operation fails,
  // a retry remains safe and no personal audio is left behind.
  await bucket.delete(submission.audio_key);
  await db.batch([
    db.prepare("DELETE FROM review_submissions WHERE id = ? AND reviewer_id = ?")
      .bind(submissionId, reviewer.id),
    db.prepare("UPDATE review_tasks SET status = 'open', updated_at = ? WHERE id = ?")
      .bind(new Date().toISOString(), submission.task_id),
  ]);
  return noStoreJson({ success: true });
}

async function stats(request: Request, env: Env): Promise<Response> {
  await requireReviewer(request, env);
  const db = reviewDb(env);
  const totals = await db.prepare(
    `SELECT COUNT(*) AS samples,
      COUNT(DISTINCT reviewer_id) AS reviewers,
      AVG(overall_score) AS average_human_score
     FROM review_submissions`,
  ).first<{ samples: number; reviewers: number; average_human_score: number | null }>();
  const modes = await db.prepare(
    `SELECT t.sample_mode AS mode, COUNT(*) AS count
     FROM review_submissions s JOIN review_tasks t ON t.id = s.task_id
     GROUP BY t.sample_mode ORDER BY count DESC`,
  ).all<{ mode: string; count: number }>();
  return noStoreJson({
    samples: Number(totals?.samples ?? 0),
    targetSamples: 500,
    reviewers: Number(totals?.reviewers ?? 0),
    averageHumanScore: totals?.average_human_score == null ? null : Math.round(Number(totals.average_human_score) * 10) / 10,
    modes: Object.fromEntries(modes.results.map(item => [item.mode, Number(item.count)])),
  });
}

async function audio(request: Request, env: Env, submissionId: string): Promise<Response> {
  await requireReviewer(request, env);
  const db = reviewDb(env);
  const bucket = reviewAudio(env);
  const metadata = await db.prepare(
    "SELECT audio_key, audio_content_type, audio_bytes FROM review_submissions WHERE id = ?",
  ).bind(submissionId).first<{ audio_key: string; audio_content_type: string; audio_bytes: number }>();
  if (!metadata) throw new HttpError(404, "AUDIO_NOT_FOUND", "录音不存在。");

  const range = parseRange(request.headers.get("range"), metadata.audio_bytes);
  const object = await bucket.get(metadata.audio_key, range ? { range: { offset: range.start, length: range.length } } : undefined);
  if (!object?.body) throw new HttpError(404, "AUDIO_NOT_FOUND", "录音不存在或已过期删除。");
  const headers = new Headers({
    "content-type": metadata.audio_content_type,
    "cache-control": "private, no-store",
    "accept-ranges": "bytes",
    "content-length": String(range?.length ?? metadata.audio_bytes),
    "x-content-type-options": "nosniff",
  });
  headers.set("etag", object.httpEtag);
  if (range) headers.set("content-range", `bytes ${range.start}-${range.end}/${metadata.audio_bytes}`);
  return new Response(object.body, { status: range ? 206 : 200, headers });
}

async function requireReviewer(request: Request, env: Env): Promise<ReviewerRow> {
  const token = cookieValue(request, SESSION_COOKIE);
  if (!token) throw new HttpError(401, "REVIEW_LOGIN_REQUIRED", "请先登录复核通道。");
  const reviewer = await reviewDb(env).prepare(
    `SELECT r.id, r.display_name
     FROM review_sessions s JOIN reviewers r ON r.id = s.reviewer_id
     WHERE s.token_hash = ? AND s.expires_at > ? AND r.active = 1`,
  ).bind(await sha256Hex(token), new Date().toISOString()).first<ReviewerRow>();
  if (!reviewer) throw new HttpError(401, "REVIEW_SESSION_EXPIRED", "复核会话已过期，请重新登录。");
  return reviewer;
}

function reviewDb(env: Env): D1Database {
  if (!env.REVIEW_DB) throw new HttpError(503, "REVIEW_NOT_CONFIGURED", "复核数据库尚未配置。");
  return env.REVIEW_DB;
}

function reviewAudio(env: Env): R2Bucket {
  if (!env.REVIEW_AUDIO) throw new HttpError(503, "REVIEW_NOT_CONFIGURED", "复核录音存储尚未配置。");
  return env.REVIEW_AUDIO;
}

function assertSameOrigin(request: Request): void {
  const origin = request.headers.get("origin");
  if (origin && origin !== new URL(request.url).origin) {
    throw new HttpError(403, "CROSS_ORIGIN_REJECTED", "跨站复核请求已被拒绝。");
  }
}

async function readReviewJson(request: Request): Promise<unknown> {
  if (!request.headers.get("content-type")?.toLowerCase().startsWith("application/json")) {
    throw new HttpError(400, "INVALID_REQUEST", "Content-Type 必须是 application/json。");
  }
  const declared = Number(request.headers.get("content-length") ?? 0);
  if (declared > 32_768) throw new HttpError(413, "PAYLOAD_TOO_LARGE", "请求内容过大。");
  const text = await request.text();
  if (new TextEncoder().encode(text).byteLength > 32_768) throw new HttpError(413, "PAYLOAD_TOO_LARGE", "请求内容过大。");
  try { return JSON.parse(text); }
  catch { throw new HttpError(400, "INVALID_REQUEST", "JSON 格式无效。"); }
}

function validateWordRatings(ratings: z.infer<typeof wordRatingsSchema>, targetWords: string[]): void {
  if (ratings.length !== targetWords.length) throw new HttpError(400, "INCOMPLETE_WORD_RATINGS", "请标注每一个目标单词。");
  const indexes = new Set(ratings.map(item => item.index));
  if (indexes.size !== targetWords.length || targetWords.some((word, index) => !indexes.has(index) || ratings.find(item => item.index === index)?.word !== word)) {
    throw new HttpError(400, "INVALID_WORD_RATINGS", "逐词标注与目标句不一致，请刷新后重试。");
  }
}

function instructionFor(mode: z.infer<typeof sampleModeSchema>): string {
  return {
    correct: "自然、清晰地朗读显示句子，作为正确参考样本。",
    minor_error: "朗读显示句子，但故意制造一个轻微发音或词尾错误，并在逐词标注中指出。",
    omission: "朗读时故意漏掉一个目标词，并把该词标记为“漏读”。",
    substitution: "朗读时故意把一个目标词换成别的词，并标记为“读成其他词”。",
    off_target: "不要朗读目标句；请朗读下方专门提供的无关句子，用于测试系统拒绝错误内容。",
    noise: "朗读目标句，但加入可控背景噪声或降低音量，用于测试低置信度分支。",
  }[mode];
}

function russianWords(value: string): string[] {
  return value.match(/[А-Яа-яЁё\u0301-]+/g) ?? [];
}

function requiredFormText(form: FormData, name: string, maxLength: number): string {
  const value = form.get(name);
  if (typeof value !== "string" || !value.trim() || value.length > maxLength) {
    throw new HttpError(400, "INVALID_REQUEST", `${name} 字段无效。`);
  }
  return value.trim();
}

function optionalFormText(form: FormData, name: string, maxLength: number): string | null {
  const value = form.get(name);
  if (value == null || value === "") return null;
  if (typeof value !== "string" || value.length > maxLength) throw new HttpError(400, "INVALID_REQUEST", `${name} 字段无效。`);
  return value.trim() || null;
}

function publicReviewer(row: ReviewerRow) {
  return { id: row.id, displayName: row.display_name };
}

function noStoreJson(value: unknown, status = 200): Response {
  return Response.json(value, { status, headers: { "cache-control": "no-store", "x-content-type-options": "nosniff" } });
}

function sessionCookie(token: string, maxAge: number): string {
  return `${SESSION_COOKIE}=${token}; Path=/; Max-Age=${maxAge}; HttpOnly; Secure; SameSite=Strict`;
}

function cookieValue(request: Request, name: string): string | null {
  const cookie = request.headers.get("cookie") ?? "";
  for (const part of cookie.split(";")) {
    const index = part.indexOf("=");
    if (index < 0) continue;
    if (part.slice(0, index).trim() === name) return part.slice(index + 1).trim();
  }
  return null;
}

function randomToken(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return base64Url(bytes);
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function constantTimeHashMatch(value: string, expectedHex: string): Promise<boolean> {
  if (!/^[a-f0-9]{64}$/i.test(expectedHex)) return false;
  const actual = hexBytes(await sha256Hex(value));
  const expected = hexBytes(expectedHex.toLowerCase());
  // Both values are fixed-length SHA-256 digests. The runtime types do not yet
  // expose timingSafeEqual, so compare every byte without early return.
  let difference = 0;
  for (let index = 0; index < actual.length; index += 1) difference |= actual[index] ^ expected[index];
  return difference === 0;
}

async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
}

function hexBytes(value: string): Uint8Array {
  const bytes = new Uint8Array(value.length / 2);
  for (let index = 0; index < bytes.length; index += 1) bytes[index] = Number.parseInt(value.slice(index * 2, index * 2 + 2), 16);
  return bytes;
}

function boundedNumber(raw: string | undefined, fallback: number, min: number, max: number): number {
  const value = Number(raw ?? fallback);
  return Number.isFinite(value) ? Math.min(max, Math.max(min, value)) : fallback;
}

function extensionFor(contentType: string): string {
  if (contentType.includes("mp4") || contentType.includes("m4a")) return "m4a";
  if (contentType.includes("ogg")) return "ogg";
  if (contentType.includes("wav")) return "wav";
  return "webm";
}

function safeJson<T>(raw: string, fallback: T): T {
  try { return JSON.parse(raw) as T; }
  catch { return fallback; }
}

function parseRange(raw: string | null, size: number): { start: number; end: number; length: number } | null {
  if (!raw) return null;
  const match = /^bytes=(\d+)-(\d*)$/.exec(raw.trim());
  if (!match) throw new HttpError(416, "INVALID_RANGE", "录音范围请求无效。");
  const start = Number(match[1]);
  const end = match[2] ? Math.min(Number(match[2]), size - 1) : size - 1;
  if (!Number.isInteger(start) || !Number.isInteger(end) || start < 0 || end < start || start >= size) {
    throw new HttpError(416, "INVALID_RANGE", "录音范围请求超出文件大小。");
  }
  return { start, end, length: end - start + 1 };
}
