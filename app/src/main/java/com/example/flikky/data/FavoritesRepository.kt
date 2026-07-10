package com.example.flikky.data

import com.example.flikky.data.db.FavoriteDao
import com.example.flikky.data.db.FavoriteGroupDao
import com.example.flikky.data.db.entities.FavoriteEntity
import com.example.flikky.data.db.entities.FavoriteGroupEntity
import com.example.flikky.session.Message
import com.example.flikky.util.IdGen
import com.example.flikky.export.FavoriteExport
import com.example.flikky.export.FavoriteGroupExport
import com.example.flikky.export.ParsedBackup
import com.example.flikky.export.ZipImporter
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.flow.Flow

class FavoritesRepository(
    private val favoriteDao: FavoriteDao,
    private val favoriteGroupDao: FavoriteGroupDao,
    private val sessionFileStore: SessionFileStore,
    private val favoriteFileStore: FavoriteFileStore,
    private val now: () -> Long,
    private val depotIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val localSourceMessageIdFactory: () -> Long = { IdGen.newMessageId() },
) {
    data class ExportData(
        val groups: List<FavoriteGroupExport>,
        val favorites: List<FavoriteExport>,
    )

    data class ImportResult(
        val imported: Int,
        val skipped: Int,
        val errors: List<String>,
    )

    fun observeFavorites(): Flow<List<FavoriteEntity>> = favoriteDao.observeAll()

    fun observeGroups(): Flow<List<FavoriteGroupEntity>> = favoriteGroupDao.observeAll()

    fun observeFavoritedIds(sessionId: Long): Flow<List<Long>> =
        favoriteDao.observeFavoritedMessageIds(sessionId)

    suspend fun isFavorited(sid: Long, mid: Long): Boolean =
        favoriteDao.findBySource(sid, mid) != null

    suspend fun favoriteText(
        sid: Long,
        sessionName: String?,
        msg: Message.Text,
        groupId: Long?,
    ): Long =
        favoriteDao.insert(
            FavoriteEntity(
                sourceSessionId = sid,
                sourceMessageId = msg.id,
                kind = "TEXT",
                textContent = msg.content,
                groupId = groupId,
                createdAt = now(),
                sourceSessionName = sessionName,
                origin = msg.origin.name,
            )
        )

    suspend fun favoriteFile(
        sid: Long,
        sessionName: String?,
        msg: Message.File,
        groupId: Long?,
    ): Long {
        val depotFileId = depotIdFactory()
        val source = File(sessionFileStore.fileDir(sid), msg.fileId)
        favoriteFileStore.copyIn(depotFileId, source)
        return favoriteDao.insert(
            FavoriteEntity(
                sourceSessionId = sid,
                sourceMessageId = msg.id,
                kind = "FILE",
                fileId = depotFileId,
                fileName = msg.name,
                fileSize = msg.sizeBytes,
                fileMime = msg.mime,
                groupId = groupId,
                createdAt = now(),
                sourceSessionName = sessionName,
                origin = msg.origin.name,
            )
        )
    }

    suspend fun addLocalText(text: String, groupId: Long?): Long {
        val normalized = text.trim()
        require(normalized.isNotBlank()) { "Local favorite text must not be blank" }
        return favoriteDao.insert(
            FavoriteEntity(
                sourceSessionId = LOCAL_SOURCE_SESSION_ID,
                sourceMessageId = localSourceMessageIdFactory(),
                kind = "TEXT",
                textContent = normalized,
                groupId = groupId,
                createdAt = now(),
                sourceSessionName = LOCAL_SOURCE_NAME,
                origin = LOCAL_ORIGIN,
            )
        )
    }

    suspend fun addLocalFile(
        name: String,
        sizeBytes: Long?,
        mime: String,
        groupId: Long?,
        source: InputStream,
    ): Long {
        val depotFileId = depotIdFactory()
        val target = favoriteFileStore.copyIn(depotFileId, source)
        return try {
            favoriteDao.insert(
                FavoriteEntity(
                    sourceSessionId = LOCAL_SOURCE_SESSION_ID,
                    sourceMessageId = localSourceMessageIdFactory(),
                    kind = "FILE",
                    fileId = depotFileId,
                    fileName = name.ifBlank { "unnamed" },
                    fileSize = sizeBytes?.takeIf { it >= 0L } ?: target.length(),
                    fileMime = mime.ifBlank { "application/octet-stream" },
                    groupId = groupId,
                    createdAt = now(),
                    sourceSessionName = LOCAL_SOURCE_NAME,
                    origin = LOCAL_ORIGIN,
                )
            )
        } catch (t: Throwable) {
            favoriteFileStore.delete(depotFileId)
            throw t
        }
    }

    suspend fun unfavoriteBySource(sid: Long, mid: Long) {
        val row = favoriteDao.findBySource(sid, mid) ?: return
        deleteFavorite(row.id)
    }

    suspend fun deleteFavorite(id: Long) {
        val row = favoriteDao.getById(id) ?: return
        favoriteDao.deleteById(id)
        row.fileId?.let { favoriteFileStore.delete(it) }
    }

    suspend fun deleteFavorites(ids: List<Long>) {
        ids.forEach { deleteFavorite(it) }
    }

    suspend fun createGroup(name: String): Long =
        favoriteGroupDao.insert(
            FavoriteGroupEntity(
                name = name.trim(),
                sortOrder = favoriteGroupDao.maxSortOrder() + 1,
                createdAt = now(),
            )
        )

    suspend fun renameGroup(id: Long, name: String) {
        favoriteGroupDao.getById(id)?.let { favoriteGroupDao.update(it.copy(name = name.trim())) }
    }

    suspend fun deleteGroup(id: Long): Pair<FavoriteGroupEntity, List<Long>>? {
        val group = favoriteGroupDao.getById(id) ?: return null
        val members = favoriteDao.memberIds(id)
        favoriteDao.rehomeGroup(id)
        favoriteGroupDao.deleteById(id)
        return group to members
    }

    suspend fun restoreGroup(group: FavoriteGroupEntity, memberIds: List<Long>): Long {
        val newId = favoriteGroupDao.insert(group.copy(id = 0))
        if (memberIds.isNotEmpty()) favoriteDao.setGroupForFavorites(memberIds, newId)
        return newId
    }

    suspend fun reorderGroups(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            favoriteGroupDao.getById(id)?.let { favoriteGroupDao.update(it.copy(sortOrder = index)) }
        }
    }

    suspend fun moveFavoritesToGroup(ids: List<Long>, groupId: Long?) {
        if (ids.isEmpty()) return
        favoriteDao.setGroupForFavorites(ids, groupId)
    }

    fun search(all: List<FavoriteEntity>, query: String): List<FavoriteEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return all
        return all.filter { favorite ->
            favorite.textContent?.contains(trimmed, ignoreCase = true) == true ||
                favorite.fileName?.contains(trimmed, ignoreCase = true) == true
        }
    }

    suspend fun exportSnapshot(): ExportData = ExportData(
        groups = favoriteGroupDao.listAll().map { group ->
            FavoriteGroupExport(
                id = group.id,
                name = group.name,
                sortOrder = group.sortOrder,
                createdAt = group.createdAt,
            )
        },
        favorites = favoriteDao.listAll().map { favorite ->
            FavoriteExport(
                id = favorite.id,
                sourceSessionId = favorite.sourceSessionId,
                sourceMessageId = favorite.sourceMessageId,
                kind = favorite.kind,
                textContent = favorite.textContent,
                fileId = favorite.fileId,
                fileName = favorite.fileName,
                fileSize = favorite.fileSize,
                fileMime = favorite.fileMime,
                groupId = favorite.groupId,
                createdAt = favorite.createdAt,
                sourceSessionName = favorite.sourceSessionName,
                origin = favorite.origin,
            )
        },
    )

    suspend fun importBackup(
        backup: ParsedBackup,
        zipFile: ZipFile,
        sessionIdMap: Map<Long, Long> = emptyMap(),
    ): ImportResult {
        var imported = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        val groupIdMap = mutableMapOf<Long, Long>()
        for (group in backup.favoriteGroups.sortedBy { it.sortOrder }) {
            val normalizedName = group.name.trim().take(12).ifEmpty { continue }
            val targetId = favoriteGroupDao.findByName(normalizedName)?.id
                ?: favoriteGroupDao.insert(
                    FavoriteGroupEntity(
                        name = normalizedName,
                        sortOrder = favoriteGroupDao.maxSortOrder() + 1,
                        createdAt = group.createdAt,
                    )
                )
            groupIdMap[group.id] = targetId
        }

        for (favorite in backup.favorites) {
            val targetSessionId = sessionIdMap[favorite.sourceSessionId] ?: favorite.sourceSessionId
            if (favoriteDao.findBySource(targetSessionId, favorite.sourceMessageId) != null) {
                skipped++
                continue
            }

            var restoredFileId: String? = null
            try {
                if (favorite.kind == "FILE") {
                    val entry = ZipImporter.resolveFavoriteFileEntry(favorite, zipFile)
                        ?: error("收藏文件缺失：${favorite.fileName ?: favorite.fileId ?: favorite.id}")
                    restoredFileId = depotIdFactory()
                    favoriteFileStore.copyIn(restoredFileId, zipFile.getInputStream(entry))
                }
                favoriteDao.insert(
                    FavoriteEntity(
                        sourceSessionId = targetSessionId,
                        sourceMessageId = favorite.sourceMessageId,
                        kind = favorite.kind,
                        textContent = favorite.textContent,
                        fileId = restoredFileId,
                        fileName = favorite.fileName,
                        fileSize = favorite.fileSize,
                        fileMime = favorite.fileMime,
                        groupId = favorite.groupId?.let(groupIdMap::get),
                        createdAt = favorite.createdAt,
                        sourceSessionName = favorite.sourceSessionName,
                        origin = favorite.origin,
                    )
                )
                imported++
            } catch (t: Throwable) {
                restoredFileId?.let(favoriteFileStore::delete)
                errors += t.message ?: "收藏导入失败"
            }
        }
        return ImportResult(imported = imported, skipped = skipped, errors = errors)
    }

    companion object {
        const val LOCAL_SOURCE_SESSION_ID = 0L
        private const val LOCAL_SOURCE_NAME = "本地添加"
        private const val LOCAL_ORIGIN = "PHONE"
    }
}
