package com.mobdev.catgram.data

import com.mobdev.catgram.network.BreedInfo
import com.mobdev.catgram.network.CatgramApiService
import com.mobdev.catgram.network.CatsData


interface CatgramRepository {
    suspend fun getCatsData(
        limit: Int,
        breedIds: List<String>,
        page: Int
    ): List<CatsData>

    suspend fun getBreedList(): List<BreedInfo>
}

class NetworkCatgramRepository(private val catgramApiService: CatgramApiService) : CatgramRepository {
    override suspend fun getCatsData(
        limit: Int,
        breedIds: List<String>,
        page: Int
    ): List<CatsData> {
        val apiKey = "live_DLVLuhSCT0Oc54KIL9cYIqIpJeJsYQACOGTL5ajdNIFDSIZxNCibgXMCJRaUXOdE"
        val order = "DESC"
        return catgramApiService.getCatsData(limit, breedIds, page, order, apiKey)
    }

    override suspend fun getBreedList(): List<BreedInfo> {
        return catgramApiService.getBreedList()
    }
}