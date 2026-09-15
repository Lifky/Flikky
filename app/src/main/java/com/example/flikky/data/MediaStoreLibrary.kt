package com.example.flikky.data

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import com.example.flikky.server.dto.AlbumItemDto
import com.example.flikky.server.routes.AlbumFileHandle
import com.example.flikky.server.routes.AlbumResult
import com.example.flikky.server.routes.MediaLibrary
import com.example.flikky.util.AlbumItemId
import com.example.flikky.util.AlbumMediaKind
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** MediaStore-backed album access kept behind the Android-free [MediaLibrary] boundary. */
class MediaStoreLibrary(private val resolver: ContentResolver) : MediaLibrary {

    override fun count(): Int = queryAll().size

    override fun listStream(): Flow<List<AlbumItemDto>> = flow {
        queryAll().chunked(BATCH_SIZE).forEach { batch ->
            currentCoroutineContext().ensureActive()
            emit(batch)
        }
    }

    override fun open(id: AlbumItemId): AlbumResult<AlbumFileHandle> {
        val uri = uriOf(id)
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
            resolver.loadThumbnail(uriOf(id), Size(maxPx, maxPx), null)
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

    /** Rebuilds a content URI from the validated kind and numeric MediaStore ID. */
    private fun uriOf(id: AlbumItemId): Uri {
        val collection = when (id.kind) {
            AlbumMediaKind.IMAGE ->
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            AlbumMediaKind.VIDEO ->
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
        return ContentUris.withAppendedId(collection, id.mediaStoreId)
    }

    private fun queryAll(): List<AlbumItemDto> =
        (queryKind(AlbumMediaKind.IMAGE) + queryKind(AlbumMediaKind.VIDEO))
            .sortedByDescending { it.takenAtMs }

    private fun queryKind(kind: AlbumMediaKind): List<AlbumItemDto> {
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

    private companion object {
        const val BATCH_SIZE = 200
    }
}
