import { HttpError } from "./errors";
import type { Env } from "./providers/AgentProvider";

interface WhisperWord { word?: unknown; start?: unknown; end?: unknown }
interface WhisperSegment {
  start?: unknown;
  end?: unknown;
  text?: unknown;
  avg_logprob?: unknown;
  no_speech_prob?: unknown;
  words?: unknown;
}
interface WhisperResult {
  text?: unknown;
  transcription_info?: {
    language?: unknown;
    language_probability?: unknown;
    duration?: unknown;
    duration_after_vad?: unknown;
  };
  segments?: unknown;
}

interface TimedWord {
  text: string;
  normalized: string;
  startMs: number;
  endMs: number;
  confidence: number;
}

interface Alignment {
  mapping: Map<number, { actualIndex: number; exact: boolean }>;
  omitted: string[];
  extra: string[];
  substitutions: Array<{ target: string; recognized: string }>;
  exactCount: number;
}

export async function transcribeSpeech(request: Request, env: Env): Promise<Record<string, unknown>> {
  const form = await readSpeechForm(request, env);
  const result = await runWhisper(form.audio, env);
  const parsed = parseWhisper(result);
  const language = stringValue(result.transcription_info?.language) || "ru";
  const languageConfidence = probability(result.transcription_info?.language_probability, 0);
  const warnings: string[] = [];
  if (language !== "ru" && languageConfidence >= 0.65) warnings.push("识别语言可能不是俄语");
  if (parsed.confidence < 0.55) warnings.push("识别置信度较低，请确认文本或重新录音");
  return {
    success: true,
    language,
    language_confidence: languageConfidence,
    transcript: parsed.text,
    normalized_transcript: normalizeText(parsed.text),
    confidence: parsed.confidence,
    should_auto_search: form.searchMode && language === "ru" && parsed.confidence >= 0.72,
    duration_ms: secondsToMs(numberValue(result.transcription_info?.duration)),
    speech_duration_ms: secondsToMs(numberValue(result.transcription_info?.duration_after_vad)),
    words: parsed.words.map(publicWord),
    alternatives: [],
    warnings,
  };
}

export async function analyzePronunciation(request: Request, env: Env): Promise<Record<string, unknown>> {
  const form = await readSpeechForm(request, env, true);
  const targetText = requiredText(form.data, "target_text", 300);
  const difficulty = optionalText(form.data, "difficulty") || "beginner";
  if (!["beginner", "intermediate", "advanced"].includes(difficulty)) {
    throw new HttpError(400, "INVALID_DIFFICULTY", "difficulty 必须是 beginner、intermediate 或 advanced");
  }
  const targetWords = russianWords(targetText);
  if (targetWords.length === 0) throw new HttpError(400, "INVALID_TARGET_TEXT", "目标文本必须包含俄语单词");

  // Do not feed the sentence being graded back to ASR.  A full initial prompt
  // can make Whisper "recognise" the expected sentence even when the learner
  // said something unrelated, which defeats content validation.
  const result = await runWhisper(form.audio, env);
  const parsed = parseWhisper(result);
  const alignment = alignWords(targetWords, parsed.words);
  const mappedCount = alignment.mapping.size;
  const substitutionCount = alignment.substitutions.length;
  const exactCoverage = alignment.exactCount / targetWords.length;
  const completeness = round2(100 * mappedCount / targetWords.length);
  const textMatch = round2(100 * (alignment.exactCount + substitutionCount * 0.55) / Math.max(targetWords.length, parsed.words.length, 1));
  const durationMs = secondsToMs(numberValue(result.transcription_info?.duration));
  const speechDurationMs = secondsToMs(numberValue(result.transcription_info?.duration_after_vad)) || durationMs;
  const detectedLanguage = stringValue(result.transcription_info?.language).toLowerCase();
  const languageConfidence = probability(result.transcription_info?.language_probability, 0);
  const transcriptEvidence = parsed.text || parsed.words.map(word => word.text).join(" ");
  const cyrillicRatio = cyrillicLetterRatio(transcriptEvidence);
  const nonRussianSpeech = detectedLanguage !== "" && detectedLanguage !== "ru" && languageConfidence >= 0.65;
  const nonRussianTranscript = transcriptEvidence.length > 0 && cyrillicRatio < 0.45;

  const words = targetWords.map((text, targetIndex) => {
    const match = alignment.mapping.get(targetIndex);
    if (!match) return {
      text, start_ms: 0, end_ms: 0, score: null, confidence: 0,
      status: "unreliable", feedback_zh: "该词未被可靠识别，请放慢后单独练习。",
    };
    const actual = parsed.words[match.actualIndex];
    const durationFactor = expectedDurationFactor(actual.endMs - actual.startMs);
    // A substituted word proves that there was speech at this timestamp, but
    // is weak evidence of a correct pronunciation of the target word.
    const matchFactor = match.exact ? 1 : 0.2;
    const confidence = round4(actual.confidence * matchFactor);
    const score = round2(100 * (0.52 * matchFactor + 0.35 * actual.confidence + 0.13 * durationFactor));
    return {
      text,
      start_ms: actual.startMs,
      end_ms: actual.endMs,
      score,
      confidence,
      status: statusFor(score, confidence),
      feedback_zh: feedbackFor(score, confidence),
    };
  });

  const scored = words.filter((word): word is typeof word & { score: number } => typeof word.score === "number");
  const pronunciation = scored.length ? average(scored.map(word => word.score)) : 0;
  const wpm = speechDurationMs > 0 ? targetWords.length * 60_000 / speechDurationMs : 0;
  const fluency = fluencyScore(wpm, difficulty, parsed.words);
  const analysisConfidence = round4(parsed.confidence * exactCoverage);
  const minimumExactWords = Math.max(1, Math.ceil(targetWords.length * 0.55));
  const hasContentEvidence = alignment.exactCount >= minimumExactWords && textMatch >= 55;
  const scoreAvailable = !nonRussianSpeech && !nonRussianTranscript && hasContentEvidence && analysisConfidence >= 0.35;
  const rawOverall = round2(pronunciation * 0.55 + fluency * 0.20 + completeness * 0.25);
  // A partially matched sentence must not receive a score that looks like a
  // pass merely because its few aligned words have high ASR confidence.
  const overall = round2(Math.min(rawOverall, contentScoreCeiling(exactCoverage, textMatch)));
  const reasonCode = scoreAvailable ? null
    : nonRussianSpeech || nonRussianTranscript ? "NON_RUSSIAN_SPEECH"
      : hasContentEvidence ? "LOW_ALIGNMENT_EVIDENCE" : "CONTENT_MISMATCH";
  const reason = scoreAvailable ? null
    : reasonCode === "NON_RUSSIAN_SPEECH" ? "检测到的内容不像俄语朗读，无法生成发音分数"
      : reasonCode === "CONTENT_MISMATCH" ? "朗读内容与目标句不一致，无法生成可靠评分"
        : "识别或时间戳证据不足，无法可靠评分";
  const warnings = [
    "本结果是基于识别文本和单词时间戳的可懂度代理评分，不是 MFA/GOP 音素级评分。",
  ];
  if (parsed.confidence < 0.55) warnings.push("识别置信度较低，请在安静环境重新录音。");

  return {
    success: true,
    target_text: targetText,
    target_stressed_text: targetText,
    recognized_text: parsed.text,
    text_match_score: textMatch,
    score_available: scoreAvailable,
    reason,
    reason_code: reasonCode,
    overall_score: scoreAvailable ? overall : null,
    pronunciation_accuracy: scoreAvailable ? pronunciation : null,
    stress_accuracy: null,
    fluency_score: scoreAvailable ? fluency : null,
    completeness_score: scoreAvailable ? completeness : null,
    proficiency_level: scoreAvailable ? proficiency(overall) : null,
    asr_confidence: parsed.confidence,
    text_match_confidence: round4(parsed.confidence * textMatch / 100),
    alignment_confidence: analysisConfidence,
    stress_confidence: 0,
    phoneme_score_confidence: 0,
    analysis_confidence: analysisConfidence,
    duration_ms: durationMs,
    speech_rate_words_per_minute: round2(wpm),
    words,
    omitted_words: alignment.omitted,
    extra_words: alignment.extra,
    substituted_words: alignment.substitutions,
    top_issues: alignment.omitted.map(word => ({ type: "omission", word, severity: "high", feedback_zh: "该词未被识别。" })),
    summary_feedback_zh: scoreAvailable
      ? "颜色表示各词的识别可懂度；点击单词可回放对应录音片段。"
      : reason,
    next_practice: [],
    warnings,
  };
}

async function readSpeechForm(request: Request, env: Env, requireTarget = false): Promise<{ audio: File; data: FormData; searchMode: boolean }> {
  const contentType = request.headers.get("content-type")?.toLowerCase() ?? "";
  if (!contentType.startsWith("multipart/form-data")) throw new HttpError(400, "INVALID_REQUEST", "Content-Type must be multipart/form-data");
  const maxBytes = boundedNumber(env.MAX_AUDIO_BYTES, 2_097_152, 1_024, 10_485_760);
  const declared = Number(request.headers.get("content-length") ?? 0);
  if (declared > maxBytes + 65_536) throw new HttpError(413, "AUDIO_TOO_LARGE", `音频文件过大，最大允许 ${formatMegabytes(maxBytes)} MB`);
  let data: FormData;
  try { data = await request.formData(); }
  catch { throw new HttpError(400, "INVALID_MULTIPART", "无法解析上传的音频"); }
  const audio = data.get("audio");
  if (!(audio instanceof File) || audio.size === 0) throw new HttpError(400, "EMPTY_AUDIO", "请选择非空音频文件");
  if (audio.size > maxBytes) throw new HttpError(413, "AUDIO_TOO_LARGE", `音频文件过大，最大允许 ${formatMegabytes(maxBytes)} MB`);
  if (requireTarget) requiredText(data, "target_text", 300);
  return { audio, data, searchMode: optionalText(data, "search_mode") !== "false" };
}

async function runWhisper(audio: File, env: Env): Promise<WhisperResult> {
  if (!env.AI) throw new HttpError(503, "SPEECH_NOT_CONFIGURED", "云端语音识别尚未配置");
  const model = env.SPEECH_ASR_MODEL ?? "@cf/openai/whisper-large-v3-turbo";
  const timeoutMs = boundedNumber(env.SPEECH_TIMEOUT_MS, 19_000, 5_000, 19_500);
  const input: Record<string, unknown> = {
    // Cloudflare's current Whisper guide uses base64 for binding calls. The
    // schema also advertises a body/contentType form, but that form is rejected
    // by the production binding for multipart File ArrayBuffers.
    audio: arrayBufferToBase64(await audio.arrayBuffer()),
    task: "transcribe",
    language: "ru",
    vad_filter: true,
    beam_size: 1,
    condition_on_previous_text: false,
    no_speech_threshold: 0.6,
  };
  const startedAt = Date.now();
  try {
    const raw = await withTimeout(env.AI.run(model, input), timeoutMs);
    if (!raw || typeof raw !== "object") throw new HttpError(502, "INVALID_ASR_RESPONSE", "云端语音识别返回格式异常", true);
    console.info(JSON.stringify({ event: "speech_asr_succeeded", provider: "workers_ai", model, latencyMs: Date.now() - startedAt, audioBytes: audio.size }));
    return raw as WhisperResult;
  } catch (error) {
    if (error instanceof HttpError) throw error;
    if (error instanceof Error && error.name === "SpeechTimeoutError") throw new HttpError(504, "SPEECH_TIMEOUT", "语音识别超过 20 秒，请缩短录音后重试", true);
    console.error(JSON.stringify({ event: "speech_asr_failed", provider: "workers_ai", model, latencyMs: Date.now() - startedAt, errorName: error instanceof Error ? error.name : "UnknownError" }));
    throw new HttpError(502, "SPEECH_PROVIDER_ERROR", "云端语音识别暂时不可用", true);
  }
}

function parseWhisper(result: WhisperResult): { text: string; confidence: number; words: TimedWord[] } {
  const segments = Array.isArray(result.segments) ? result.segments.filter(isObject) as WhisperSegment[] : [];
  const words: TimedWord[] = [];
  const confidences: number[] = [];
  for (const segment of segments) {
    const confidence = segmentConfidence(segment);
    confidences.push(confidence);
    if (!Array.isArray(segment.words)) continue;
    for (const rawWord of segment.words) {
      if (!isObject(rawWord)) continue;
      const word = rawWord as WhisperWord;
      const text = stringValue(word.word).trim();
      const normalized = normalizeWord(text);
      const startMs = secondsToMs(numberValue(word.start));
      const endMs = secondsToMs(numberValue(word.end));
      if (!normalized || endMs <= startMs) continue;
      words.push({ text, normalized, startMs, endMs, confidence });
    }
  }
  const text = stringValue(result.text).trim();
  return { text, confidence: round4(confidences.length ? average(confidences) : (text ? 0.5 : 0)), words };
}

function alignWords(targetWords: string[], actualWords: TimedWord[]): Alignment {
  const target = targetWords.map(normalizeWord);
  const actual = actualWords.map(word => word.normalized);
  const rows = target.length + 1;
  const cols = actual.length + 1;
  const cost = Array.from({ length: rows }, () => Array<number>(cols).fill(0));
  const op = Array.from({ length: rows }, () => Array<"match" | "replace" | "delete" | "insert">(cols).fill("match"));
  for (let i = 1; i < rows; i++) { cost[i][0] = i; op[i][0] = "delete"; }
  for (let j = 1; j < cols; j++) { cost[0][j] = j; op[0][j] = "insert"; }
  for (let i = 1; i < rows; i++) for (let j = 1; j < cols; j++) {
    const exact = target[i - 1] === actual[j - 1];
    const candidates: Array<[number, "match" | "replace" | "delete" | "insert"]> = [
      [cost[i - 1][j - 1] + (exact ? 0 : 1), exact ? "match" : "replace"],
      [cost[i - 1][j] + 1, "delete"],
      [cost[i][j - 1] + 1, "insert"],
    ];
    candidates.sort((a, b) => a[0] - b[0]);
    [cost[i][j], op[i][j]] = candidates[0];
  }
  const mapping = new Map<number, { actualIndex: number; exact: boolean }>();
  const omitted: string[] = [];
  const extra: string[] = [];
  const substitutions: Array<{ target: string; recognized: string }> = [];
  let exactCount = 0;
  let i = target.length;
  let j = actual.length;
  while (i > 0 || j > 0) {
    const action = op[i][j];
    if (i > 0 && j > 0 && (action === "match" || action === "replace")) {
      const exact = action === "match";
      mapping.set(i - 1, { actualIndex: j - 1, exact });
      if (exact) exactCount += 1;
      else substitutions.push({ target: targetWords[i - 1], recognized: actualWords[j - 1].text.trim() });
      i -= 1; j -= 1;
    } else if (i > 0 && action === "delete") { omitted.push(targetWords[i - 1]); i -= 1; }
    else if (j > 0) { extra.push(actualWords[j - 1].text.trim()); j -= 1; }
    else break;
  }
  return { mapping, omitted: omitted.reverse(), extra: extra.reverse(), substitutions: substitutions.reverse(), exactCount };
}

function russianWords(text: string): string[] { return text.match(/[А-Яа-яЁё]+(?:-[А-Яа-яЁё]+)*/gu) ?? []; }
function cyrillicLetterRatio(text: string): number {
  const letters = text.match(/[\p{L}]/gu) ?? [];
  if (letters.length === 0) return 0;
  const cyrillic = letters.filter(letter => /[А-Яа-яЁё]/u.test(letter)).length;
  return cyrillic / letters.length;
}
function contentScoreCeiling(exactCoverage: number, textMatch: number): number {
  if (textMatch < 55 || exactCoverage < 0.55) return 0;
  // At the acceptance boundary the highest possible result is 78. It only
  // becomes possible to reach 90+ when nearly all target words are exact.
  return Math.min(100, 78 + Math.max(0, exactCoverage - 0.55) * (22 / 0.45));
}
function normalizeWord(text: string): string { return text.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLocaleLowerCase("ru-RU").replace(/^[^а-яё]+|[^а-яё-]+$/giu, ""); }
function normalizeText(text: string): string { return text.normalize("NFC").trim().toLocaleLowerCase("ru-RU").replace(/\s+/g, " ").replace(/[.!?,;:]+$/u, ""); }
function publicWord(word: TimedWord) { return { text: word.text.trim(), start_ms: word.startMs, end_ms: word.endMs, confidence: word.confidence }; }
function segmentConfidence(segment: WhisperSegment): number {
  const logprob = numberValue(segment.avg_logprob);
  const speech = 1 - probability(segment.no_speech_prob, 0);
  return round4(Math.max(0, Math.min(1, Math.exp(Math.min(0, logprob)) * speech)));
}
function expectedDurationFactor(durationMs: number): number { return Math.max(0.25, Math.min(1, durationMs / 140, 900 / Math.max(durationMs, 1))); }
function statusFor(score: number, confidence: number): string { if (confidence < 0.45) return "unreliable"; if (score >= 90) return "excellent"; if (score >= 75) return "good"; if (score >= 60) return "acceptable"; return "needs_work"; }
function feedbackFor(score: number, confidence: number): string { if (confidence < 0.45) return "识别证据较弱，建议重新录音。"; if (score >= 90) return "朗读清晰，继续保持。"; if (score >= 75) return "整体清楚，可继续优化节奏。"; if (score >= 60) return "基本可懂，建议点击回放后放慢练习。"; return "识别差异较大，请点击回放并单独练习。"; }
function fluencyScore(wpm: number, difficulty: string, words: TimedWord[]): number {
  const ideal = difficulty === "advanced" ? 125 : difficulty === "intermediate" ? 110 : 90;
  const rate = Math.max(35, 100 - Math.abs(wpm - ideal) * 0.9);
  const gaps = words.slice(1).map((word, index) => Math.max(0, word.startMs - words[index].endMs));
  const pause = gaps.length ? Math.max(35, 100 - average(gaps) / 12) : 85;
  return round2(rate * 0.65 + pause * 0.35);
}
function proficiency(score: number): string { if (score >= 90) return "熟练"; if (score >= 80) return "较熟练"; if (score >= 70) return "基本熟练"; if (score >= 60) return "基本可懂"; if (score >= 40) return "需要加强"; return "建议重新练习"; }
function requiredText(form: FormData, key: string, maxLength: number): string { const value = optionalText(form, key); if (!value) throw new HttpError(400, "INVALID_REQUEST", `${key} 不能为空`); if (value.length > maxLength) throw new HttpError(400, "INVALID_REQUEST", `${key} 过长`); return value; }
function optionalText(form: FormData, key: string): string { const value = form.get(key); return typeof value === "string" ? value.trim() : ""; }
function boundedNumber(raw: string | undefined, fallback: number, min: number, max: number): number { const value = Number(raw ?? fallback); return Number.isFinite(value) ? Math.min(max, Math.max(min, value)) : fallback; }
function numberValue(value: unknown): number { return typeof value === "number" && Number.isFinite(value) ? value : 0; }
function stringValue(value: unknown): string { return typeof value === "string" ? value : ""; }
function formatMegabytes(bytes: number): string { return (bytes / 1_048_576).toFixed(bytes % 1_048_576 === 0 ? 0 : 1); }
function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  // Keep every non-final chunk divisible by three so concatenated base64 is valid.
  const chunkSize = 32_766;
  let encoded = "";
  for (let offset = 0; offset < bytes.length; offset += chunkSize) {
    const chunk = bytes.subarray(offset, Math.min(offset + chunkSize, bytes.length));
    let binary = "";
    for (let index = 0; index < chunk.length; index += 1) binary += String.fromCharCode(chunk[index]);
    encoded += btoa(binary);
  }
  return encoded;
}
function probability(value: unknown, fallback: number): number { return Math.max(0, Math.min(1, typeof value === "number" && Number.isFinite(value) ? value : fallback)); }
function secondsToMs(seconds: number): number { return Math.max(0, Math.round(seconds * 1000)); }
function average(values: number[]): number { return values.reduce((sum, value) => sum + value, 0) / Math.max(1, values.length); }
function round2(value: number): number { return Math.round(Math.max(0, Math.min(100, value)) * 100) / 100; }
function round4(value: number): number { return Math.round(Math.max(0, Math.min(1, value)) * 10_000) / 10_000; }
function isObject(value: unknown): value is Record<string, unknown> { return typeof value === "object" && value !== null; }
function withTimeout<T>(operation: Promise<T>, timeoutMs: number): Promise<T> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      const error = new Error("Speech timed out");
      error.name = "SpeechTimeoutError";
      reject(error);
    }, timeoutMs);
    operation.then(
      value => { clearTimeout(timer); resolve(value); },
      error => { clearTimeout(timer); reject(error); },
    );
  });
}
