import { describe,it,expect } from 'vitest';
import { zipSync } from 'fflate';
import { validateResource } from '../worker/resources';
const wav=new TextEncoder().encode('RIFF1234WAVEvalid-audio');
describe('uploaded audio archives',()=>{
 it('rejects traversal and unsupported nested objects',()=>{for(const name of ['../record.wav','audio/script.exe'])expect(()=>validateResource('admin-resources/test/audio.zip','application/zip',zipSync({[name]:wav}))).toThrow();});
 it('checks contained audio signatures rather than just ZIP magic',()=>{expect(()=>validateResource('admin-resources/test/audio.zip','application/zip',zipSync({'record.wav':new TextEncoder().encode('not wave')}))).toThrow();expect(()=>validateResource('admin-resources/test/audio.zip','application/zip',zipSync({'record.wav':wav,'manifest.json':new TextEncoder().encode('{}')}))).not.toThrow();});
});
