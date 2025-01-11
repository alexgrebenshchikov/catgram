package com.mobdev.catgram.network

import kotlinx.serialization.Serializable

@Serializable
data class BreedInfo(
    val id: String,
    val name: String,
    val description: String
)
