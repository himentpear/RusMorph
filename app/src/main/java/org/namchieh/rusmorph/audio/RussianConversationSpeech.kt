package org.namchieh.rusmorph.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class RussianConversationSpeech(
    context: Context,
    private val onText: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val recognizer: SpeechRecognizer? = try {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    } catch (_: Exception) { null }
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onListening(true) }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { onListening(false) }
            override fun onError(error: Int) { onListening(false); onError("语音识别失败，请重试或使用文字输入") }
            override fun onResults(results: Bundle?) {
                onListening(false)
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onText)
                    ?: onError("没有识别到俄语，请重试")
            }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.forLanguageTag("ru-RU"))
                    ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                    if (!ttsReady) onError("设备未安装俄语语音，文字对话仍可使用")
                } else onError("俄语朗读暂不可用，文字对话仍可使用")
            }
        } catch (_: Exception) { onError("俄语朗读暂不可用，文字对话仍可使用") }
    }

    fun listen() {
        if (recognizer == null) { onError("设备不支持语音识别，请使用文字输入"); return }
        try {
            onListening(true)
            recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        } catch (_: Exception) { onListening(false); onError("无法启动语音识别，请使用文字输入") }
    }

    fun stopListening() { try { recognizer?.stopListening() } catch (_: Exception) {} }
    fun speak(text: String) {
        if (!ttsReady) { onError("设备未安装俄语语音，文字对话仍可使用"); return }
        try { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "conversation-${System.currentTimeMillis()}") }
        catch (_: Exception) { onError("俄语朗读暂不可用") }
    }
    fun stopSpeaking() { try { tts?.stop() } catch (_: Exception) {} }
    fun close() { stopSpeaking(); recognizer?.destroy(); tts?.shutdown() }
}
