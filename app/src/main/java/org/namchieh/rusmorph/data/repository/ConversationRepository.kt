package org.namchieh.rusmorph.data.repository

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.namchieh.rusmorph.BuildConfig
import org.namchieh.rusmorph.data.remote.EndpointPolicy
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ConversationHistory(val role: String, val content: String)
data class ConversationRequest(val sessionId: String, val scenario: String, val message: String, val history: List<ConversationHistory>)
data class ConversationCorrection(val hasError: Boolean = false, val original: String? = null, val corrected: String? = null, val explanationZh: String? = null)

sealed interface ConversationEvent {
    data class Token(val text: String) : ConversationEvent
    data class Correction(val value: ConversationCorrection) : ConversationEvent
    data object Done : ConversationEvent
}

class ConversationRepository(private val baseUrl: String) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .build()

    fun converse(request: ConversationRequest): Flow<ConversationEvent> = flow {
        val base = EndpointPolicy.normalized(baseUrl, BuildConfig.ALLOW_CLEARTEXT_ENDPOINTS)
            ?: throw IOException("AI 对话服务尚未配置")
        val httpRequest = Request.Builder()
            .url(base + "api/conversation")
            .post(gson.toJson(request).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "text/event-stream")
            .build()
        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) throw IOException("AI 对话服务暂不可用（${response.code}）")
            if (response.header("Content-Type")?.startsWith("text/event-stream") != true) {
                throw IOException("AI 对话服务返回了无效格式")
            }
            val source = response.body?.source() ?: throw IOException("AI 对话服务没有返回内容")
            var event = ""
            var data = StringBuilder()
            var done = false
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isEmpty()) {
                    if (data.isNotEmpty()) {
                        val payload = data.toString()
                        when (event) {
                            "token" -> emit(ConversationEvent.Token(JsonParser.parseString(payload).asJsonObject.get("text").asString))
                            "correction" -> emit(ConversationEvent.Correction(gson.fromJson(payload, ConversationCorrection::class.java)))
                            "error" -> throw IOException("AI 回复中断，请重试")
                            "done" -> { emit(ConversationEvent.Done); done = true }
                        }
                    }
                    event = ""
                    data = StringBuilder()
                    if (done) break
                } else if (line.startsWith("event:")) event = line.substringAfter(':').trim()
                else if (line.startsWith("data:")) data.append(line.substringAfter(':').trimStart())
            }
            if (!done) throw IOException("AI 回复中途断开，请重试")
        }
    }.flowOn(Dispatchers.IO)
}
