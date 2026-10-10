import { WorkerEntrypoint } from 'cloudflare:workers';
import type { Env } from './providers/AgentProvider';
export class OperationsRpc extends WorkerEntrypoint<Env>{
 async status(){return {available:true,asrModel:this.env.SPEECH_ASR_MODEL||'@cf/openai/whisper-large-v3-turbo',replyModel:'@cf/zai-org/glm-4.7-flash',ttsModel:this.env.SPEECH_TTS_MODEL||'gemini-3.8-flash-tts',ttsVoice:this.env.SPEECH_TTS_VOICE||'Kore',liveModel:'gemini-3.8-live',liveVoice:'Kore',liveDeploymentEnabled:this.env.LIVE_VOICE_ENABLED==='true',liveVerified:this.env.LIVE_VOICE_VERIFIED==='true',geminiKeyConfigured:Boolean(this.env.GEMINI_API_KEY),maxSessionSeconds:600};}
}
