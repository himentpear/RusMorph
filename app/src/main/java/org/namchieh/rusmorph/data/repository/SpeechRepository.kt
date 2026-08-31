package org.namchieh.rusmorph.data.repository

import com.google.gson.JsonParser
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.namchieh.rusmorph.data.remote.PronunciationDto
import org.namchieh.rusmorph.data.remote.SpeechApi
import org.namchieh.rusmorph.data.remote.TranscriptionDto
import retrofit2.Retrofit
import retrofit2.HttpException
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class SpeechRepository private constructor(private val api: SpeechApi?) {
    val isConfigured: Boolean get() = api != null

    suspend fun transcribe(file: File): TranscriptionDto {
        val service = checkNotNull(api) { "语音服务尚未配置" }
        return speechCall { service.transcribe(
            MultipartBody.Part.createFormData(
                "audio", file.name, file.asRequestBody("audio/mp4".toMediaType())
            ),
            "ru".toRequestBody("text/plain".toMediaType()),
            "true".toRequestBody("text/plain".toMediaType()),
        ) }
    }

    suspend fun analyze(file: File, targetText: String, difficulty: String): PronunciationDto {
        val service = checkNotNull(api) { "语音服务尚未配置" }
        return speechCall { service.analyze(
            MultipartBody.Part.createFormData(
                "audio", file.name, file.asRequestBody("audio/mp4".toMediaType())
            ),
            targetText.toRequestBody("text/plain".toMediaType()),
            difficulty.toRequestBody("text/plain".toMediaType()),
        ) }
    }

    private suspend fun <T> speechCall(block: suspend () -> T): T = try {
        block()
    } catch (exception: HttpException) {
        val backendMessage = runCatching {
            val root = JsonParser.parseString(exception.response()?.errorBody()?.string()).asJsonObject
            root.get("message")?.asString
                ?: root.getAsJsonObject("error")?.get("message")?.asString
        }.getOrNull()
        throw IllegalStateException(
            backendMessage ?: "语音服务返回错误（HTTP ${exception.code()}）",
            exception,
        )
    }

    companion object {
        fun create(baseUrl: String): SpeechRepository {
            val normalized = baseUrl.trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?.let { if (it.endsWith('/')) it else "$it/" }
            val api = normalized?.let {
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .callTimeout(35, TimeUnit.SECONDS)
                    .build()
                Retrofit.Builder()
                    .baseUrl(it)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(SpeechApi::class.java)
            }
            return SpeechRepository(api)
        }
    }
}
