export interface Env {
  DOWNLOADS: R2Bucket;
}

const APK_KEY = "werus-0.003.apk";
const LEGACY_APK_KEY = "werus-0.3.0.apk";
const VERSION = "0.003";
const VERSION_CODE = 3;
const DOWNLOAD_PATH = `/werus-latest.apk`;
const DOWNLOAD_NAME = `WeRus-${VERSION}.apk`;
const APK_SHA256 = "bf9df75d9ab3883034cdb339ce080f0a14653defa98fe3601606139dc91be64b";
const GITHUB_STABLE_MANIFEST = "https://github.com/himentpear/RusMorph/releases/latest/download/android-stable.json";

const APP_ICON_B64 = "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";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response("Method Not Allowed", { status: 405, headers: { Allow: "GET, HEAD" } });
    }
    if (
      url.pathname === "/werus" || url.pathname === "/werus/" ||
      url.pathname === "/rusmorph" || url.pathname === "/rusmorph/" ||
      url.pathname === "/"
    ) {
      return landingPage(request.method === "HEAD");
    }
    if (url.pathname === "/werus/icon.png" || url.pathname === "/rusmorph/icon.png" || url.pathname === "/favicon.ico") {
      const bytes = Uint8Array.from(atob(APP_ICON_B64), c => c.charCodeAt(0));
      return new Response(bytes, {
        headers: {
          "content-type": "image/png",
          "cache-control": "public, max-age=86400",
        },
      });
    }
    if (url.pathname === "/werus/tts" || url.pathname === "/rusmorph/tts" || url.pathname === "/api/tts") {
      const text = url.searchParams.get("text") || url.searchParams.get("q") || "";
      const clean = text.replace(/[\u0300-\u036f\u00ad́]/g, "").trim();
      if (!clean) return new Response("Missing text parameter", { status: 400 });
      try {
        const ttsUrl = `https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=ru&q=${encodeURIComponent(clean)}`;
        const upstream = await fetch(ttsUrl, {
          headers: {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            "Referer": "https://translate.google.com/",
          },
        });
        if (!upstream.ok) {
          return new Response("TTS upstream failed", { status: upstream.status });
        }
        const audioBytes = await upstream.arrayBuffer();
        return new Response(audioBytes, {
          headers: {
            "content-type": "audio/mpeg",
            "cache-control": "public, max-age=604800, s-maxage=2592000",
            "access-control-allow-origin": "*",
          },
        });
      } catch (err) {
        return new Response("TTS proxy error", { status: 502 });
      }
    }
    if (url.pathname === "/werus/version.json" || url.pathname === "/rusmorph/version.json") {
      return Response.json({
        name: "全员俄人WeRus",
        version: VERSION,
        versionCode: VERSION_CODE,
        downloadUrl: `https://namchieh.org${DOWNLOAD_PATH}`,
        sha256: APK_SHA256,
        reviewWorkbench: "https://api.namchieh.org/review/",
      }, { headers: securityHeaders("application/json; charset=utf-8") });
    }
    if (url.pathname === "/update/android/stable") {
      if (request.method !== "GET" && request.method !== "HEAD") {
        const headers = securityHeaders("text/plain; charset=utf-8");
        headers.set("allow", "GET, HEAD");
        return new Response("Method Not Allowed", { status: 405, headers });
      }
      return stableUpdateManifest(env, request.method === "HEAD");
    }
    if (
      url.pathname === "/werus-latest.apk" ||
      url.pathname === `/werus-${VERSION}.apk` ||
      url.pathname === "/werus-0.3.0.apk" ||
      url.pathname === "/rusmorph-latest.apk" ||
      url.pathname === `/rusmorph-${VERSION}.apk` ||
      url.pathname === "/rusmorph-0.3.0.apk"
    ) {
      return apkResponse(request, env);
    }
    return new Response("Not Found", { status: 404 });
  },
};

async function stableUpdateManifest(env: Env, head: boolean): Promise<Response> {
  try {
    const upstream = await fetch(GITHUB_STABLE_MANIFEST, {
      headers: {
        "Accept": "application/json",
        "User-Agent": "RusMorph-Update-Manifest/1.0",
      },
      redirect: "follow",
    });
    if (upstream.ok) {
      const body = await upstream.text();
      const parsed = JSON.parse(body);
      if (parsed && typeof parsed === "object" && parsed.platform === "android" && parsed.channel === "stable") {
        const headers = securityHeaders("application/json; charset=utf-8");
        headers.set("cache-control", "public, max-age=300, s-maxage=300");
        return new Response(head ? null : body, { headers });
      }
    }
  } catch {
    // A GitHub Release may not exist yet; serve the currently published R2 build.
  }

  let metadata = await env.DOWNLOADS.head(APK_KEY);
  if (!metadata) metadata = await env.DOWNLOADS.head(LEGACY_APK_KEY);
  if (!metadata) return new Response("Not Found", { status: 404 });

  const manifest = {
    platform: "android",
    channel: "stable",
    versionCode: VERSION_CODE,
    versionName: VERSION,
    minSupportedVersionCode: 1,
    forceUpdate: false,
    publishedAt: "2026-09-19T12:26:34Z",
    title: `RusMorph ${VERSION}`,
    releaseNotes: [],
    apk: {
      url: `https://namchieh.org${DOWNLOAD_PATH}`,
      sha256: APK_SHA256,
      size: metadata.size,
    },
    releasePageUrl: "https://github.com/himentpear/RusMorph",
  };
  const headers = securityHeaders("application/json; charset=utf-8");
  headers.set("cache-control", "public, max-age=300, s-maxage=300");
  return new Response(head ? null : JSON.stringify(manifest), { headers });
}

async function apkResponse(request: Request, env: Env): Promise<Response> {
  let metadata = await env.DOWNLOADS.head(APK_KEY);
  let keyToUse = APK_KEY;
  if (!metadata) {
    metadata = await env.DOWNLOADS.head(LEGACY_APK_KEY);
    keyToUse = LEGACY_APK_KEY;
  }
  if (!metadata) return new Response("Not Found", { status: 404 });

  const range = parseRange(request.headers.get("range"), metadata.size);
  const object = request.method === "HEAD"
    ? null
    : await env.DOWNLOADS.get(keyToUse, range ? { range: { offset: range.start, length: range.length } } : undefined);
  if (request.method !== "HEAD" && !object?.body) return new Response("Not Found", { status: 404 });

  const headers = new Headers({
    "content-type": "application/vnd.android.package-archive",
    "content-disposition": `attachment; filename="${DOWNLOAD_NAME}"`,
    "content-length": String(range?.length ?? metadata.size),
    "cache-control": "public, max-age=300",
    "accept-ranges": "bytes",
    "etag": metadata.httpEtag,
    "x-content-type-options": "nosniff",
    "x-app-name": "WeRus",
    "x-werus-version": VERSION,
    "x-werus-sha256": APK_SHA256,
  });
  if (range) headers.set("content-range", `bytes ${range.start}-${range.end}/${metadata.size}`);
  return new Response(request.method === "HEAD" ? null : object!.body, { status: range ? 206 : 200, headers });
}

function landingPage(head: boolean): Response {
  const html = `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="theme-color" content="#1B1B1B">
<link rel="icon" type="image/png" href="data:image/png;base64,${APP_ICON_B64}">
<title>全员俄人 WeRus - 正式版 Android 官方下载</title>
<style>
:root {
  color-scheme: light;
  --canvas: #F4F4F6;
  --surface: #FFFFFF;
  --carbon: #1B1B1B;
  --graphite: #323232;
  --orange: #FC6E20;
  --cream: #FFE7D0;
  --outline: #E4E4E8;
  --pill: #ECECEF;
  --text-sec: #686870;
  --text-ter: #8E8E98;
}
* { box-sizing: border-box; }
body {
  margin: 0;
  background: var(--canvas);
  color: var(--carbon);
  font: 16px/1.65 -apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", Roboto, "Noto Sans SC", sans-serif;
  -webkit-font-smoothing: antialiased;
}
main {
  width: min(720px, calc(100% - 36px));
  margin: 0 auto;
  padding: 56px 0 80px;
}
.brand-header {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 24px;
}
.app-icon {
  width: 76px;
  height: 76px;
  border-radius: 20px;
  box-shadow: 0 8px 24px rgba(27, 27, 27, 0.08);
  border: 1px solid var(--outline);
  background: var(--surface);
  flex-shrink: 0;
}
.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  letter-spacing: .12em;
  color: var(--carbon);
  background: var(--cream);
  padding: 4px 10px;
  border-radius: 50px;
  font-weight: 700;
  margin: 0 0 6px;
}
.eyebrow .dot {
  width: 6px;
  height: 6px;
  background: var(--orange);
  border-radius: 50%;
}
h1 {
  font-size: clamp(34px, 7vw, 48px);
  line-height: 1.1;
  font-weight: 800;
  letter-spacing: -0.02em;
  margin: 0;
  color: var(--carbon);
}
h1 span {
  color: var(--orange);
  font-weight: 800;
}
.lead {
  font-size: 17px;
  color: var(--text-sec);
  line-height: 1.6;
  margin: 14px 0 28px;
}
.card {
  background: var(--surface);
  border: 1px solid var(--outline);
  border-radius: 22px;
  padding: 28px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.03);
}
.meta-badges {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}
.badge {
  background: var(--pill);
  color: var(--text-sec);
  font-size: 13px;
  padding: 5px 12px;
  border-radius: 8px;
  font-weight: 600;
}
.badge.highlight {
  background: var(--cream);
  color: var(--carbon);
}
.download-btn {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 18px 24px;
  background: var(--carbon);
  color: #ffffff;
  text-decoration: none;
  border-radius: 16px;
  font-weight: 700;
  font-size: 17px;
  transition: all 0.2s ease;
  box-shadow: 0 6px 18px rgba(27,27,27,0.18);
}
.download-btn:hover {
  background: var(--orange);
  transform: translateY(-1px);
}
.download-btn .arrow {
  font-size: 20px;
}
.features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 14px;
  margin-top: 24px;
}
.feature-item {
  background: var(--canvas);
  border: 1px solid var(--outline);
  border-radius: 14px;
  padding: 16px 18px;
}
.feature-title {
  font-weight: 700;
  font-size: 15px;
  margin-bottom: 4px;
  color: var(--carbon);
  display: flex;
  align-items: center;
  gap: 8px;
}
.feature-desc {
  font-size: 13px;
  color: var(--text-sec);
  line-height: 1.5;
  margin: 0;
}
.footer-note {
  margin-top: 24px;
  font-size: 13px;
  color: var(--text-ter);
  line-height: 1.6;
}
.hash-box {
  margin-top: 14px;
  background: var(--canvas);
  border-radius: 10px;
  padding: 10px 14px;
  font: 12px/1.5 ui-monospace, SFMono-Regular, Menlo, monospace;
  color: var(--text-sec);
  overflow-wrap: anywhere;
}
@media (max-width: 540px) {
  main { padding: 36px 0 60px; }
  .card { padding: 20px; }
  .download-btn { padding: 16px 20px; font-size: 16px; }
}
</style>
</head>
<body>
<main>
  <div class="brand-header">
    <img src="data:image/png;base64,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" class="app-icon" alt="全员俄人WeRus 图标">
    <div>
      <div class="eyebrow"><span class="dot"></span> OFFICIAL RELEASE · ANDROID</div>
      <h1>全员俄人 <span>WeRus</span></h1>
    </div>
  </div>

  <p class="lead">面向高校俄语学习者与专业研习人员的现代化词法助手。深度融入《大学俄语》教材体系，集成俄语标准发音与 Whisper 智能录音评测。</p>

  <section class="card">
    <div class="meta-badges">
      <span class="badge highlight">正式版 v${VERSION} (${VERSION_CODE})</span>
      <span class="badge">Android 8.0 及以上</span>
      <span class="badge">离线可用词典</span>
      <span class="badge">Whisper 评测</span>
    </div>

    <a class="download-btn" href="${DOWNLOAD_PATH}">
      <span>下载 WeRus Android 安装包</span>
      <span class="arrow">APK ↓</span>
    </a>

    <div class="features-grid">
      <div class="feature-item">
        <div class="feature-title">📚 大学俄语一、二册全收录</div>
        <p class="feature-desc">覆盖 30 个教学课次、2,870+ 核心词汇，完整标注性、数、格、体、变位法及语音交替。</p>
      </div>
      <div class="feature-item">
        <div class="feature-title">🎙️ Whisper 智能发音评测</div>
        <p class="feature-desc">现场录音并调用 Whisper 模型比对转写词形，精确诊断重音错位、吞音与元音弱化。</p>
      </div>
      <div class="feature-item">
        <div class="feature-title">🔊 原声标准示范播报</div>
        <p class="feature-desc">词条内置俄语原生 TTS 示范发音，支持用户录音与原声双轨对照复核。</p>
      </div>
      <div class="feature-item">
        <div class="feature-title">✦ AI 词法负一层工作区</div>
        <p class="feature-desc">多智能体支持词源探究、构词衍生与形态变格，随手下滑即可沉浸式解惑。</p>
      </div>
    </div>

    <div class="footer-note">
      <p>💡 提示：本版本为「全员俄人 WeRus」正式发布版，支持直接覆盖旧版升级安装，保留所有本地生词收藏与学习记录。若系统提示安全未知来源，请确认下载来源为 namchieh.org 官方域名。</p>
      <div class="hash-box">
        <strong>安装包校验码 (SHA-256):</strong><br>${APK_SHA256}
      </div>
    </div>
  </section>
</main>
</body>
</html>`;
  return new Response(head ? null : html, { headers: securityHeaders("text/html; charset=utf-8") });
}

function securityHeaders(contentType: string): Headers {
  return new Headers({
    "content-type": contentType,
    "cache-control": "public, max-age=300",
    "content-security-policy": "default-src 'none'; img-src data:; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'",
    "referrer-policy": "no-referrer",
    "x-content-type-options": "nosniff",
    "x-frame-options": "DENY",
  });
}

function parseRange(raw: string | null, size: number): { start: number; end: number; length: number } | null {
  if (!raw) return null;
  const match = /^bytes=(\d+)-(\d*)$/.exec(raw.trim());
  if (!match) return null;
  const start = Number(match[1]);
  const end = match[2] ? Math.min(Number(match[2]), size - 1) : size - 1;
  if (!Number.isInteger(start) || !Number.isInteger(end) || start < 0 || end < start || start >= size) return null;
  return { start, end, length: end - start + 1 };
}
