import type { Env } from './env';
import { atomic, fail, hash, operation, revision } from './common';
import { z } from 'zod';
import { unzipSync } from 'fflate';
const mime:Record<string,string>={png:'image/png',jpg:'image/jpeg',webp:'image/webp',mp3:'audio/mpeg',wav:'audio/wav',zip:'application/zip'};
export function validateResource(key:string,contentType:string,bytes:Uint8Array){
 if(!/^admin-resources\/[a-zA-Z0-9_-]{1,80}\/[a-zA-Z0-9_-]{1,120}\.(png|jpg|webp|mp3|wav|zip)$/.test(key))fail(400,'INVALID_PATH','路径必须位于 admin-resources/版本/文件，且使用支持的扩展名');
 const ext=key.split('.').pop()!;if(mime[ext]!==contentType)fail(400,'INVALID_FORMAT','类型与扩展名不匹配');if(!bytes.length||bytes.length>(ext==='zip'?32:ext==='mp3'||ext==='wav'?16:8)*1024*1024)fail(413,'INVALID_SIZE','文件大小超出限制');
 const hex=Array.from(bytes.slice(0,12),x=>x.toString(16).padStart(2,'0')).join('');
 const valid=ext==='png'?hex.startsWith('89504e470d0a1a0a'):ext==='jpg'?hex.startsWith('ffd8ff'):ext==='webp'?hex.startsWith('52494646')&&hex.slice(16)==='57454250':ext==='wav'?hex.startsWith('52494646')&&hex.slice(16)==='57415645':ext==='mp3'?hex.startsWith('494433')||(bytes[0]===255&&(bytes[1]&224)===224):hex.startsWith('504b0304');if(!valid)fail(400,'INVALID_SIGNATURE','文件签名与声明的格式不一致');
 if(ext==='zip'){
  let total=0,count=0,audio=0;
  try{const entries=unzipSync(bytes,{filter:f=>{total+=f.originalSize;count++;if(count>2000||total>32*1024*1024||f.originalSize>16*1024*1024||!f.name||f.name.includes('..')||f.name.startsWith('/')||f.name.includes('\\')||!/^([a-zA-Z0-9_-]+\/)*[a-zA-Z0-9_.-]+\.(mp3|wav|json)$/.test(f.name))throw new Error('INVALID_ARCHIVE');return true;}});
   for(const [name,value] of Object.entries(entries)){const extension=name.split('.').pop()!;if(extension==='json'){JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(value));}else{validateResource('admin-resources/archive/'+name.split('/').pop(),mime[extension],value);audio++;}}
   if(!audio)throw new Error('EMPTY_AUDIO_ARCHIVE');
  }catch{fail(400,'INVALID_ARCHIVE','音频包包含无效路径、类型、格式或超限文件');}
 }
}
export async function upload(env:Env,actor:string,request:Request){const key=request.headers.get('x-resource-key')||'',op=operation.parse(request.headers.get('idempotency-key'));const length=Number(request.headers.get('content-length'));if(!length||length>32*1024*1024)fail(413,'INVALID_SIZE','需要有效文件长度，最大 32 MiB');const reader=request.body?.getReader();if(!reader)fail(400,'EMPTY_FILE','文件为空');let size=0;const chunks:Uint8Array[]=[];try{for(;;){const item=await reader!.read();if(item.done)break;size+=item.value.length;if(size>32*1024*1024){await reader!.cancel();fail(413,'INVALID_SIZE','文件过大');}chunks.push(item.value);}}finally{reader!.releaseLock();}const bytes=new Uint8Array(size);let offset=0;for(const c of chunks){bytes.set(c,offset);offset+=c.length;}const type=request.headers.get('content-type')||'';validateResource(key,type,bytes);const digest=await hash(bytes);const saved=await env.CONTENT_BUCKET.head(key);if(saved&&saved.customMetadata?.sha256!==digest)fail(409,'IMMUTABLE_RESOURCE','已有资源不能覆盖');await env.CONTENT_BUCKET.put(key,bytes,{onlyIf:{etagDoesNotMatch:'*'},httpMetadata:{contentType:type},customMetadata:{sha256:digest}});const check=await env.CONTENT_BUCKET.get(key);if(!check||await hash(new Uint8Array(await check.arrayBuffer()))!==digest)fail(503,'UPLOAD_VERIFY_FAILED','上传校验失败');
 const result=await atomic(env.ADMIN_DB,actor,'resource.upload',key,op,{digest,size,type},env.ADMIN_DB.prepare('INSERT INTO resources(key,sha256,bytes,content_type,created_at,created_by) VALUES(?,?,?,?,?,?) ON CONFLICT(key) DO UPDATE SET key=excluded.key WHERE sha256=excluded.sha256').bind(key,digest,size,type,new Date().toISOString(),actor));return {key,sha256:digest,...result};}
export async function remove(env:Env,actor:string,key:string,input:unknown){const b=z.object({operationId:operation,revision}).strict().parse(input);if(await env.ADMIN_DB.prepare('SELECT 1 FROM resource_refs WHERE resource_key=? LIMIT 1').bind(key).first())fail(409,'RESOURCE_REFERENCED','正式版本引用的资源不能删除');
 // Keep physical bytes immutable: remove from the editorial inventory only. Release verification can never race an R2 deletion.
 return atomic(env.ADMIN_DB,actor,'resource.archive',key,b.operationId,b,env.ADMIN_DB.prepare('DELETE FROM resources WHERE key=? AND revision=? AND NOT EXISTS(SELECT 1 FROM resource_refs WHERE resource_key=?)').bind(key,b.revision,key));}
