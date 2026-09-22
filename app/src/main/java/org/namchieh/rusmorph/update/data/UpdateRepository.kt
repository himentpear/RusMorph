package org.namchieh.rusmorph.update.data

import com.google.gson.GsonBuilder
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.namchieh.rusmorph.update.model.AppUpdate
import org.namchieh.rusmorph.update.model.toValidatedUpdate
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class UpdateRepository(private val api: UpdateApi) {
    suspend fun latestStable(): AppUpdate =
        api.stableAndroidUpdate().toValidatedUpdate() ?: throw InvalidUpdateManifestException()

    companion object {
        fun create(baseUrl: String): UpdateRepository {
            require(baseUrl.startsWith("https://")) { "Update base URL must use HTTPS" }
            val client = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .build()
            val api = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
                .build()
                .create(UpdateApi::class.java)
            return UpdateRepository(api)
        }
    }
}

class InvalidUpdateManifestException : IllegalArgumentException("Invalid update manifest")
