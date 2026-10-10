import { WorkerEntrypoint } from 'cloudflare:workers';
import { ZodError } from 'zod';
import type { Env } from './env';
import { ApiError, fail, json, list, readJson } from './common';
import { authenticate, sameOrigin, secure } from './auth';
import { auditList, controlUpdate, membership, reviewAudio, reviewList, reviewUpdate, userDetail, userList } from './domain';
import { catalog, draftCreate, draftDiff, draftList, draftUpdate, draftValidate, sourceComponent, active } from './content';
import { upload, remove } from './resources';
import { createRate, overview, recordEvent, retention } from './telemetry';
import { Github } from './github';
export { ReleaseCoordinator } from './release';
export class OperationsRegistry extends WorkerEntrypoint<Env>{
 async controls(){const rows=(await this.env.ADMIN_DB.prepare('SELECT name,enabled FROM controls').all<{name:string;enabled:number}>()).results;return Object.fromEntries(rows.map(r=>[r.name,Boolean(r.enabled)]));}
 async activeVersions(){const r=await active(this.env);if(!r)throw new Error('RELEASE_NOT_REGISTERED');return {contentVersion:String(r.content_version),dictionaryVersion:String(r.dictionary_version)};}
 async recordEvent(input:unknown){await recordEvent(this.env,input);return {recorded:true};}
}
export async function handle(request:Request,env:Env){try{
 const admin=await authenticate(request,env);if(!await env.ADMIN_DB.prepare("SELECT 1 FROM admin_principals WHERE email=? AND role='admin'").bind(admin.email).first())fail(403,'ADMIN_REQUIRED','此账号没有管理台权限');const url=new URL(request.url);if(url.origin!==env.ADMIN_ORIGIN)fail(403,'WRONG_HOST','管理台仅允许专用域名访问');
 if(!['GET','HEAD'].includes(request.method))sameOrigin(request,env);
 const path=url.pathname.replace(/^\/api\/admin\/v1/,'');
 if(!url.pathname.startsWith('/api/admin/v1/')){if(!['GET','HEAD'].includes(request.method))fail(405,'METHOD_NOT_ALLOWED','请求方式无效');return secure(await env.ASSETS.fetch(request));}
 const method=request.method;
 if(path==='/me'&&method==='GET')return secure(json({...admin,integrations:{accessConfigured:!!env.ACCESS_AUD,githubAppConfigured:new Github(env).configured()},origin:env.ADMIN_ORIGIN}));
 if(path==='/overview'&&method==='GET')return secure(json(await overview(env)));
 if(path==='/users'&&method==='GET')return secure(json(await userList(env,url)));
 let m=/^\/users\/([a-zA-Z0-9_-]+)(\/membership)?$/.exec(path);
 if(m&&method==='GET'&&!m[2])return secure(json(await userDetail(env,m[1])));
 if(m&&method==='POST'&&m[2])return secure(json(await membership(env,admin.email,m[1],await readJson(request))));
 if(path==='/catalog'&&method==='GET')return secure(json(await catalog(env)));
 if(path==='/content/source'&&method==='GET')return secure(json(await sourceComponent(env,url.searchParams.get('kind')||'',url.searchParams.get('component')||'')));
 if(path==='/drafts'&&method==='GET')return secure(json(await draftList(env,url)));
 if(path==='/drafts'&&method==='POST')return secure(json(await draftCreate(env,admin.email,await readJson(request)),201));
 m=/^\/drafts\/([a-f0-9-]+)(\/validate|\/diff)?$/.exec(path);
 if(m&&method==='GET')return secure(json(await draftDiff(env,m[1])));
 if(m&&method==='PATCH'&&!m[2])return secure(json(await draftUpdate(env,admin.email,m[1],await readJson(request))));
 if(m&&method==='POST'&&m[2]==='/validate')return secure(json(await draftValidate(env,admin.email,m[1],await readJson(request))));
 if(path==='/releases'&&method==='GET')return secure(json(await list(env.ADMIN_DB,'SELECT r.*,a.revision registry_revision,CASE WHEN a.release_id=r.id THEN 1 ELSE 0 END active FROM releases r LEFT JOIN active_release a ON a.slot=1 ORDER BY r.created_at DESC,r.id',[],url)));
 if(path==='/releases'&&method==='POST')return secure(json(await env.PUBLISHER.getByName('content-registry').submit(admin.email,await readJson(request)),202));
 if(path==='/releases/rollback'&&method==='POST')return secure(json(await env.PUBLISHER.getByName('content-registry').rollback(admin.email,await readJson(request)),202));
 if(path==='/tasks'&&method==='GET')return secure(json(await list(env.ADMIN_DB,'SELECT id,actor,kind,status,step,source_commit,pr_url,error_code,created_at,updated_at FROM tasks ORDER BY created_at DESC,id',[],url)));
 m=/^\/tasks\/([a-f0-9-]+)(\/merge|\/retry|\/cancel)?$/.exec(path);
 if(m&&method==='GET'){const task=await env.ADMIN_DB.prepare('SELECT id,actor,kind,status,step,source_commit,pr_url,error_code,created_at,updated_at FROM tasks WHERE id=?').bind(m[1]).first();if(!task)fail(404,'NOT_FOUND','任务不存在');return secure(json(task));}
 if(m&&method==='POST'&&m[2]==='/merge')return secure(json(await env.PUBLISHER.getByName('content-registry').confirmMerge(admin.email,m[1],await readJson(request)),202));
 if(m&&method==='POST'&&m[2]==='/retry')return secure(json(await env.PUBLISHER.getByName('content-registry').resume(admin.email,m[1],await readJson(request)),202));
 if(m&&method==='POST'&&m[2]==='/cancel')return secure(json(await env.PUBLISHER.getByName('content-registry').cancel(admin.email,m[1],await readJson(request))));
 if(path==='/resources'&&method==='GET')return secure(json(await list(env.ADMIN_DB,'SELECT r.*,COUNT(rr.release_id) reference_count FROM resources r LEFT JOIN resource_refs rr ON rr.resource_key=r.key GROUP BY r.key ORDER BY r.created_at DESC,r.key',[],url)));
 if(path==='/resources'&&method==='POST')return secure(json(await upload(env,admin.email,request),201));
 if(path==='/resources'&&method==='DELETE')return secure(json(await remove(env,admin.email,url.searchParams.get('key')||'',await readJson(request))));
 if(path==='/reviews'&&method==='GET')return secure(json(await reviewList(env,url)));
 m=/^\/reviews\/([a-zA-Z0-9_-]+)(\/audio)?$/.exec(path);
 if(m&&method==='GET'&&m[2])return secure(await reviewAudio(env,m[1],request));
 if(m&&method==='PATCH'&&!m[2])return secure(json(await reviewUpdate(env,admin.email,m[1],await readJson(request))));
 if(path==='/operations'&&method==='GET'){let backend:unknown={available:false};try{backend=await env.AGENT_ADMIN?.status()||backend;}catch{}return secure(json({backend,controls:(await env.ADMIN_DB.prepare('SELECT * FROM controls ORDER BY name').all()).results,rates:(await env.ADMIN_DB.prepare('SELECT * FROM rates ORDER BY effective_at DESC').all()).results,events:await list(env.ADMIN_DB,'SELECT id,model,feature,elapsed_ms,result,input_units,output_units,unit,fallback_reason,occurred_at FROM model_events ORDER BY occurred_at DESC,id',[],url)}));}
 m=/^\/operations\/controls\/([a-z]+)$/.exec(path);if(m&&method==='PATCH')return secure(json(await controlUpdate(env,admin.email,m[1],await readJson(request))));
 if(path==='/operations/rates'&&method==='POST')return secure(json(await createRate(env,admin.email,await readJson(request)),201));
 if(path==='/audit'&&method==='GET')return secure(json(await auditList(env,url)));
 return fail(404,'NOT_FOUND','接口不存在');
 }catch(error){if(error instanceof ApiError)return secure(json({error:{code:error.code,message:error.message}},error.status));if(error instanceof ZodError)return secure(json({error:{code:'VALIDATION_FAILED',message:'输入格式无效',fields:error.issues.map(i=>({path:i.path.join('.'),message:i.message}))}},400));
 // Never log request bodies, JWTs, learner data or upstream exception payloads.
 console.error('admin_request_failed',{code:'INTERNAL_ERROR'});return secure(json({error:{code:'INTERNAL_ERROR',message:'服务暂不可用，请稍后重试'}},503));}}
export default {fetch:handle,async scheduled(_event:ScheduledController,env:Env){await retention(env);}} satisfies ExportedHandler<Env>;
