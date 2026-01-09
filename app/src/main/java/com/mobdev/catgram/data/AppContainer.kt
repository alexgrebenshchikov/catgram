package com.mobdev.catgram.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.mobdev.catgram.network.CatgramApiService
import com.mobdev.catgram.network.ImageUploadApiService
import com.mobdev.catgram.network.ImageUploader
import com.mobdev.catgram.ml.CatDetector
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

interface AppContainer {
    val catgramApiRepository: CatgramApiRepository
    val userPostsRepository: UserPostsRepository
    val favouritesRepository: FavouritesRepository
    val imageUploader: ImageUploader
    val catDetector: CatDetector
}

class DefaultAppContainer : AppContainer {
    private val json = Json { ignoreUnknownKeys = true }

    private val catgramApiRetrofitService: CatgramApiService by lazy {
        val baseUrl = "https://api.thecatapi.com/v1/"
        val retrofit: Retrofit = Retrofit.Builder()
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .baseUrl(baseUrl)
            .build()
        retrofit.create(CatgramApiService::class.java)
    }

    private val imageUploadApiRetrofitService: ImageUploadApiService by lazy {
        val baseUrl = "https://api.imgbb.com/"
        val retrofit: Retrofit = Retrofit.Builder()
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .baseUrl(baseUrl)
            .build()
        retrofit.create(ImageUploadApiService::class.java)
    }

    override val catgramApiRepository: CatgramApiRepository by lazy {
        NetworkCatgramApiRepository(catgramApiRetrofitService)
        //MockCatgramApiRepository()
    }
    override val userPostsRepository: UserPostsRepository by lazy {
        FirebaseUserPostsRepository()
    }
    override val favouritesRepository: FavouritesRepository by lazy {
        FirebaseFavouritesRepository()
    }

    override val imageUploader: ImageUploader by lazy {
        ImageUploader(imageUploadApiRetrofitService)
    }

    override val catDetector: CatDetector by lazy {
        CatDetector()
    }
}