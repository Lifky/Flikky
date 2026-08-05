package com.example.flikky.ui.components

import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** 媒体文件的展示宽高（已按 EXIF / 视频 rotation 换算成竖排后的实际方向）。 */
data class MediaDimensions(val width: Int, val height: Int)

/**
 * 读取图片/视频原始宽高，供媒体气泡在解码前就确定布局尺寸——布局绝不跟随
 * painter 固有像素走（会随来源与解码时机漂移，v1.17 修复轮教训）。
 *
 * 结果按「路径 + lastModified」缓存在内存里：LazyColumn 滚动往返不会重复打开
 * 文件，[peek] 供 Compose 同步取缓存避免占位闪变。
 */
object MediaDimensionsCache {

    private val cache = ConcurrentHashMap<String, MediaDimensions>()

    private fun keyOf(file: File) = "${file.path}:${file.lastModified()}"

    /** 主线程安全的同步缓存查询；未读取过返回 null。 */
    fun peek(file: File): MediaDimensions? = cache[keyOf(file)]

    /** IO 线程读取并缓存；文件损坏或元数据缺失返回 null（不缓存失败）。 */
    suspend fun read(file: File, isVideo: Boolean): MediaDimensions? {
        peek(file)?.let { return it }
        return withContext(Dispatchers.IO) {
            val dims = runCatching {
                if (isVideo) readVideo(file) else readImage(file)
            }.getOrNull()
            dims?.also { cache[keyOf(file)] = it }
        }
    }

    private fun readImage(file: File): MediaDimensions? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        // EXIF 90/270 度旋转的照片（手机竖拍常见）解码后宽高互换。
        val orientation = runCatching {
            ExifInterface(file.path)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val swap = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE
        return if (swap) MediaDimensions(opts.outHeight, opts.outWidth)
        else MediaDimensions(opts.outWidth, opts.outHeight)
    }

    private fun readVideo(file: File): MediaDimensions? =
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.path)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: return null
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: return null
            if (w <= 0 || h <= 0) return null
            val rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) MediaDimensions(h, w)
            else MediaDimensions(w, h)
        }
}
