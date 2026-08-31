package org.namchieh.rusmorph.data.remote

import org.namchieh.rusmorph.agent.AgentRequest
import org.namchieh.rusmorph.agent.AgentResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AgentApi {
    @GET("health")
    suspend fun health(): Response<HealthDto>

    @POST("v1/ask")
    suspend fun ask(@Body request: AgentRequest): Response<AgentResponse>

    @POST("v1/route")
    suspend fun route(@Body request: RouteRequestDto): Response<RouteResponseDto>

    @POST("v1/compose")
    suspend fun compose(@Body request: ComposeRequestDto): Response<ComposeResponseDto>

    @POST("v1/pronunciation-example")
    suspend fun pronunciationExample(
        @Body request: PronunciationExampleRequestDto,
    ): Response<PronunciationExampleDto>
}

data class HealthDto(val status: String, val provider: String, val configured: Boolean, val model: String)

data class PronunciationExampleRequestDto(
    val conversationId: String,
    val difficulty: String,
    val topic: String?,
)

data class PronunciationExampleDto(
    val russian: String,
    val chinese: String,
    val evidenceType: String,
)
