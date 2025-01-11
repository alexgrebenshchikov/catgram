package com.mobdev.catgram.network

import kotlinx.serialization.Serializable

@Serializable
data class CatsData(
    val id: String,
    val url: String,
    val breeds: List<BreedInfo>
)