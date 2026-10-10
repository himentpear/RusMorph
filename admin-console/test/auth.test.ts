import { describe,it,expect,beforeAll } from 'vitest';
import { generateKeyPair, SignJWT, exportJWK, createLocalJWKSet } from 'jose';
import { authenticate,sameOrigin,secure } from '../worker/auth';
const env={ACCESS_ISSUER:'https://werus.cloudflareaccess.com',ACCESS_AUD:'admin-aud',ADMIN_EMAILS:'himentpear@gmail.com'};
let privateKey:CryptoKey,key:any;
beforeAll(async()=>{const pair=await generateKeyPair('RS256');privateKey=pair.privateKey;const publicJwk=await exportJWK(pair.publicKey);key=createLocalJWKSet({keys:[{...publicJwk,kid:'test',alg:'RS256'}]});});
async function request(extra:any={}){const token=await new SignJWT({email:'himentpear@gmail.com',...extra}).setProtectedHeader({alg:'RS256',kid:'test'}).setSubject('administrator').setIssuedAt().setIssuer(extra.iss||env.ACCESS_ISSUER).setAudience(extra.aud||env.ACCESS_AUD).setExpirationTime(extra.exp||'5m').sign(privateKey);return new Request('https://werus-admin.namchieh.org/',{headers:{'cf-access-jwt-assertion':token}});}
describe('Access trust boundary',()=>{
 it('verifies signature, issuer, audience and allowlist',async()=>expect(await authenticate(await request(),env,key)).toMatchObject({email:'himentpear@gmail.com',role:'admin'}));
 it('rejects no JWT even with spoofed email header',async()=>expect(authenticate(new Request('https://werus-admin.namchieh.org/',{headers:{'cf-access-authenticated-user-email':'himentpear@gmail.com'}}),env,key)).rejects.toMatchObject({status:401}));
 it('denies every user until issuer and audience configured',async()=>expect(authenticate(await request(),{...env,ACCESS_AUD:''},key)).rejects.toMatchObject({status:503}));
 it('denies an empty admin allowlist',async()=>expect(authenticate(await request(),{...env,ADMIN_EMAILS:''},key)).rejects.toMatchObject({status:403}));
 it.each([{email:'member@example.com'},{iss:'https://other.cloudflareaccess.com'},{aud:'other-aud'},{exp:1}])('rejects non-admin and invalid claims %j',async extra=>expect(authenticate(await request(extra),env,key)).rejects.toThrow());
 it('rejects a forged signature',async()=>{const req=await request();const token=req.headers.get('cf-access-jwt-assertion')!;const forged=token.slice(0,-30)+'A'.repeat(30);await expect(authenticate(new Request(req.url,{headers:{'cf-access-jwt-assertion':forged}}),env,key)).rejects.toMatchObject({status:401});});
 it('requires same origin and rejects missing origin for writes',()=>{const e={ADMIN_ORIGIN:'https://werus-admin.namchieh.org'};expect(()=>sameOrigin(new Request(e.ADMIN_ORIGIN,{method:'POST'}),e)).toThrow();expect(()=>sameOrigin(new Request(e.ADMIN_ORIGIN,{method:'POST',headers:{origin:'https://evil.example'}}),e)).toThrow();expect(()=>sameOrigin(new Request(e.ADMIN_ORIGIN,{method:'POST',headers:{origin:e.ADMIN_ORIGIN,'sec-fetch-site':'same-origin'}}),e)).not.toThrow();});
 it('disables browser caching and framing for private assets',()=>{const r=secure(new Response('private'));expect(r.headers.get('cache-control')).toBe('no-store');expect(r.headers.get('content-security-policy')).toContain("frame-ancestors 'none'");});
});
