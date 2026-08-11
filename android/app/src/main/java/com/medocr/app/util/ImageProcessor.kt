package com.medocr.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Mirrors `ocr_parser.py::preprocess_image`: EXIF auto-rotate, RGB-normalise,
 * downscale to <= 2000px on the long edge, and re-encode as JPEG so every
 * provider receives a small, correctly-oriented image.
 */
object ImageProcessor {

    private const val MAX_DIMENSION = 2000
    private const val JPEG_QUALITY = 92

    suspend fun toJpegBytes(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val orientation = readExifOrientation(context, uri)
        val bounds = decodeBounds(context, uri)
        val sampleSize = calculateInSampleSize(bounds.first, bounds.second, MAX_DIMENSION * 2)

        val rawBitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: error("Unable to decode image")

        val rotated = applyExifRotation(rawBitmap, orientation)
        val resized = downscaleIfNeeded(rotated)

        ByteArrayOutputStream().use { out ->
            resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            if (resized !== rotated) resized.recycle()
            if (rotated !== rawBitmap) rawBitmap.recycle()
            rotated.recycle()
            out.toByteArray()
        }
    }

    suspend fun toBase64(context: Context, uri: Uri): String =
        Base64.encodeToString(toJpegBytes(context, uri), Base64.NO_WRAP)

    private fun readExifOrientation(context: Context, uri: Uri): Int =
        context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

    private fun decodeBounds(context: Context, uri: Uri): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
        return options.outWidth to options.outHeight
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqMax: Int): Int {
        var sampleSize = 1
        var w = width
        var h = height
        while (maxOf(w, h) / 2 >= reqMax) {
            w /= 2
            h /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun applyExifRotation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun downscaleIfNeeded(bitmap: Bitmap): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim <= MAX_DIMENSION) return bitmap
        val scale = MAX_DIMENSION.toFloat() / maxDim
        val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
