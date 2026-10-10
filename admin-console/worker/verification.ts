import { fail, hash } from './common';
import type { Env } from './env';
type Descriptor={key:string;sha256:string;size:number};
export async function releaseDescriptors(env:Env,contentVersion:string,dictionaryVersion:string){
 const read=async(key:string)=>{const obj=await env.CONTENT_BUCKET.get(key);if(!obj||obj.size>16_777_216)fail(503,'RESOURCE_MISSING','完整发布的资源缺失或过大');const bytes=new Uint8Array(await obj!.arrayBuffer());return {document:JSON.parse(new TextDecoder().decode(bytes)),descriptor:{key,sha256:await hash(bytes),size:bytes.length}};};
 const cm=await read(`content/${contentVersion}/manifest.json`),dm=await read(`dictionary/${dictionaryVersion}/manifest.json`);
 if(cm.document.schemaVersion!==1||cm.document.catalogVersion!==contentVersion||!Array.isArray(cm.document.components)||dm.document.schemaVersion!==2||dm.document.catalogVersion!==dictionaryVersion)fail(400,'INVALID_MANIFEST','构建目录格式或版本无效');
 const index=await read(dm.document.components.index.key);if(index.descriptor.sha256!==dm.document.components.index.sha256||index.descriptor.size!==dm.document.components.index.size)fail(400,'HASH_MISMATCH','词典索引与目录不一致');
 const descriptors:Descriptor[]=[cm.descriptor,dm.descriptor,...cm.document.components,...Object.values(dm.document.components) as Descriptor[],...index.document.flatMap((r:any)=>[r.detail,r.inflections])];
 const unique=new Map<string,Descriptor>();for(const d of descriptors){if(!d||!Number.isInteger(d.size)||d.size<1||d.size>16_777_216||!/^[a-f0-9]{64}$/.test(d.sha256)||(!d.key.startsWith('content/'+contentVersion+'/')&&!d.key.startsWith('dictionary/'+dictionaryVersion+'/'))||d.key.includes('..'))fail(400,'INVALID_REFERENCE','目录引用非法资源');const before=unique.get(d.key);if(before&&(before.sha256!==d.sha256||before.size!==d.size))fail(400,'INVALID_REFERENCE','资源引用相互冲突');unique.set(d.key,d);}
 if(index.document.length!==dm.document.entryCount||new Set(index.document.map((r:any)=>r.id)).size!==dm.document.entryCount)fail(400,'INVALID_REFERENCE','词典条目计数或稳定 ID 无效');return [...unique.values()];
}
export async function verifyChunk(env:Env,descriptors:Descriptor[],offset:number,count=8){for(const d of descriptors.slice(offset,offset+count)){const obj=await env.CONTENT_BUCKET.get(d.key);if(!obj||obj.size!==d.size||await hash(new Uint8Array(await obj.arrayBuffer()))!==d.sha256)fail(503,'HASH_MISMATCH','发布资源读回校验失败');}}
