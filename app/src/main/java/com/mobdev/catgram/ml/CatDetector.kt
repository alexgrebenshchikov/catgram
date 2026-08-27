package com.mobdev.catgram.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.mobdev.catgram.logging.logger
import kotlinx.coroutines.tasks.await

class CatDetector {
    
    private val catLabels = setOf("cat")
    private val confidenceThreshold = 0.5f
    
    suspend fun isCatImage(context: Context, imageUri: Uri): Result<Boolean> {
        var bitmap: Bitmap? = null
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        return try {
            bitmap = uriToBitmap(context, imageUri)
                ?: return Result.failure(Throwable("Failed to load image"))
            
            val image = InputImage.fromBitmap(bitmap, 0)
            
            val labels = labeler.process(image).await()
            logger.d("Image labels: ${labels.map { "${it.text}: ${it.confidence}" }}")
            
            val isCat = labels.any { label ->
                label.text.lowercase() in catLabels && label.confidence >= confidenceThreshold
            }
            
            logger.d("Is cat image: $isCat")
            Result.success(isCat)
        } catch (e: Throwable) {
            logger.e("Cat detection error: ${e.message}", e)
            Result.failure(e)
        } finally {
            bitmap?.recycle()
            labeler.close()
        }
    }
    
    private fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.isMutableRequired = true
                    val maxDimension = maxOf(info.size.width, info.size.height)
                    if (maxDimension > MAX_DETECTION_DIMENSION) {
                        val scale = MAX_DETECTION_DIMENSION.toFloat() / maxDimension
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt(),
                            (info.size.height * scale).toInt(),
                        )
                    }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sampleSize = 1
                while (bounds.outWidth / sampleSize > MAX_DETECTION_DIMENSION ||
                    bounds.outHeight / sampleSize > MAX_DETECTION_DIMENSION
                ) {
                    sampleSize *= 2
                }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(
                        it,
                        null,
                        BitmapFactory.Options().apply { inSampleSize = sampleSize },
                    )
                }
            }
        } catch (e: Throwable) {
            logger.e( "Failed to decode bitmap: ${e.message}", e)
            null
        }
    }

    private companion object {
        const val MAX_DETECTION_DIMENSION = 1024
    }
}
