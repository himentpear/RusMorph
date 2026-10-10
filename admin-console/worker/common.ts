import { z } from 'zod';
export class ApiError extends Error { constructor(public status:number,public code:string,message:string){super(message);} }
export const fail=(status:number,code:string,message:string):never=>{throw new ApiError(status,code,message);};
export const json=(value:unknown,status=200)=>Response.json(value,{status,headers:{'cache-control':'no-store'}});
export const id=z.string().regex(/^[a-zA-Z0-9_.:-]{1,160}$/);
export const revision=z.number().int().nonnegative();
export const operation=z.string().uuid();
export async function readJson(request:Request,limit=1_048_576):Promise<unknown>{
 if(!request.headers.get('content-type')?.startsWith('application/json'))fail(415,'JSON_REQUIRED','需要 JSON 请求');
 if(!request.body)fail(400,'INVALID_JSON','请求内容为空');
 const reader=request.body!.getReader();let bytes=0;const chunks:Uint8Array[]=[];
 try{for(;;){const v=await reader.read();if(v.done)break;bytes+=v.value.length;if(bytes>limit){await reader.cancel();fail(413,'TOO_LARGE','请求过大');}chunks.push(v.value);}}finally{reader.releaseLock();}
 const data=new Uint8Array(bytes);let offset=0;for(const c of chunks){data.set(c,offset);offset+=c.length;}
 try{return JSON.parse(new TextDecoder().decode(data));}catch{return fail(400,'INVALID_JSON','JSON 格式无效');}
}
export async function hash(value:string|Uint8Array){return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',typeof value==='string'?new TextEncoder().encode(value):value))).map(v=>v.toString(16).padStart(2,'0')).join('');}
export function page(url:URL){const offset=Number(url.searchParams.get('cursor')||0);if(!Number.isSafeInteger(offset)||offset<0)fail(400,'BAD_CURSOR','分页标识无效');return {offset,limit:Math.min(100,Math.max(1,Number(url.searchParams.get('limit')||25)||25))};}
export async function list(db:D1Database,sql:string,args:unknown[],url:URL){const {offset,limit}=page(url);const rows=(await db.prepare(sql+' LIMIT ? OFFSET ?').bind(...args,limit+1,offset).all()).results;return {items:rows.slice(0,limit),nextCursor:rows.length>limit?String(offset+limit):null};}
export async function replay(db:D1Database,actor:string,action:string,object:string,op:string,payload:unknown){
 const fingerprint=await hash(JSON.stringify({actor,action,object,payload}));
 const existing=await db.prepare('SELECT fingerprint FROM admin_audit WHERE operation_id=?').bind(op).first<{fingerprint:string}>();
 if(existing&&existing.fingerprint!==fingerprint)fail(409,'IDEMPOTENCY_CONFLICT','此操作标识已用于其他请求');
 return Boolean(existing);
}
export async function atomic(db:D1Database,actor:string,action:string,object:string,op:string,payload:unknown,statement:D1PreparedStatement|D1PreparedStatement[]){
 const fingerprint=await hash(JSON.stringify({actor,action,object,payload}));
 if(await replay(db,actor,action,object,op,payload))return {replayed:true};
 try{await db.batch([
 db.prepare('INSERT INTO admin_audit(operation_id,actor,action,object_id,fingerprint,result,created_at) VALUES(?,?,?,?,?,?,?)').bind(op,actor,action,object,fingerprint,'success',new Date().toISOString()),
 ...(Array.isArray(statement)?statement:[statement]).flatMap(s=>[s,db.prepare('INSERT INTO admin_guard(passed) VALUES(changes())')]),
 db.prepare('DELETE FROM admin_guard')]);return {replayed:false};
 }catch(e){const winner=await db.prepare('SELECT fingerprint FROM admin_audit WHERE operation_id=?').bind(op).first<{fingerprint:string}>();if(winner?.fingerprint===fingerprint)return {replayed:true};if(winner)fail(409,'IDEMPOTENCY_CONFLICT','此操作标识已用于其他请求');if(String(e).includes('REVISION_CONFLICT'))fail(409,'REVISION_CONFLICT','内容已更新，请刷新后重试');throw e;}
}
