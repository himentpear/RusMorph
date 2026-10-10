import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';
import { normalizeKey, verifyGithub } from './verify-github.mjs';

// Create Git objects only. The branch/PR is submitted separately, never merge or activate here.
const [appId, installationId, keyPath, workflowBlob] = process.argv.slice(2);
const describeOnly = process.argv.includes('--describe');
const root = fileURLToPath(new URL('../../', import.meta.url));
const output = new URL('../.wrangler/bootstrap-source.json', import.meta.url);
if (!/^[a-f0-9]{40}$/.test(workflowBlob || '')) throw new Error('Verified workflow blob required');
let github;
let stage = "verify-app";
try {
  const pem = await readFile(keyPath, 'utf8');
  await verifyGithub(appId, installationId, pem);
  await mkdir(new URL('../.wrangler/', import.meta.url), { recursive: true });
  await build({ entryPoints: [fileURLToPath(new URL('../worker/github.ts', import.meta.url))],
    bundle: true, platform: 'node', format: 'esm', packages: 'external',
    outfile: fileURLToPath(new URL('../.wrangler/github-bootstrap.mjs', import.meta.url)) });
  const { Github } = await import('../.wrangler/github-bootstrap.mjs');
  github = new Github({ GITHUB_APP_ID: appId, GITHUB_INSTALLATION_ID: installationId,
    GITHUB_APP_PRIVATE_KEY: normalizeKey(pem), GITHUB_REPOSITORY: 'himentpear/RusMorph', GITHUB_BASE_BRANCH: 'main' });
  stage = 'read-main';
  const base = await github.request('/git/ref/heads/main');
  const baseCommit = await github.request('/git/commits/' + base.object.sha);
  const files = execFileSync('git', ['ls-files', '--', 'admin-console'], { cwd: root, encoding: 'utf8' }).trim().split('\n')
    .filter(p => p && !p.includes('scripts/apply-integration.py') && !p.includes('scripts/patch-telemetry.py') && !p.includes('scripts/finish-backend.py'));
  files.push('admin-console/scripts/asset-metadata.py', 'admin-console/scripts/prepare-bootstrap.mjs',
    'tools/cloud_content/build_cloud_content.py', 'tools/textbook_dataset/validate_dataset.py', 'tools/lexicon/manage.py',
    'data-source/admin-dictionary/online-reviewed.json');
  for (let n = 1; n <= 4; n++) for (const name of ['manual_review', 'exercise_answers'])
    files.push(`data-source/admin-validation/textbook/university_russian_${n}/${name}.json`);
  const elements = [], evidence = [];
  for (const path of [...new Set(files)].sort()) {
    if (/\.pem$|\.env|\.dev\.vars|\.local\.|node_modules|(?:^|\/)\.wrangler(?:\/|$)|__pycache__/i.test(path)) throw new Error('Invalid bootstrap source path');
    stage = 'source:' + path;
    const bytes = await readFile(new URL(path, new URL('../../', import.meta.url)));
    const sha256 = createHash('sha256').update(bytes).digest('hex');
    const blob = describeOnly ? {sha:createHash('sha1').update(Buffer.from('blob '+bytes.length+'\0')).update(bytes).digest('hex')} : await github.request('/git/blobs', 'POST', { content: bytes.toString('base64'), encoding: 'base64' });
    elements.push({ path, mode: '100644', type: 'blob', sha: blob.sha });
    evidence.push({ path, size: bytes.length, sha256 });
    if (evidence.length % 10 === 0) console.log(JSON.stringify({ stage: 'prepare-source', files: evidence.length }));
  }
  elements.push({ path: '.github/workflows/admin-content.yml', mode: '100644', type: 'blob', sha: workflowBlob });
  stage = 'create-tree';
  const tree = describeOnly ? {sha:null} : await github.request('/git/trees', 'POST', { base_tree: baseCommit.tree.sha, tree: elements });
  const record = { baseCommit: base.object.sha, baseTree:baseCommit.tree.sha, treeElements:elements, tree: tree.sha, localSourceCommit: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim(),
    files: evidence, workflowBlob, wroteBranch: false, publishedContent: false };
  await writeFile(output, JSON.stringify(record, null, 2) + '\n');
  console.log(JSON.stringify({ baseCommit: record.baseCommit, tree: record.tree, sourceFiles: evidence.length + 1, wroteBranch: false, publishedContent: false }));
} catch (error) {
  console.error(JSON.stringify({stage,error:error?.code||'LOCAL_OR_GITHUB_ERROR',reason:/^(GitHub configuration verification failed|App ID does not match|Install the active App|Required restricted permission missing|Unexpected installation permission|App must be installed only|Invalid GitHub App identifiers|fetch failed|Invalid bootstrap source path)/.test(error?.message||'')?error.message:'details withheld'}));
  process.exitCode = 1;
} finally {
  if (github?.token) {
    const response = await fetch('https://api.github.com/installation/token', { method: 'DELETE',
      headers: { authorization: 'Bearer ' + github.token, 'user-agent': 'WeRus-admin-bootstrap' } });
    if (response.status !== 204) { console.error('Temporary source preparation token could not be revoked'); process.exitCode = 1; }
  }
}
