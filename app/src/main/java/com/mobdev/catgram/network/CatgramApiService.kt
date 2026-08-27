package com.mobdev.catgram.network

import retrofit2.http.GET
import retrofit2.http.Query

interface CatgramApiService {
    @GET("images/search")
    suspend fun getCatsData(
        @Query("limit") limit: Int,
        @Query("breed_ids") breedIds: String?,
        @Query("page") page: Int,
        @Query("order") order: String,
    ): List<CatsData.CatsApiData>

    @GET("breeds")
    suspend fun getBreedList(): List<BreedInfo>
}
