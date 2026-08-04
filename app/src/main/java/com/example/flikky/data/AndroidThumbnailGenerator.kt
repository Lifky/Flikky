package com.example.flikky.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.example.flikky.server.routes.ThumbnailGenerator
import java.io.File

/** Generates cached 512px JPEG thumbnails for local image and video files. */
class AndroidThumbnailGenerator(
    private val maxDim: Int = 512,
    private val quality: Int = 80,
) : ThumbnailGenerator {

    override fun generate(source: File, mime: String, target: File): Boolean {
        val bitmap = when {
            mime.startsWith("image/") -> decodeSampledImage(source)
            mime.startsWith("video/") -> decodeVideoFrame(source)
            else -> null
        } ?: return false
        return try {
            target.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
        } catch (_: Exception) {
            target.delete()
            false
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeSampledImage(source: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val decoded = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        return scaleDown(decoded)
    }

    private fun decodeVideoFrame(source: File): Bitmap? = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(source.absolutePath)
            retriever.frameAtTime
        }
    }.getOrNull()?.let(::scaleDown)

    private fun scaleDown(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxDim) return source
        val ratio = maxDim.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== source) source.recycle()
        return scaled
    }
}
