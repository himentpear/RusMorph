package org.namchieh.rusmorph.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

interface RusMorphApi

object RusMorphApiFactory {
    fun create(baseUrl: String): RusMorphApi =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RusMorphApi::class.java)
}

