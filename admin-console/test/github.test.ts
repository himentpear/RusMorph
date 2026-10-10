import { describe,it,expect,vi } from 'vitest';
import { Github } from '../worker/github';
import type { Env } from '../worker/env';
describe('immutable source submission',()=>{
 const task='123',sha='a'.repeat(40),branch='codex/admin-release-'+task,path='data-source/admin-drafts/'+task+'.json',draft={document:{text:'reviewed'}};
 function github(value=JSON.stringify(draft,null,2)+'\n',prs:any[]=[]){const gh=new Github({GITHUB_BASE_BRANCH:'main',GITHUB_REPOSITORY:'himentpear/RusMorph'} as Env);vi.spyOn(gh,'request').mockImplementation(async(p,method)=>{if(p.startsWith('/git/ref/heads/'))return {object:{sha}};if(p.startsWith('/contents/'))return {content:btoa(value)};if(p.startsWith('/commits/'))return {sha,parents:[{}],files:[{filename:path}],commit:{message:'content: reviewed administrative draft '+task}};if(p.startsWith('/pulls?'))return prs;if(p==='/pulls'&&method==='POST')return {number:1,head:{sha},base:{ref:'main'},state:'open'};throw new Error(p);});return gh;}
 it('recovers the PR creation step using the actual commit, not a file blob SHA',async()=>{const gh=github();const r=await gh.submit(task,draft);expect(r.commit).toBe(sha);expect(r.pr.number).toBe(1);expect(gh.request).toHaveBeenCalledWith('/pulls','POST',expect.objectContaining({head:branch}));});
 it('refuses an existing branch with different frozen content',async()=>{await expect(github('changed').submit(task,draft)).rejects.toMatchObject({code:'SOURCE_CHANGED'});});
 it('does not reuse a closed or changed PR',async()=>{await expect(github(undefined,[{base:{ref:'main'},head:{sha},state:'closed'}]).submit(task,draft)).rejects.toMatchObject({code:'SOURCE_CHANGED'});});
 it('checks all check-run pages before allowing merge confirmation',async()=>{const gh=github();vi.mocked(gh.request).mockImplementation(async p=>p.endsWith('&page=1')?{total_count:101,check_runs:Array.from({length:100},()=>({status:'completed',conclusion:'success',name:'other'}))}:{total_count:101,check_runs:[{status:'completed',conclusion:'failure',name:'admin-content-validation'}]});expect(await gh.checksPassed(sha)).toBe(false);});
});
