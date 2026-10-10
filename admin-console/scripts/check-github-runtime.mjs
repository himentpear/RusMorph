import { readFile } from 'node:fs/promises';
import { build } from 'esbuild';
import { fileURLToPath } from 'node:url';
import { normalizeKey, verifyGithub } from './verify-github.mjs';

const [appId, installationId, path] = process.argv.slice(2);
try {
  const pem = await readFile(path, 'utf8');
  await verifyGithub(appId, installationId, pem);
  await build({ entryPoints: [fileURLToPath(new URL('../worker/github.ts', import.meta.url))],
    bundle: true, platform: 'node', format: 'esm', packages: 'external',
    outfile: fileURLToPath(new URL('../.wrangler/github-runtime-check.mjs', import.meta.url)) });
  const { Github } = await import('../.wrangler/github-runtime-check.mjs');
  const github = new Github({ GITHUB_APP_ID: appId, GITHUB_INSTALLATION_ID: installationId,
    GITHUB_APP_PRIVATE_KEY: normalizeKey(pem), GITHUB_REPOSITORY: 'himentpear/RusMorph', GITHUB_BASE_BRANCH: 'main' });
  try {
    const head = await github.request('/git/ref/heads/main');
    const workflow = await github.request('/contents/.github/workflows/admin-content.yml?ref=main', 'GET', undefined, true);
    const compiler = await github.request('/contents/admin-console/scripts/compile-release.py?ref=main', 'GET', undefined, true);
    console.log(JSON.stringify({ runtimeAuthentication: true, repository: 'himentpear/RusMorph',
      mainCommit: head.object.sha, contentWorkflowPresent: Boolean(workflow), compilerPresent: Boolean(compiler), wroteSource: false }));
  } finally {
    // The bundled TypeScript private field is used only to revoke this isolated smoke token.
    if (github.token) {
      const response = await fetch('https://api.github.com/installation/token', { method: 'DELETE',
        headers: { authorization: 'Bearer ' + github.token, 'user-agent': 'WeRus-admin-runtime-check' } });
      if (response.status !== 204) throw new Error('Smoke token revocation failed');
    }
  }
} catch {
  console.error('GitHub runtime check failed; no source was submitted. Check App permissions, local PEM and network access.');
  process.exitCode = 1;
}
