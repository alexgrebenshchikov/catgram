package com.mobdev.catgram.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.mobdev.catgram.auth.AuthProvider
import com.mobdev.catgram.auth.FirebaseAuthProvider
import com.mobdev.catgram.coroutines.DefaultDispatcherProvider
import com.mobdev.catgram.coroutines.DispatcherProvider
import com.mobdev.catgram.ml.CatDetector
import com.mobdev.catgram.network.CatgramApiService
import com.mobdev.catgram.network.ImageUploadApiService
import com.mobdev.catgram.network.ImageUploader
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

interface AppContainer {
    val catgramApiRepository: CatgramApiRepository
    val userPostsRepository: UserPostsRepository
    val favouritesRepository: FavouritesRepository
    val imageUploader: ImageUploader
    val catDetector: CatDetector
    val authProvider: AuthProvider
    val feedDataStore: DataStore<Preferences>
    val dispatcherProvider: DispatcherProvider
}

class DefaultAppContainer(private val context: Context) : AppContainer {
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
        FirebaseUserPostsRepository(authProvider, context)
    }
    override val favouritesRepository: FavouritesRepository by lazy {
        FirebaseFavouritesRepository(authProvider)
    }

    override val imageUploader: ImageUploader by lazy {
        ImageUploader(imageUploadApiRetrofitService)
    }

    override val catDetector: CatDetector by lazy {
        CatDetector()
    }

    override val authProvider: AuthProvider by lazy {
        FirebaseAuthProvider()
    }

    override val feedDataStore: DataStore<Preferences> by lazy {
        context.feedDataStore
    }
    override val dispatcherProvider: DispatcherProvider by lazy {
        DefaultDispatcherProvider()
    }

    companion object {
        private val Context.feedDataStore: DataStore<Preferences> by preferencesDataStore("feed-filter")
    }
}
