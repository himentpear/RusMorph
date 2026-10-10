import { createRemoteJWKSet, jwtVerify, type JWTVerifyGetKey } from 'jose';
import type { Env } from './env';
import { fail } from './common';
const keysets=new Map<string,JWTVerifyGetKey>();
export async function authenticate(request:Request,env:Pick<Env,'ACCESS_ISSUER'|'ACCESS_AUD'|'ADMIN_EMAILS'>,key?:JWTVerifyGetKey){
 if(!env.ACCESS_ISSUER||!env.ACCESS_AUD)fail(503,'ACCESS_NOT_CONFIGURED','管理台登录尚未配置');
 if(!/^https:\/\/[a-z0-9-]+\.cloudflareaccess\.com$/.test(env.ACCESS_ISSUER))fail(503,'ACCESS_NOT_CONFIGURED','登录配置无效');
 const token=request.headers.get('cf-access-jwt-assertion');if(!token)fail(401,'LOGIN_REQUIRED','请通过 Cloudflare Access 登录');
 if(!key){key=keysets.get(env.ACCESS_ISSUER);if(!key){key=createRemoteJWKSet(new URL(env.ACCESS_ISSUER+'/cdn-cgi/access/certs'));keysets.set(env.ACCESS_ISSUER,key);}}
 let email='';let subject='';try{const {payload}=await jwtVerify(token!,key,{issuer:env.ACCESS_ISSUER,audience:env.ACCESS_AUD,algorithms:['RS256'],requiredClaims:['exp','iat','sub','email']});email=typeof payload.email==='string'?payload.email.toLowerCase():'';subject=String(payload.sub||'');}catch{fail(401,'INVALID_LOGIN','登录已失效，请重新登录');}
 const allow=new Set(env.ADMIN_EMAILS.split(',').map(x=>x.trim().toLowerCase()).filter(Boolean));if(!allow.has(email)||!subject)fail(403,'ADMIN_REQUIRED','此账号没有管理台权限');
 return {email,subject,role:'admin' as const};
}
export function sameOrigin(request:Request,env:Pick<Env,'ADMIN_ORIGIN'>){
 if(new URL(request.url).origin!==env.ADMIN_ORIGIN||request.headers.get('origin')!==env.ADMIN_ORIGIN)fail(403,'ORIGIN_REQUIRED','仅允许管理台同源操作');
 const site=request.headers.get('sec-fetch-site');if(site&&site!=='same-origin')fail(403,'ORIGIN_REQUIRED','跨站操作被拒绝');
}
export function secure(response:Response){const headers=new Headers(response.headers);headers.set('cache-control','no-store');headers.set('content-security-policy',"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; media-src 'self' blob:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");headers.set('x-content-type-options','nosniff');headers.set('referrer-policy','no-referrer');headers.set('x-frame-options','DENY');headers.set('permissions-policy','microphone=(), camera=(), geolocation=()');return new Response(response.body,{status:response.status,headers});}
