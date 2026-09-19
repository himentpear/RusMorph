package org.namchieh.rusmorph.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class SpeechWordDto(
    val text: String,
    val start_ms: Int,
    val end_ms: Int,
    val confidence: Double,
    val estimated: Boolean = false,
)

data class SpeechEvidenceDto(
    val scoring_method: String,
    val provider: String,
    val model: String,
    val model_version: String,
    val evidence_level: String,
    val has_real_word_timestamps: Boolean,
    val has_forced_alignment: Boolean,
    val has_phoneme_posterior: Boolean,
    val timestamps_estimated: Boolean,
)

data class TranscriptionDto(
    val success: Boolean,
    val language: String,
    val language_confidence: Double,
    val transcript: String,
    val normalized_transcript: String,
    val confidence: Double,
    val should_auto_search: Boolean,
    val duration_ms: Int,
    val speech_duration_ms: Int,
    val words: List<SpeechWordDto>,
    val alternatives: List<String>,
    val warnings: List<String>,
    val evidence: SpeechEvidenceDto? = null,
)

data class PronunciationWordDto(
    val text: String,
    val start_ms: Int,
    val end_ms: Int,
    val score: Double?,
    val confidence: Double,
    val status: String,
    val feedback_zh: String,
)

data class PronunciationDto(
    val success: Boolean,
    val target_text: String,
    val target_stressed_text: String,
    val recognized_text: String,
    val text_match_score: Double,
    val score_available: Boolean,
    val reason: String?,
    val overall_score: Double?,
    val pronunciation_accuracy: Double?,
    val stress_accuracy: Double?,
    val fluency_score: Double?,
    val completeness_score: Double?,
    val proficiency_level: String?,
    val analysis_confidence: Double,
    val words: List<PronunciationWordDto>,
    val summary_feedback_zh: String,
    val warnings: List<String>,
    val evidence: SpeechEvidenceDto? = null,
)

interface SpeechApi {
    @Multipart
    @POST("api/asr/transcribe")
    suspend fun transcribe(
        @Part audio: MultipartBody.Part,
        @Part("language") language: RequestBody,
        @Part("search_mode") searchMode: RequestBody,
    ): TranscriptionDto

    @Multipart
    @POST("api/pronunciation/analyze")
    suspend fun analyze(
        @Part audio: MultipartBody.Part,
        @Part("target_text") targetText: RequestBody,
        @Part("difficulty") difficulty: RequestBody,
    ): PronunciationDto
}
