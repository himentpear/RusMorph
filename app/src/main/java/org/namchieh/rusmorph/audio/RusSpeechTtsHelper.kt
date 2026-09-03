package org.namchieh.rusmorph.audio

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

class RusSpeechTtsHelper(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaPlayer: MediaPlayer? = null
    private var systemTts: TextToSpeech? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private val _isReady = MutableStateFlow(true)
    val isReady: StateFlow<Boolean> = _isReady

    private var systemTtsReady = false

    init {
        try {
            systemTts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val localeRu = Locale.forLanguageTag("ru-RU")
                    val res = systemTts?.setLanguage(localeRu)
                    if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                        systemTtsReady = true
                    } else {
                        val fallback = systemTts?.setLanguage(Locale.forLanguageTag("ru"))
                        if (fallback != TextToSpeech.LANG_MISSING_DATA && fallback != TextToSpeech.LANG_NOT_SUPPORTED) {
                            systemTtsReady = true
                        }
                    }
                }
            }

            systemTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                }
            })
        } catch (e: Exception) {
            Log.w("RusSpeechTtsHelper", "System TTS init exception: ${e.message}")
        }
    }

    fun speak(text: String, speed: Float = 1.0f) {
        val clean = text.replace("́", "").replace("`", "").trim()
        if (clean.isBlank()) return

        stop()
        _isSpeaking.value = true

        scope.launch {
            try {
                val cacheDir = File(context.cacheDir, "tts_cache").apply { mkdirs() }
                val hash = md5(clean)
                val cachedFile = File(cacheDir, "$hash.mp3")

                val audioFile = if (cachedFile.exists() && cachedFile.length() > 500) {
                    cachedFile
                } else {
                    withContext(Dispatchers.IO) {
                        downloadTtsAudio(clean, cachedFile)
                    }
                }

                if (audioFile != null && audioFile.exists() && audioFile.length() > 500) {
                    playAudioFile(audioFile, speed)
                } else if (systemTtsReady) {
                    playWithSystemTts(clean, speed)
                } else {
                    _isSpeaking.value = false
                    Toast.makeText(context, "发音获取失败，请连接网络后重试", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("RusSpeechTtsHelper", "Error speaking $clean", e)
                if (systemTtsReady) {
                    playWithSystemTts(clean, speed)
                } else {
                    _isSpeaking.value = false
                }
            }
        }
    }

    private fun playAudioFile(file: File, speed: Float = 1.0f) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    _isSpeaking.value = false
                    it.release()
                    mediaPlayer = null
                }
                setOnErrorListener { _, _, _ ->
                    _isSpeaking.value = false
                    mediaPlayer?.release()
                    mediaPlayer = null
                    true
                }
                prepare()
                try {
                    playbackParams = playbackParams.setSpeed(speed)
                } catch (_: Exception) {}
                start()
            }
        } catch (e: Exception) {
            Log.e("RusSpeechTtsHelper", "MediaPlayer error", e)
            _isSpeaking.value = false
        }
    }

    private fun playWithSystemTts(cleanText: String, speed: Float = 1.0f) {
        try {
            systemTts?.setSpeechRate(speed)
            systemTts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "rus_tts_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            _isSpeaking.value = false
        }
    }

    private fun downloadTtsAudio(text: String, destFile: File): File? {
        val endpoints = listOf(
            "https://namchieh.org/werus/tts?text=${URLEncoder.encode(text, "UTF-8")}",
            "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=ru&q=${URLEncoder.encode(text, "UTF-8")}",
        )

        for (endpoint in endpoints) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")
                    conn.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.length() > 500) {
                        tempFile.renameTo(destFile)
                        return destFile
                    } else {
                        tempFile.delete()
                    }
                }
            } catch (e: Exception) {
                Log.w("RusSpeechTtsHelper", "Failed fetching TTS from $endpoint: ${e.message}")
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}
        try {
            systemTts?.stop()
        } catch (_: Exception) {}
        _isSpeaking.value = false
    }

    fun shutdown() {
        stop()
        scope.cancel()
        try {
            systemTts?.shutdown()
            systemTts = null
        } catch (_: Exception) {}
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
