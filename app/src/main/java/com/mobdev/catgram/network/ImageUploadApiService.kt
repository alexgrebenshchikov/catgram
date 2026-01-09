package com.mobdev.catgram.network

import kotlinx.serialization.Serializable
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Query

@Serializable
data class ImageUploadResponse(
    val success: Boolean,
    val status: Int,
    val data: ImageUploadData? = null
)

@Serializable
data class ImageUploadData(
    val url: String,
)

interface ImageUploadApiService {
    @FormUrlEncoded
    @POST("1/upload")
    suspend fun uploadImage(
        @Query("key") apiKey: String,
        @Field("image") imageBase64: String
    ): ImageUploadResponse
}

