import { readFile } from 'node:fs/promises';
import { createPrivateKey } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { importPKCS8, SignJWT } from 'jose';

export function normalizeKey(pem) {
  const key = createPrivateKey(pem);
  if (key.asymmetricKeyType !== 'rsa') throw new Error('GitHub App requires an RSA key');
  return key.export({ type: 'pkcs8', format: 'pem' }).toString();
}
export async function verifyGithub(appId, installationId, pem) {
  if (!/^\d+$/.test(appId) || !/^\d+$/.test(installationId)) throw new Error('Invalid GitHub App identifiers');
  const key = await importPKCS8(normalizeKey(pem), 'RS256');
  const jwt = await new SignJWT({}).setProtectedHeader({alg:'RS256'}).setIssuer(appId)
    .setIssuedAt(Math.floor(Date.now()/1000)-60).setExpirationTime('8m').sign(key);
  async function request(path, token = jwt, method = 'GET', body) {
    const response = await fetch('https://api.github.com'+path, {method,
      headers:{authorization:'Bearer '+token,accept:'application/vnd.github+json','user-agent':'WeRus-admin-setup','x-github-api-version':'2022-11-28','content-type':'application/json'},
      body:body===undefined?undefined:JSON.stringify(body)});
    if (!response.ok) throw new Error('GitHub configuration verification failed ('+response.status+')');
    return response.status===204?null:response.json();
  }
  const app = await request('/app');
  if (String(app.id)!==appId) throw new Error('App ID does not match private key');
  const install = await request('/app/installations/'+installationId);
  if (install.suspended_at || install.account?.login!=='himentpear' || install.repository_selection!=='selected')
    throw new Error('Install the active App only on the selected himentpear/RusMorph repository');
  const required={contents:'write',pull_requests:'write',checks:'read',actions:'read',metadata:'read'};
  const missing=Object.entries(required).filter(([permission,value])=>install.permissions?.[permission]!==value);
  if(missing.length)throw new Error('Required restricted permission missing: '+missing.map(([p,v])=>p+'='+v+' (current '+(install.permissions?.[p]||'none')+')').join(', '));
  for (const [permission,value] of Object.entries(install.permissions||{}))
    if (value!=='none' && !Object.hasOwn(required,permission)) throw new Error('Unexpected installation permission: '+permission);
  const access=await request('/app/installations/'+installationId+'/access_tokens',jwt,'POST',{permissions:required});
  try {
    const repos=await request('/installation/repositories?per_page=100',access.token);
    if(repos.total_count!==1 || repos.repositories?.[0]?.full_name!=='himentpear/RusMorph')
      throw new Error('App must be installed only on himentpear/RusMorph');
  } finally { await request('/installation/token',access.token,'DELETE'); }
  return {appId,installationId,repository:'himentpear/RusMorph',verified:true};
}
if (process.argv[1] && fileURLToPath(import.meta.url)===resolve(process.argv[1])) {
  try {
    const [appId,installationId,path]=process.argv.slice(2);
    console.log(JSON.stringify(await verifyGithub(appId,installationId,await readFile(path,'utf8'))));
  } catch (error) { const message=String(error?.message||'');console.error(/^(GitHub configuration verification failed|App ID does not match|Install the active App|Required restricted permission missing|Unexpected installation permission|App must be installed only|Invalid GitHub App identifiers)/.test(message)?message:'GitHub App key could not be read or parsed; verify the local RSA PEM path.');process.exitCode=1; }
}
