package com.example.flikky.ui.favorites

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flikky.R
import com.example.flikky.data.FavoriteFileStore
import com.example.flikky.data.FavoritesRepository
import com.example.flikky.data.db.entities.FavoriteEntity
import com.example.flikky.data.db.entities.FavoriteGroupEntity
import com.example.flikky.data.settings.SettingsRepository
import com.example.flikky.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FavoritesViewModel @JvmOverloads constructor(
    app: Application,
    private val repository: FavoritesRepository = ServiceLocator.favoritesRepository,
    private val settingsRepository: SettingsRepository = ServiceLocator.settingsRepository,
    private val favoriteFileStore: () -> FavoriteFileStore = { ServiceLocator.favoriteFileStore },
) : AndroidViewModel(app) {
    val groups: Flow<List<FavoriteGroupEntity>> = repository.observeGroups()
    val activeGroupId: Flow<Long?> = settingsRepository.settings.map { it.activeFavoriteGroupId }

    /** 弹药库是否有任何收藏（不分组、不过滤）。用于决定是否显示合集 chip 行——与主页一致：整库为空时不显示。 */
    val hasFavorites: Flow<Boolean> = repository.observeFavorites().map { it.isNotEmpty() }

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query.asStateFlow()

    val items: Flow<List<FavoriteEntity>> = combine(
        repository.observeFavorites(),
        settingsRepository.settings,
        query,
    ) { favorites, settings, q ->
        val grouped = if (settings.activeFavoriteGroupId == null) {
            favorites
        } else {
            favorites.filter { it.groupId == settings.activeFavoriteGroupId }
        }
        repository.search(grouped, q)
    }

    private val _selection = MutableStateFlow<Set<Long>?>(null)
    val selection: StateFlow<Set<Long>?> = _selection.asStateFlow()
    val selecting: StateFlow<Boolean> = _selection
        .map { it != null }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    fun setQuery(value: String) {
        query.value = value
    }

    fun clearQuery() {
        query.value = ""
    }

    fun setActiveGroup(id: Long?): Job =
        viewModelScope.launch { settingsRepository.setActiveFavoriteGroup(id) }

    fun createGroup(name: String): Job =
        viewModelScope.launch {
            val validName = normalizeGroupName(name) ?: return@launch
            val id = repository.createGroup(validName)
            settingsRepository.setActiveFavoriteGroup(id)
        }

    fun renameGroup(id: Long, name: String): Job =
        viewModelScope.launch {
            val validName = normalizeGroupName(name) ?: return@launch
            repository.renameGroup(id, validName)
        }

    suspend fun deleteGroupWithUndo(id: Long): Pair<FavoriteGroupEntity, List<Long>>? {
        val active = settingsRepository.settings.first().activeFavoriteGroupId
        val token = repository.deleteGroup(id) ?: return null
        if (active == id) settingsRepository.setActiveFavoriteGroup(null)
        return token
    }

    suspend fun restoreGroup(group: FavoriteGroupEntity, members: List<Long>) {
        val restoredId = repository.restoreGroup(group, members)
        settingsRepository.setActiveFavoriteGroup(restoredId)
    }

    fun reorderGroups(orderedIds: List<Long>): Job =
        viewModelScope.launch { repository.reorderGroups(orderedIds) }

    private fun normalizeGroupName(name: String): String? =
        name.trim().take(12).ifEmpty { null }

    fun enterSelecting() {
        if (_selection.value == null) _selection.value = emptySet()
    }

    fun exitSelecting() {
        _selection.value = null
    }

    fun toggleSelection(favoriteId: Long) {
        val current = _selection.value ?: emptySet()
        _selection.value = if (favoriteId in current) current - favoriteId else current + favoriteId
    }

    fun selectAll(ids: List<Long>) {
        _selection.value = ids.toSet()
    }

    suspend fun moveSelectedToGroup(groupId: Long?): Int {
        val ids = _selection.value?.toList().orEmpty()
        if (ids.isEmpty()) return 0
        repository.moveFavoritesToGroup(ids, groupId)
        _selection.value = null
        return ids.size
    }

    suspend fun deleteSelected() {
        val ids = _selection.value?.toList().orEmpty()
        if (ids.isEmpty()) return
        repository.deleteFavorites(ids)
        _selection.value = null
    }

    fun deleteFavorite(id: Long): Job =
        viewModelScope.launch { repository.deleteFavorite(id) }

    suspend fun addLocalText(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isBlank()) return false
        repository.addLocalText(normalized, currentActiveGroupId())
        return true
    }

    suspend fun addLocalFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        val resolver = ctx.contentResolver
        val info = readLocalFileInfo(uri)
        val input = resolver.openInputStream(uri) ?: return@withContext false
        runCatching {
            repository.addLocalFile(
                name = info.name,
                sizeBytes = info.sizeBytes,
                mime = info.mime,
                groupId = currentActiveGroupId(),
                source = input,
            )
        }.isSuccess
    }

    suspend fun sendFavorite(favorite: FavoriteEntity): Boolean {
        val controller = ServiceLocator.currentController ?: return false
        return when (favorite.kind) {
            "TEXT" -> {
                val text = favorite.textContent.orEmpty()
                if (text.isBlank()) return false
                controller.sendText(text)
                true
            }
            "FILE" -> {
                val depotId = favorite.fileId ?: return false
                val file = favoriteFileStore().resolve(depotId)
                withContext(Dispatchers.IO) {
                    controller.offerStoredFile(
                        source = file,
                        name = favorite.fileName ?: "unnamed",
                        size = favorite.fileSize ?: file.length(),
                        mime = favorite.fileMime ?: "application/octet-stream",
                    )
                }
            }
            else -> false
        }
    }

    private suspend fun currentActiveGroupId(): Long? =
        settingsRepository.settings.first().activeFavoriteGroupId

    private data class LocalFileInfo(
        val name: String,
        val sizeBytes: Long?,
        val mime: String,
    )

    private fun readLocalFileInfo(uri: Uri): LocalFileInfo {
        val ctx = getApplication<Application>()
        val resolver = ctx.contentResolver
        var name: String? = null
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        name = cursor.getString(nameIndex)
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex).takeIf { it >= 0L }
                    }
                }
            }
        return LocalFileInfo(
            name = name?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                ?: "unnamed",
            sizeBytes = size,
            mime = resolver.getType(uri) ?: "application/octet-stream",
        )
    }

    fun openFavoriteFile(favorite: FavoriteEntity) {
        val depotId = favorite.fileId ?: return
        val ctx = getApplication<Application>()
        val file = favoriteFileStore().resolve(depotId)
        if (!file.exists()) {
            Toast.makeText(ctx, R.string.file_missing, Toast.LENGTH_SHORT).show()
            return
        }
        val authority = "${ctx.packageName}.fileprovider"
        val uri = try {
            FileProvider.getUriForFile(ctx, authority, file, favorite.fileName ?: depotId)
        } catch (_: IllegalArgumentException) {
            Toast.makeText(ctx, R.string.file_provider_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, favorite.fileMime?.ifBlank { null } ?: "application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(Intent.createChooser(intent, ctx.getString(R.string.file_open_chooser)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, R.string.file_no_handler, Toast.LENGTH_SHORT).show()
        }
    }
}
