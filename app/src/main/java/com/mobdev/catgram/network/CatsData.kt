package com.mobdev.catgram.network

import com.google.firebase.Timestamp
import kotlinx.serialization.Serializable

sealed interface CatsData {
    @Serializable
    data class CatsApiData(
        val id: String,
        val url: String,
        val breeds: List<BreedInfo>
    ) : CatsData

    data class CatsUserPostData(
        val id: String,
        val userId: String,
        val url: String,
        val text: String,
        val displayName: String,
        val avatarUrl: String?,
        val timestamp: Timestamp?,
    ) : CatsData
}