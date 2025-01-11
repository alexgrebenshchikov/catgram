package com.mobdev.catgram.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.mobdev.catgram.network.CatgramApiService
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

interface AppContainer {
    val catgramRepository: CatgramRepository
}

class DefaultAppContainer : AppContainer {
    private val BASE_URL =
        "https://api.thecatapi.com/v1/"

    private val json = Json { ignoreUnknownKeys = true }

    private val retrofit: Retrofit = Retrofit.Builder()
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .baseUrl(BASE_URL)
        .build()

    private val retrofitService: CatgramApiService by lazy {
        retrofit.create(CatgramApiService::class.java)
    }

    override val catgramRepository: CatgramRepository by lazy {
        NetworkCatgramRepository(retrofitService)
    }

}