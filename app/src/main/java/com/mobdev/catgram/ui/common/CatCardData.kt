package com.mobdev.catgram.ui.common

import com.google.firebase.Timestamp
import com.mobdev.catgram.network.BreedInfo

typealias FavClickCallback = (Boolean, CatCardData) -> Unit
typealias CheckIsFavCallback = (CatCardData) -> Boolean
typealias CheckIsEnabledCallback = (String) -> Boolean
typealias GetLikesCountCallback = ((String) -> Long?)?
typealias GetCommentsCountCallback = ((String) -> Long?)?
typealias OnErrorItemClicked = (() -> Unit)?
typealias OnPostDeleteCallback = ((CatCardData.UserPost) -> Unit)?
typealias CheckIsMyPostCallback = (String) -> Boolean

sealed interface CatCardData {
    val id: String

    data class CatsApi(
        override val id: String,
        val url: String,
        val breeds: List<BreedInfo>,
    ) : CatCardData

    data class UserPost(
        override val id: String,
        val userId: String,
        val url: String,
        val text: String,
        val displayName: String,
        val avatarUrl: String?,
        val timestamp: Timestamp?,
    ) : CatCardData
}
