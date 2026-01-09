package com.mobdev.catgram.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.mobdev.catgram.logging.logger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class CatDetector {
    
    private val catLabels = setOf("cat")
    private val confidenceThreshold = 0.5f
    
    suspend fun isCatImage(context: Context, imageUri: Uri): Result<Boolean> {
        return suspendCoroutine { continuation ->
            try {
                val bitmap = uriToBitmap(context, imageUri)
                if (bitmap == null) {
                    continuation.resume(Result.failure(Exception("Failed to load image")))
                    return@suspendCoroutine
                }
                
                val image = InputImage.fromBitmap(bitmap, 0)
                val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                
                labeler.process(image)
                    .addOnSuccessListener { labels ->
                        logger.d( "Image labels: ${labels.map { "${it.text}: ${it.confidence}" }}")
                        
                        val isCat = labels.any { label ->
                            label.text.lowercase() in catLabels && label.confidence >= confidenceThreshold
                        }
                        
                        logger.d( "Is cat image: $isCat")
                        continuation.resume(Result.success(isCat))
                    }
                    .addOnFailureListener { e ->
                        logger.e( "Image labeling failed: ${e.message}")
                        continuation.resume(Result.failure(e))
                    }
            } catch (e: Exception) {
                logger.e( "Cat detection error: ${e.message}")
                continuation.resume(Result.failure(e))
            }
        }
    }
    
    private fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            logger.e( "Failed to decode bitmap: ${e.message}")
            null
        }
    }
}
