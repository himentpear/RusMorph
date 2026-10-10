// Isolated runtime check: test signing keys and synthetic D1 data never enter production configuration.
import { Miniflare, convertV4MiniflareOptions } from 'miniflare';
import { generateKeyPair,exportJWK,SignJWT } from 'jose';
import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
const pair=await generateKeyPair('RS256');const publicJwk={...await exportJWK(pair.publicKey),kid:'smoke',alg:'RS256'};
let assetsServed=0;
const issuer='https://werus-runtime-test.cloudflareaccess.com',origin='https://werus-admin.namchieh.org';
const mf=new Miniflare(convertV4MiniflareOptions({modules:true,scriptPath:'.wrangler/smoke/index.js',compatibilityDate:'2026-10-10',bindings:{ADMIN_ORIGIN:origin,ACCESS_ISSUER:issuer,ACCESS_AUD:'runtime-aud',ADMIN_EMAILS:'himentpear@gmail.com'},d1Databases:{ADMIN_DB:'admin',USER_DB:'users',REVIEW_DB:'reviews',CONTENT_DB:'content'},r2Buckets:['CONTENT_BUCKET','REVIEW_AUDIO'],durableObjects:{PUBLISHER:{className:'ReleaseCoordinator',useSQLite:true}},serviceBindings:{ASSETS:async()=>{assetsServed++;return new Response('private asset');}},outboundService:async req=>{assert.equal(new URL(req.url).href,issuer+'/cdn-cgi/access/certs');return Response.json({keys:[publicJwk]});}}));
try{const db=await mf.getD1Database('ADMIN_DB');await db.exec((await readFile('migrations/0001_admin.sql','utf8')).replace(/\n/g,' '));
 const jwt=await new SignJWT({email:'himentpear@gmail.com'}).setProtectedHeader({alg:'RS256',kid:'smoke'}).setSubject('runtime-admin').setIssuer(issuer).setAudience('runtime-aud').setIssuedAt().setExpirationTime('5m').sign(pair.privateKey);
 for(const path of ['/','/assets/app.js','/api/admin/v1/me']){const r=await mf.dispatchFetch(origin+path);assert.equal(r.status,401);}
 assert.equal(assetsServed,0);assert.equal((await mf.dispatchFetch(origin+'/',{headers:{'cf-access-jwt-assertion':jwt}})).status,200);assert.equal(assetsServed,1);
 const me=await mf.dispatchFetch(origin+'/api/admin/v1/me',{headers:{'cf-access-jwt-assertion':jwt}});assert.equal(me.status,200);assert.equal((await me.json()).email,'himentpear@gmail.com');
 const wrongOrigin=await mf.dispatchFetch(origin+'/api/admin/v1/drafts',{method:'POST',headers:{'cf-access-jwt-assertion':jwt,origin:'https://other.test','content-type':'application/json'},body:'{}'});assert.equal(wrongOrigin.status,403);
 console.log('workerd runtime smoke: page/assets/API JWT verification, private assets and same-origin rejection passed');
}finally{await mf.dispose();}
