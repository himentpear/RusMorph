import { describe,it,expect,vi } from 'vitest';
import { requireService, operationalLive, observed, registryVersions } from '../src/OperationsService';
import type { Env } from '../src/providers/AgentProvider';
describe('private operations controls',()=>{
 it('fails closed when the control lookup fails',async()=>{const env={OPERATIONS:{controls:async()=>{throw new Error('D1 offline');}}} as unknown as Env;await expect(requireService(env,'conversation')).rejects.toMatchObject({code:'OPERATIONS_UNAVAILABLE'});});
 it('keeps independently deployed legacy backends compatible',async()=>{await expect(requireService({} as Env,'synthesis')).resolves.toBeUndefined();});
 it('global conversation switch stops all dialogue functions',async()=>{const env={OPERATIONS:{controls:async()=>({conversation:false,synthesis:true})}} as unknown as Env;await expect(requireService(env,'synthesis')).rejects.toMatchObject({code:'SERVICE_DISABLED'});});
 it('requires both deployment and verification for managed Live',async()=>{const env={LIVE_VOICE_ENABLED:'true',OPERATIONS:{controls:async()=>({conversation:true,live:true})}} as unknown as Env;expect(await operationalLive(env)).toBe(false);env.LIVE_VOICE_VERIFIED='true';expect(await operationalLive(env)).toBe(true);});
 it('only sends allowed metadata, no returned learner text',async()=>{const recordEvent=vi.fn(async()=>({}));const env={OPERATIONS:{recordEvent}} as unknown as Env;const r=await observed(env,'user-id','reply','glm',async()=>({reply:'learner-visible text'}));expect(r.reply).toContain('text');const event=recordEvent.mock.calls[0][0] as any;expect(event.accountHash).toMatch(/^[a-f0-9]{64}$/);expect(JSON.stringify(event)).not.toContain('user-id');expect(JSON.stringify(event)).not.toContain('learner');expect(event.inputUnits).toBeNull();});
 it('registry failures never fall through to independently mutable R2 pointers',async()=>{const env={OPERATIONS:{activeVersions:async()=>{throw new Error('offline');}}} as unknown as Env;await expect(registryVersions(env)).rejects.toMatchObject({code:'RELEASE_REGISTRY_UNAVAILABLE'});});
});
