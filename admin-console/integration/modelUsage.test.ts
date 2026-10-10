import { describe,it,expect } from 'vitest';
import { withModelUsage } from '../src/rpc/modelUsage';
describe('private numeric model metadata',()=>{
 it('aggregates retry usage without including model outputs',async()=>{const ai={run:async()=>({response:'sensitive text',usage:{prompt_tokens:8,completion_tokens:3}})};const result=await withModelUsage(ai,async runner=>{await runner.run('glm',{messages:[]});await runner.run('glm',{messages:[]});return {reply:'filtered reply'};});expect(result.modelUsage).toEqual({calls:2,inputUnits:16,outputUnits:6,unit:'tokens'});expect(JSON.stringify(result.modelUsage)).not.toContain('text');});
 it('does not turn missing upstream usage into zero cost',async()=>{const ai={run:async()=>({response:'filtered'})};const result=await withModelUsage(ai,async runner=>{await runner.run('glm',{messages:[]});return {};});expect(result.modelUsage).toMatchObject({calls:1,inputUnits:null,outputUnits:null});});
 it('identifies deterministic corrections that make no model call',async()=>{const result=await withModelUsage({run:async()=>{throw new Error('must not call');}},async()=>({status:'ready'}));expect(result.modelUsage.calls).toBe(0);});
});
