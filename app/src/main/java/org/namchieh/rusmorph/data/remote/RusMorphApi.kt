package org.namchieh.rusmorph.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Reserved read contract for remotely managed word books; the app remains asset-first. */
interface RusMorphApi {
    @GET("v1/wordbooks")
    suspend fun wordBooks(@Query("cursor") cursor: String? = null): WordBookListDto

    @GET("v1/wordbooks/{wordBookId}")
    suspend fun wordBook(@Path("wordBookId") wordBookId: String): WordBookDto

    @GET("v1/wordbooks/{wordBookId}/lessons")
    suspend fun lessons(@Path("wordBookId") wordBookId: String, @Query("cursor") cursor: String? = null): WordBookLessonListDto

    @GET("v1/wordbooks/{wordBookId}/lessons/{lessonId}")
    suspend fun lesson(@Path("wordBookId") wordBookId: String, @Path("lessonId") lessonId: String): WordBookLessonDto

    @GET("v1/wordbooks/{wordBookId}/lessons/{lessonId}/words")
    suspend fun lessonWords(@Path("wordBookId") wordBookId: String, @Path("lessonId") lessonId: String, @Query("cursor") cursor: String? = null): LessonWordListDto

    @GET("v1/wordbooks/{wordBookId}/lessons/{lessonId}/dialogues")
    suspend fun lessonDialogues(@Path("wordBookId") wordBookId: String, @Path("lessonId") lessonId: String, @Query("cursor") cursor: String? = null): LessonDialogueListDto

    @GET("v1/wordbooks/{wordBookId}/lessons/{lessonId}/texts")
    suspend fun lessonTexts(@Path("wordBookId") wordBookId: String, @Path("lessonId") lessonId: String, @Query("cursor") cursor: String? = null): LessonTextListDto
}

object RusMorphApiFactory {
    fun create(baseUrl: String): RusMorphApi =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RusMorphApi::class.java)
}
