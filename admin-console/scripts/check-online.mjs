import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';

const root = new URL('../', import.meta.url);
const config = JSON.parse(await readFile(new URL('wrangler.jsonc', root), 'utf8'));
const bootstrap = process.argv.includes('--bootstrap');
const baseline = JSON.parse(await readFile(new URL('ONLINE_BASELINE.json', root), 'utf8'));
const html = await readFile(new URL('dist/index.html', root), 'utf8');
const paths = ['/', '/api/admin/v1/me', ...Array.from(html.matchAll(/(?:src|href)="(\/assets\/[^"\s]+)/g), match => match[1])];
const checks = [];
for (const path of paths) {
  const response = await fetch(config.vars.ADMIN_ORIGIN + path, { redirect: 'manual' });
  if (bootstrap) {
    const body = await response.json();
    if (response.status !== 503 || body?.error?.code !== 'ACCESS_NOT_CONFIGURED')
      throw new Error('Bootstrap access rejection failed: ' + path);
  } else {
    const location = response.headers.get('location');
    if (response.status !== 302 || !location || new URL(location).origin !== config.vars.ACCESS_ISSUER)
      throw new Error('Access login redirect missing: ' + path);
  }
  checks.push({ path, status: response.status });
}
let token = process.env.CLOUDFLARE_API_TOKEN;
if (!token) {
  const text = await readFile(join(homedir(), 'AppData', 'Roaming', 'xdg.config', '.wrangler', 'config', 'default.toml'), 'utf8');
  token = /oauth_token\s*=\s*"([^"]+)"/.exec(text)?.[1];
}
if (!token) throw new Error('Local Cloudflare credential required for read-only deployment checks');
async function request(path, body) {
  const response = await fetch('https://api.cloudflare.com/client/v4/accounts/' + config.account_id + path, {
    method: body === undefined ? 'GET' : 'POST',
    headers: { authorization: 'Bearer ' + token, 'content-type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) throw new Error('Deployment check API failed: HTTP ' + response.status);
  const result = await response.json();
  if (!result.success) throw new Error('Deployment check API rejected request');
  return result.result;
}
const subdomain = await request('/workers/scripts/' + config.name + '/subdomain');
if (subdomain.enabled !== false || subdomain.previews_enabled !== false)
  throw new Error('workers.dev or preview access remains enabled');
const domains = await request('/workers/domains');
const hostname = new URL(config.vars.ADMIN_ORIGIN).hostname;
if (!domains.some(domain => domain.hostname === hostname && domain.service === config.name))
  throw new Error('Custom domain does not belong to this Worker');
const database = config.d1_databases.find(binding => binding.binding === 'ADMIN_DB').database_id;
const query = sql => request('/d1/database/' + database + '/query', { sql, params: [] });
const active = (await query('SELECT r.id,r.content_version,r.dictionary_version,r.resource_hash,a.revision FROM active_release a JOIN releases r ON r.id=a.release_id WHERE a.slot=1'))[0]?.results?.[0];
if (active?.id !== baseline.releaseId || active?.resource_hash !== baseline.resourceHash || active?.revision !== 0)
  throw new Error('Registered online content changed; inspect before proceeding');
const live = (await query("SELECT enabled FROM controls WHERE name='live'"))[0]?.results?.[0];
if (live?.enabled !== 0) throw new Error('Live must remain disabled for this delivery');
const localSourceCommit = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim();
const record = { checkedAt: new Date().toISOString(), origin: config.vars.ADMIN_ORIGIN, mode: bootstrap ? 'bootstrap-deny-all' : 'access-login', localSourceCommit, checks, workersDev: false, previews: false, activeRelease: active, liveEnabled: false };
await mkdir(new URL('.wrangler/', root), { recursive: true });
await writeFile(new URL('.wrangler/online-check.json', root), JSON.stringify(record, null, 2) + '\n');
console.log(JSON.stringify(record, null, 2));
