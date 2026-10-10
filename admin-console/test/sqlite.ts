import { DatabaseSync } from 'node:sqlite';
export class TestDatabase {
 db=new DatabaseSync(':memory:');
 exec(sql:string){this.db.exec(sql);}
 prepare(sql:string){return new Statement(this,sql);}
 async batch(statements:Statement[]){this.db.exec('BEGIN IMMEDIATE');try{const result=statements.map(s=>s.execute());this.db.exec('COMMIT');return result;}catch(e){this.db.exec('ROLLBACK');throw e;}}
 close(){this.db.close();}
}
class Statement {
 values:any[]=[];constructor(private parent:TestDatabase,private sql:string){}
 bind(...values:any[]){this.values=values;return this;}
 execute(){const p=this.parent.db.prepare(this.sql);if(/^\s*(SELECT|PRAGMA)/i.test(this.sql)){return {results:p.all(...this.values),success:true,meta:{changes:0}};}const r=p.run(...this.values);return {results:[],success:true,meta:{changes:Number(r.changes)}};}
 async all(){return this.execute();}
 async first(){return this.parent.db.prepare(this.sql).get(...this.values)||null;}
 async run(){return this.execute();}
}
