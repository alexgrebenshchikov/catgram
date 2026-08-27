package com.mobdev.catgram.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class CatApiConfigurationException : IOException("CAT_API_KEY is not configured")

class CatApiKeyInterceptor(
    private val apiKey: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (apiKey.isBlank()) {
            throw CatApiConfigurationException()
        }

        val request = chain.request()
            .newBuilder()
            .header(API_KEY_HEADER, apiKey)
            .build()

        return chain.proceed(request)
    }

    private companion object {
        const val API_KEY_HEADER = "x-api-key"
    }
}
