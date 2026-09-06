package com.paizi.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.paizi.core.logging.LoggingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

class VisionManager(private val context: Context) {
    private val TAG = "VisionManager"

    data class PreprocessedImage(
        val base64Data: String,
        val mimeType: String,
        val width: Int,
        val height: Int,
        val originalSizeBytes: Long
    )

    suspend fun processImageUri(uri: Uri, maxDimension: Int = 1024): PreprocessedImage = withContext(Dispatchers.IO) {
        LoggingManager.i(TAG, "Processing vision attachment from URI: $uri")
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open stream for URI: $uri")

        val bytes = inputStream.readBytes()
        inputStream.close()

        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight
        val mime = boundsOptions.outMimeType ?: "image/jpeg"

        var inSampleSize = 1
        while ((origW / inSampleSize) > maxDimension || (origH / inSampleSize) > maxDimension) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        val decodedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: throw IllegalStateException("Failed to decode bitmap from image bytes")

        val outputStream = ByteArrayOutputStream()
        decodedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val compressedBytes = outputStream.toByteArray()
        val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)

        LoggingManager.i(TAG, "Vision attachment processed: ${decodedBitmap.width}x${decodedBitmap.height} (${compressedBytes.size} bytes)")
        PreprocessedImage(
            base64Data = base64,
            mimeType = "image/jpeg",
            width = decodedBitmap.width,
            height = decodedBitmap.height,
            originalSizeBytes = bytes.size.toLong()
        )
    }

    suspend fun processImageFile(file: File): PreprocessedImage {
        return processImageUri(Uri.fromFile(file))
    }
}
