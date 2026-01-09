package com.mobdev.catgram.network

import android.content.Context
import android.net.Uri
import android.util.Base64


private const val API_KEY: String = "d7d190f7551b2c5f02a6e90822c022ee"
data class ImageUploadResult(
    val url: String
)

class ImageUploader(
    private val apiService: ImageUploadApiService
) {
    suspend fun uploadImage(imageUri: Uri, context: Context): Result<ImageUploadResult> {
        return try {
            // Convert image Uri to Base64
            val base64Image = uriToBase64(imageUri, context)
                ?: return Result.failure(Throwable("Failed to read image"))
            // Call API
            val response = apiService.uploadImage(
                apiKey = API_KEY,
                imageBase64 = base64Image
            )

            if (response.success && response.data != null) {
                Result.success(ImageUploadResult(url = response.data.url))
            } else {
                Result.failure(Throwable("Upload failed"))
            }
        } catch (e: Exception) {
            Result.failure(Throwable("Error: ${e.message}"))
        }
    }

    private fun uriToBase64(uri: Uri, context: Context): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            bytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        } catch (_: Exception) {
            null
        }
    }
}
