package com.mobdev.catgram.ui

import android.content.Intent
import android.net.Uri

sealed interface AppDeepLink {
    data class Post(
        val postId: String,
        val activityId: String? = null,
    ) : AppDeepLink
}

object AppDeepLinks {
    const val EXTRA_POST_ID = "postId"
    const val EXTRA_ACTIVITY_ID = "activityId"

    fun postUri(postId: String, activityId: String? = null): Uri {
        require(postId.isNotBlank()) { "Post id must not be blank" }
        return Uri.Builder()
            .scheme(SCHEME)
            .authority(POST_HOST)
            .appendPath(postId)
            .apply {
                activityId?.takeIf(String::isNotBlank)?.let {
                    appendQueryParameter(EXTRA_ACTIVITY_ID, it)
                }
            }
            .build()
    }

    fun fromIntent(intent: Intent?): AppDeepLink? {
        val data = intent?.data
        if (data?.scheme == SCHEME && data.host == POST_HOST) {
            val postId = data.pathSegments.firstOrNull()?.takeIf(String::isNotBlank) ?: return null
            return AppDeepLink.Post(postId, data.getQueryParameter(EXTRA_ACTIVITY_ID))
        }

        val postId = intent?.getStringExtra(EXTRA_POST_ID)?.takeIf(String::isNotBlank) ?: return null
        return AppDeepLink.Post(postId, intent.getStringExtra(EXTRA_ACTIVITY_ID))
    }

    private const val SCHEME = "catgram"
    private const val POST_HOST = "post"
}
