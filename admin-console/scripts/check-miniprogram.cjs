const path=require('node:path');
const root=path.resolve(__dirname,'../../apps/miniprogram');
const compiler=process.argv[2]?require(path.resolve(process.argv[2])):require('miniprogram-compiler');
for(const [name,method] of [['WXML','wxmlToJs'],['WXSS','wxssToJs']]){
 const result=compiler[method](root);
 if(typeof result!=='string'||!result.trim())throw new Error(name+' compiler returned no code');
 console.log(name+' official compiler passed');
}
