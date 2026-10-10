import { readFile, writeFile } from 'node:fs/promises';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { createHash } from 'node:crypto';
import { validateOrganization, validatePolicies, administrator } from './access-policy.mjs';
const root=new URL('../',import.meta.url),account='f969744f6b1b6276e1fec7df747ba9e4',origin='https://werus-admin.namchieh.org';
const digest=b=>createHash('sha256').update(b).digest('hex');
async function credentials(){if(process.env.CLOUDFLARE_API_TOKEN)return process.env.CLOUDFLARE_API_TOKEN;const file=join(homedir(),'AppData','Roaming','xdg.config','.wrangler','config','default.toml');const text=await readFile(file,'utf8');const token=/oauth_token\s*=\s*"([^"]+)"/.exec(text)?.[1];if(!token)throw new Error('Cloudflare credentials unavailable');return token;}
const token=await credentials();
async function request(path,method='GET',body,raw=false){const response=await fetch('https://api.cloudflare.com/client/v4/accounts/'+account+path,{method,headers:{authorization:'Bearer '+token,...(body===undefined?{}:{'content-type':'application/json'})},body:body===undefined?undefined:JSON.stringify(body)});if(!response.ok){const e=new Error('Cloudflare '+method+' '+path.split('?')[0]+' returned '+response.status);e.status=response.status;throw e;}if(raw)return new Uint8Array(await response.arrayBuffer());const data=await response.json();if(!data.success)throw new Error('Cloudflare API rejected '+path.split('?')[0]);return data.result;}
const query=(db,sql,params=[])=>request('/d1/database/'+db+'/query','POST',{sql,params});
async function columns(db,table){return (await query(db,'PRAGMA table_info('+table+')'))[0].results.map(x=>x.name);}
async function migrateDomain(db,table,changes,file){const names=await columns(db,table);if(!names.length)throw new Error('Missing prerequisite table '+table);let sql=await readFile(new URL(file,root),'utf8');for(const [name,statement] of changes){if(names.includes(name))sql=sql.replace(statement,'');}if(sql.trim())await query(db,sql);}
const cfg=JSON.parse(await readFile(new URL('wrangler.jsonc',root),'utf8'));
let adminId=cfg.d1_databases.find(x=>x.binding==='ADMIN_DB').database_id;
if(process.argv.includes('--access-only')){await configureAccess();process.exit(process.exitCode||0);}
const databases=await request('/d1/database');let database=databases.find(x=>x.name==='werus-admin');if(!database)database=await request('/d1/database','POST',{name:'werus-admin'});adminId=database.uuid;cfg.d1_databases.find(x=>x.binding==='ADMIN_DB').database_id=adminId;
await query(adminId,'CREATE TABLE IF NOT EXISTS admin_schema_migrations(name TEXT PRIMARY KEY,applied_at TEXT NOT NULL)');
const applied=(await query(adminId,"SELECT name FROM admin_schema_migrations WHERE name='0001_admin'"))[0].results;
if(!applied.length){await query(adminId,await readFile(new URL('migrations/0001_admin.sql',root),'utf8'));await query(adminId,"INSERT INTO admin_schema_migrations VALUES('0001_admin',?)",[new Date().toISOString()]);}
const userId=cfg.d1_databases.find(x=>x.binding==='USER_DB').database_id,reviewId=cfg.d1_databases.find(x=>x.binding==='REVIEW_DB').database_id;
if(!(await columns(userId,'user_memberships')).length)await query(userId,"CREATE TABLE user_memberships(user_id TEXT PRIMARY KEY REFERENCES users(user_id),status TEXT NOT NULL CHECK(status IN ('active','revoked')),expires_at TEXT NOT NULL CHECK(expires_at GLOB '????-??-??T??:??:??.???Z'),updated_at TEXT NOT NULL)");
await migrateDomain(userId,'user_memberships',[['revision','ALTER TABLE user_memberships ADD COLUMN revision INTEGER NOT NULL DEFAULT 0;']],'domain-migrations/users/0006_admin_operations.sql');
await migrateDomain(reviewId,'review_submissions',[
 ['admin_revision','ALTER TABLE review_submissions ADD COLUMN admin_revision INTEGER NOT NULL DEFAULT 0;'],
 ['admin_excluded','ALTER TABLE review_submissions ADD COLUMN admin_excluded INTEGER NOT NULL DEFAULT 0 CHECK(admin_excluded IN (0,1));'],
 ['admin_annotation_json','ALTER TABLE review_submissions ADD COLUMN admin_annotation_json TEXT;']],'domain-migrations/reviews/0001_admin_operations.sql');
console.log('Additive domain migrations applied; existing user/review rows preserved.');
const registered=(await query(adminId,'SELECT release_id FROM active_release WHERE slot=1'))[0].results;
if(!registered.length){
 const get=key=>request('/r2/buckets/rusmorph-content/objects/'+key,'GET',undefined,true);
 const [cpRaw,dpRaw]=await Promise.all([get('content/current.json'),get('dictionary/current.json')]);const cp=JSON.parse(new TextDecoder().decode(cpRaw)),dp=JSON.parse(new TextDecoder().decode(dpRaw));
 for(const [prefix,pointer] of [['content',cp],['dictionary',dp]])if(!/^\d{4}\.\d{2}\.\d{2}\.[a-f0-9]{12}$/.test(pointer.catalogVersion)||pointer.manifestKey!==prefix+'/'+pointer.catalogVersion+'/manifest.json')throw new Error('Invalid online pointer');
 const [cmRaw,dmRaw]=await Promise.all([get(cp.manifestKey),get(dp.manifestKey)]);const cm=JSON.parse(new TextDecoder().decode(cmRaw)),dm=JSON.parse(new TextDecoder().decode(dmRaw));
 const descriptors=[...cm.components,{key:cp.manifestKey,sha256:digest(cmRaw),size:cmRaw.length},{key:dp.manifestKey,sha256:digest(dmRaw),size:dmRaw.length},...Object.values(dm.components)];
 const indexRaw=await get(dm.components.index.key);if(digest(indexRaw)!==dm.components.index.sha256)throw new Error('Dictionary index corrupt');const index=JSON.parse(new TextDecoder().decode(indexRaw));for(const entry of index)descriptors.push(entry.detail,entry.inflections);
 const unique=[...new Map(descriptors.map(x=>[x.key,x])).values()];let progress=0;for(let i=0;i<unique.length;i+=8){await Promise.all(unique.slice(i,i+8).map(async d=>{if(!d.key.startsWith('content/'+cp.catalogVersion+'/')&&!d.key.startsWith('dictionary/'+dp.catalogVersion+'/'))throw new Error('Online descriptor escaped version');const bytes=await get(d.key);if(bytes.length!==d.size||digest(bytes)!==d.sha256)throw new Error('Online resource verification failed: '+d.key);}));progress=Math.min(i+8,unique.length);if(i%64===0)console.log('Online immutable resources verified:',progress,'/',unique.length);}
 const [cpCheck,dpCheck]=await Promise.all([get('content/current.json'),get('dictionary/current.json')]);if(digest(cpRaw)!==digest(cpCheck)||digest(dpRaw)!==digest(dpCheck))throw new Error('Online pointers changed during registration; retry');
 const releaseId='online-'+cp.catalogVersion+'-'+dp.catalogVersion,resourceHash=digest(JSON.stringify(unique));
 await query(adminId,'INSERT INTO releases VALUES(?,?,?,?,?,?,?,?,?,1)',[releaseId,cp.catalogVersion,dp.catalogVersion,cp.manifestKey,dp.manifestKey,null,resourceHash,new Date().toISOString(),null]);
 for(let i=0;i<unique.length;i+=50){const slice=unique.slice(i,i+50);await query(adminId,'INSERT OR IGNORE INTO resource_refs VALUES '+slice.map(()=>'(?,?)').join(','),slice.flatMap(d=>[releaseId,d.key]));}
 await query(adminId,'INSERT INTO active_release VALUES(1,?,0)',[releaseId]);
 await writeFile(new URL('ONLINE_BASELINE.json',root),JSON.stringify({registeredAt:new Date().toISOString(),releaseId,contentVersion:cp.catalogVersion,dictionaryVersion:dp.catalogVersion,resourceHash,verifiedResources:unique.length,sourceCommit:null},null,2)+'\n');console.log('Registered current online release; no content uploaded or activated.');
}
await writeFile(new URL('wrangler.jsonc',root),JSON.stringify(cfg,null,2)+'\n');
if(process.argv.includes('--configure-access'))await configureAccess();else console.log('Access not changed. Run with --configure-access after granting Access management permissions.');
async function configureAccess(){
 try{let issuer;try{const org=await request('/access/organizations');issuer=validateOrganization(org,process.env.WERUS_ACCESS_TEAM_DOMAIN);}catch(e){if(e.status!==403||!process.env.WERUS_ACCESS_TEAM_DOMAIN)throw e;issuer=validateOrganization({auth_domain:process.env.WERUS_ACCESS_TEAM_DOMAIN},process.env.WERUS_ACCESS_TEAM_DOMAIN);const certs=await fetch(issuer+'/cdn-cgi/access/certs');if(!certs.ok||!(await certs.json()).keys?.some(k=>k.kty==='RSA'))throw new Error('The supplied Access team does not publish valid signing keys');console.log('Using the user-supplied Access team with verified public signing keys; organization API remains restricted.');}const apps=await request('/access/apps');let app=apps.find(x=>x.domain==='werus-admin.namchieh.org');if(!app)app=await request('/access/apps','POST',{name:'WeRus 运营管理台',domain:'werus-admin.namchieh.org',type:'self_hosted',session_duration:'8h',app_launcher_visible:false});
 if(app.type!=='self_hosted'||!app.aud||app.session_duration!=='8h')throw new Error('Existing Access app type, audience or session duration must be reviewed');
 const policies=await request('/access/apps/'+app.id+'/policies');validatePolicies(policies);if(!policies.some(p=>p.decision==='allow'))await request('/access/apps/'+app.id+'/policies','POST',{name:'WeRus administrators',decision:'allow',precedence:1,include:[{email:{email:administrator}}],exclude:[],require:[]});
 validatePolicies(await request('/access/apps/'+app.id+'/policies'));
 cfg.vars.ACCESS_ISSUER=issuer;cfg.vars.ACCESS_AUD=app.aud;await writeFile(new URL('wrangler.jsonc',root),JSON.stringify(cfg,null,2)+'\n');console.log('Access application configured for himentpear@gmail.com; JWT issuer and audience recorded.');
 }catch(e){console.log('Access setup blocked:',e.message);process.exitCode=2;}
}
