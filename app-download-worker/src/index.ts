export interface Env {
  DOWNLOADS: R2Bucket;
}

const APK_KEY = "rusmorph-0.3.0.apk";
const VERSION = "0.3.0";
const VERSION_CODE = 3;
const DOWNLOAD_PATH = `/rusmorph-${VERSION}.apk`;
const DOWNLOAD_NAME = `RusMorph-${VERSION}.apk`;
const APK_SHA256 = "4ba891d7b92aebf7629e1e278418d67cdf969eb24be377b5065e5d1c7d223eba";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response("Method Not Allowed", { status: 405, headers: { Allow: "GET, HEAD" } });
    }
    if (url.pathname === "/rusmorph" || url.pathname === "/rusmorph/") {
      return landingPage(request.method === "HEAD");
    }
    if (url.pathname === "/rusmorph/version.json") {
      return Response.json({
        version: VERSION,
        versionCode: VERSION_CODE,
        downloadUrl: `https://namchieh.org${DOWNLOAD_PATH}`,
        sha256: APK_SHA256,
        reviewWorkbench: "https://api.namchieh.org/review/",
      }, { headers: securityHeaders("application/json; charset=utf-8") });
    }
    if (url.pathname === "/rusmorph-latest.apk" || url.pathname === DOWNLOAD_PATH) return apkResponse(request, env);
    return new Response("Not Found", { status: 404 });
  },
};

async function apkResponse(request: Request, env: Env): Promise<Response> {
  const metadata = await env.DOWNLOADS.head(APK_KEY);
  if (!metadata) return new Response("Not Found", { status: 404 });
  const range = parseRange(request.headers.get("range"), metadata.size);
  const object = request.method === "HEAD"
    ? null
    : await env.DOWNLOADS.get(APK_KEY, range ? { range: { offset: range.start, length: range.length } } : undefined);
  if (request.method !== "HEAD" && !object?.body) return new Response("Not Found", { status: 404 });

  const headers = new Headers({
    "content-type": "application/vnd.android.package-archive",
    "content-disposition": `attachment; filename="${DOWNLOAD_NAME}"`,
    "content-length": String(range?.length ?? metadata.size),
    "cache-control": "public, max-age=300",
    "accept-ranges": "bytes",
    "etag": metadata.httpEtag,
    "x-content-type-options": "nosniff",
    "x-rusmorph-version": VERSION,
    "x-rusmorph-sha256": APK_SHA256,
  });
  if (range) headers.set("content-range", `bytes ${range.start}-${range.end}/${metadata.size}`);
  return new Response(request.method === "HEAD" ? null : object!.body, { status: range ? 206 : 200, headers });
}

function landingPage(head: boolean): Response {
  const html = `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="theme-color" content="#171512"><title>RusMorph Android 下载</title>
<style>
:root{color-scheme:light;--ink:#171512;--paper:#f4efe5;--red:#a33a2b;--line:#d3c9b8;--muted:#6d675e}*{box-sizing:border-box}
body{margin:0;background:var(--paper);color:var(--ink);font:16px/1.65 system-ui,-apple-system,"Noto Sans SC",sans-serif}
main{width:min(760px,calc(100% - 32px));margin:0 auto;padding:72px 0}.eyebrow{font-size:12px;letter-spacing:.18em;color:var(--red);font-weight:800}
h1{font:700 clamp(44px,10vw,80px)/.98 Georgia,serif;margin:12px 0 22px}h1 span{color:var(--red)}.lead{font-size:18px;color:var(--muted);max-width:620px}
.card{margin-top:38px;border:1px solid var(--line);background:#faf7f0;padding:28px}.meta{display:flex;gap:18px;flex-wrap:wrap;color:var(--muted);font-size:13px}
.download{display:flex;justify-content:space-between;align-items:center;gap:20px;margin:24px 0 18px;padding:17px 20px;background:var(--red);color:#fff;text-decoration:none;font-weight:800}
ul{padding-left:20px}.hash{overflow-wrap:anywhere;font:12px/1.6 ui-monospace,monospace;color:var(--muted)}.note{border-left:3px solid var(--red);padding-left:14px;color:var(--muted)}
@media(max-width:520px){main{padding:42px 0}.card{padding:20px}.download{align-items:flex-start;flex-direction:column}}
</style></head><body><main><p class="eyebrow">RUSSIAN LEARNING · ANDROID</p><h1>RusMorph<br><span>${VERSION}</span></h1>
<p class="lead">围绕课程、课次、学习单元与复习重构的俄语综合学习平台，保留完整词典、AI 与朗读纠音能力。</p>
<section class="card"><div class="meta"><strong>版本 ${VERSION}（${VERSION_CODE}）</strong><span>Android 8.0 及以上</span><span>约 12 MB</span></div>
<a class="download" href="${DOWNLOAD_PATH}"><span>下载 Android 安装包</span><span>APK ↓</span></a>
<ul><li>《大学俄语1》18 课与 1,929 条分课词汇</li><li>课程、对话、词汇与复习一体化学习路径</li><li>下拉显现带视差效果的 AI 负一层工作区</li><li>支持录音、回放、逐词标注与教师复核</li></ul>
<p class="note">升级安装可保留现有应用数据。若浏览器提示风险，请确认下载域名为 namchieh.org。</p>
<p class="hash">SHA-256<br>${APK_SHA256}</p></section></main></body></html>`;
  return new Response(head ? null : html, { headers: securityHeaders("text/html; charset=utf-8") });
}

function securityHeaders(contentType: string): Headers {
  return new Headers({
    "content-type": contentType,
    "cache-control": "public, max-age=300",
    "content-security-policy": "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'",
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
