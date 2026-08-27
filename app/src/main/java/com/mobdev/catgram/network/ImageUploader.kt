package com.mobdev.catgram.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.mobdev.catgram.BuildConfig
import java.io.ByteArrayOutputStream

private const val MAX_IMAGE_DIMENSION = 1200
private const val JPEG_QUALITY = 85

data class ImageUploadResult(
    val url: String,
)

class ImageUploader(
    private val apiService: ImageUploadApiService,
) {
    suspend fun uploadImage(imageUri: Uri, context: Context): Result<ImageUploadResult> {
        return try {
            val base64Image = resizeAndEncodeImage(imageUri, context)
                ?: return Result.failure(Throwable("Failed to process image"))
            val response = apiService.uploadImage(
                apiKey = BuildConfig.IMGBB_API_KEY,
                imageBase64 = base64Image,
            )

            if (response.success && response.data != null) {
                Result.success(ImageUploadResult(response.data.url))
            } else {
                Result.failure(Throwable("Upload failed"))
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun resizeAndEncodeImage(uri: Uri, context: Context): String? {
        return try {
            // Get original dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight

            // Calculate sample size for initial downscaling (power of 2)
            var sampleSize = 1
            while (originalWidth / sampleSize > MAX_IMAGE_DIMENSION * 2 ||
                originalHeight / sampleSize > MAX_IMAGE_DIMENSION * 2
            ) {
                sampleSize *= 2
            }

            // Decode with sample size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            var bitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            } ?: return null

            // Apply EXIF rotation
            bitmap = applyExifRotation(uri, context, bitmap)

            // Scale to exact max dimension
            val width = bitmap.width
            val height = bitmap.height
            if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
                val scale = MAX_IMAGE_DIMENSION.toFloat() / maxOf(width, height)
                val newWidth = (width * scale).toInt()
                val newHeight = (height * scale).toInt()
                val scaledBitmap = bitmap.scale(newWidth, newHeight)
                if (scaledBitmap != bitmap) {
                    bitmap.recycle()
                }
                bitmap = scaledBitmap
            }

            ByteArrayOutputStream().use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
                bitmap.recycle()
                Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun applyExifRotation(uri: Uri, context: Context, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(inputStream)
            inputStream.close()

            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> return bitmap
            }

            val matrix = Matrix().apply { postRotate(rotation) }
            val rotatedBitmap =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            rotatedBitmap
        } catch (_: Exception) {
            bitmap
        }
    }
}
