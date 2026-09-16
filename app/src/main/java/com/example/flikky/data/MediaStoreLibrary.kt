package com.example.flikky.data

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import com.example.flikky.server.dto.AlbumBucketDto
import com.example.flikky.server.dto.AlbumItemDto
import com.example.flikky.server.routes.AlbumFileHandle
import com.example.flikky.server.routes.AlbumResult
import com.example.flikky.server.routes.MediaLibrary
import com.example.flikky.util.AlbumItemId
import com.example.flikky.util.AlbumDateKey
import com.example.flikky.util.AlbumMediaKind
import com.example.flikky.util.NAME_ORDER
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * MediaStore-backed album access kept behind the Android-free [MediaLibrary] boundary.
 *
 * [zone] 是 lambda 而不是取值一次：用户可以在系统设置里改时区，而这个对象活在
 * `ServiceLocator` 里、生命周期比一次设置变更长得多。**它是全项目唯一决定
 * 「一张照片属于哪一天」的地方**（D65），所以取错时区的后果是两端分组不一致。
 */
class MediaStoreLibrary(
    private val resolver: ContentResolver,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : MediaLibrary {

    override fun count(bucket: String?): Int = queryAll(bucket).size

    override fun listStream(bucket: String?): Flow<List<AlbumItemDto>> = flow {
        queryAll(bucket).chunked(BATCH_SIZE).forEach { batch ->
            currentCoroutineContext().ensureActive()
            emit(batch)
        }
    }

    override fun todayKey(): String = AlbumDateKey.of(System.currentTimeMillis(), zone())

    override fun yesterdayKey(): String {
        val yesterday = Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(zone())
            .minusDays(1)
        return AlbumDateKey.of(yesterday.toInstant().toEpochMilli(), zone())
    }

    /**
     * 相册簿聚合。
     *
     * 在内存里按 [AlbumItemDto.bucketName] 聚合，而不是让 MediaStore 做 GROUP BY：
     * `queryAll` 已经把两类媒体合过一遍，再发一次分组查询就要处理
     * 「Images 与 Video 各有一份同名 bucket」的合并，代价比在这里分组高。
     *
     * 封面取该簿里最新的一项 —— 与用户在系统相册里看到的封面语义一致。
     */
    override fun buckets(): List<AlbumBucketDto> =
        queryAll(null)
            .groupBy { it.bucketName }
            .map { (name, rows) ->
                AlbumBucketDto(
                    name = name,
                    count = rows.size,
                    // rows 已按 takenAtMs 倒序（queryAll 排过），首项即最新。
                    coverId = rows.first().id,
                )
            }
            .sortedWith(compareByDescending<AlbumBucketDto> { it.count }.thenBy(NAME_ORDER) { it.name })

    override fun open(id: AlbumItemId): AlbumResult<AlbumFileHandle> {
        val uri = contentUri(id)
        val row = queryOne(id) ?: return AlbumResult.NotFound
        val probe = runCatching { resolver.openInputStream(uri) }.getOrNull()
            ?: return AlbumResult.NotFound
        probe.close()
        return AlbumResult.Ok(
            AlbumFileHandle(
                fileName = row.name,
                mime = row.mime,
                size = row.size,
                open = {
                    resolver.openInputStream(uri) ?: throw IOException("cannot open $uri")
                },
            ),
        )
    }

    override fun thumbnail(id: AlbumItemId, maxPx: Int): AlbumResult<ByteArray> {
        val bitmap = runCatching {
            resolver.loadThumbnail(contentUri(id), Size(maxPx, maxPx), null)
        }.getOrNull() ?: return AlbumResult.NotFound
        val output = ByteArrayOutputStream()
        val compressed = runCatching {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
        }.getOrDefault(false)
        bitmap.recycle()
        if (!compressed || output.size() == 0) return AlbumResult.NotFound
        return AlbumResult.Ok(output.toByteArray())
    }

    private data class Row(
        val name: String,
        val mime: String,
        val size: Long,
    )

    /** [bucket] 为 null 表示整个相册；筛选在合并排序**之后**做，两种视图顺序才一致。 */
    private fun queryAll(bucket: String?): List<AlbumItemDto> =
        (queryKind(AlbumMediaKind.IMAGE) + queryKind(AlbumMediaKind.VIDEO))
            .let { rows -> if (bucket == null) rows else rows.filter { it.bucketName == bucket } }
            .sortedByDescending { it.takenAtMs }

    private fun queryKind(kind: AlbumMediaKind): List<AlbumItemDto> {
        // 一次列举内固定时区：中途变化会让同一批项的键不自洽。
        val currentZone = zone()
        val collection = when (kind) {
            AlbumMediaKind.IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            AlbumMediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
        val columns = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            if (kind == AlbumMediaKind.VIDEO) add(MediaStore.MediaColumns.DURATION)
        }.toTypedArray()

        val items = mutableListOf<AlbumItemDto>()
        runCatching { resolver.query(collection, columns, null, null, null) }
            .getOrNull()
            ?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val modifiedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val takenIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val durationIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
                val bucketIndex = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val rawId = cursor.getLong(idIndex)
                    if (rawId <= 0L) continue
                    val takenAt = takenIndex.takeIf { it >= 0 }
                        ?.let(cursor::getLong)
                        ?.takeIf { it > 0L }
                        ?: modifiedIndex.takeIf { it >= 0 }
                            ?.let { cursor.getLong(it) * 1_000L }
                        ?: 0L
                    items += AlbumItemDto(
                        id = AlbumItemId(kind, rawId).format(),
                        name = nameIndex.takeIf { it >= 0 }?.let(cursor::getString) ?: "$rawId",
                        mime = mimeIndex.takeIf { it >= 0 }?.let(cursor::getString)
                            ?: if (kind == AlbumMediaKind.IMAGE) "image/*" else "video/*",
                        size = sizeIndex.takeIf { it >= 0 }?.let(cursor::getLong) ?: 0L,
                        takenAtMs = takenAt,
                        durationMs = durationIndex.takeIf { it >= 0 }?.let(cursor::getLong) ?: 0L,
                        // 日期在**这里**算完就下发，两端都不再从 takenAtMs 推日期。
                        // 这是 D65 的落点：手机与浏览器设备时区不同时，各自算会分组不一致。
                        dateKey = AlbumDateKey.of(takenAt, currentZone),
                        bucketName = bucketIndex.takeIf { it >= 0 }
                            ?.let(cursor::getString)
                            .orEmpty(),
                    )
                }
            }
        return items
    }

    /** Deliberately simple for v1.21: this scans one media kind and can be indexed later. */
    private fun queryOne(id: AlbumItemId): Row? {
        val dto = queryKind(id.kind).firstOrNull { it.id == id.format() } ?: return null
        return Row(name = dto.name, mime = dto.mime, size = dto.size)
    }

    internal companion object {
        const val BATCH_SIZE = 200

        /** Rebuilds a content URI from a validated kind and numeric MediaStore ID. */
        fun contentUri(id: AlbumItemId): Uri {
            val collection = when (id.kind) {
                AlbumMediaKind.IMAGE ->
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                AlbumMediaKind.VIDEO ->
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            }
            return ContentUris.withAppendedId(collection, id.mediaStoreId)
        }
    }
}
