import { describe,it,expect } from 'vitest';
import { generateKeyPairSync } from 'node:crypto';
// @ts-ignore local setup utility is plain JavaScript
import { normalizeKey } from '../scripts/verify-github.mjs';
// @ts-ignore local setup utility is plain JavaScript
import { validateOrganization,validatePolicies } from '../scripts/access-policy.mjs';
describe('secure paired setup',()=>{
 it('normalizes GitHub RSA PEM without an external converter',()=>{const key=generateKeyPairSync('rsa',{modulusLength:2048}).privateKey.export({format:'pem',type:'pkcs1'});expect(normalizeKey(key)).toContain('BEGIN PRIVATE KEY');});
 it('rejects the wrong team or an uninitialized Access organization',()=>{expect(()=>validateOrganization({},'werus.cloudflareaccess.com')).toThrow();expect(()=>validateOrganization({auth_domain:'other.cloudflareaccess.com'},'werus.cloudflareaccess.com')).toThrow();expect(validateOrganization({auth_domain:'werus.cloudflareaccess.com'},'werus.cloudflareaccess.com')).toBe('https://werus.cloudflareaccess.com');});
 it.each(['bypass','non_identity','allow'])('does not adopt a broad existing %s policy',decision=>{expect(()=>validatePolicies([{decision,include:[{everyone:{}}]}])).toThrow();});
 it('allows only the exact administrator email policy',()=>{expect(()=>validatePolicies([{decision:'allow',include:[{email:{email:'himentpear@gmail.com'}}],exclude:[],require:[]}])).not.toThrow();});
});
