import { generateReply } from '../ai/conversationService';
type Runner=Parameters<typeof generateReply>[0];
export type ModelUsage={calls:number;inputUnits:number|null;outputUnits:number|null;unit:'tokens'};
export async function withModelUsage<T extends object>(ai:Runner,work:(runner:Runner)=>Promise<T>):Promise<T&{modelUsage:ModelUsage}>{
 const usage:ModelUsage={calls:0,inputUnits:0,outputUnits:0,unit:'tokens'};
 const runner:Runner={async run(...args){usage.calls++;let value:unknown;
  try{value=await ai.run(...args);}catch(error){usage.inputUnits=null;usage.outputUnits=null;throw error;}
  const result=value as {usage?:{prompt_tokens?:number;completion_tokens?:number}}|null;
  const add=(current:number|null,n:unknown)=>current!==null&&typeof n==='number'&&Number.isFinite(n)&&n>=0?current+n:null;
  usage.inputUnits=add(usage.inputUnits,result?.usage?.prompt_tokens);usage.outputUnits=add(usage.outputUnits,result?.usage?.completion_tokens);
  return value;
 }};
 const result=await work(runner);return {...result,modelUsage:{...usage}};
}
