package com.example.flikky.ui.files

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.db.FileOverviewRow
import com.example.flikky.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class FilesViewModel @JvmOverloads constructor(
    app: Application,
    private val repository: SessionRepository = ServiceLocator.repository,
) : AndroidViewModel(app) {
    private val _category = MutableStateFlow(FileCategory.ALL)
    val category: StateFlow<FileCategory> = _category.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(FileSort.TIME)
    val sort: StateFlow<FileSort> = _sort.asStateFlow()

    private val _selection = MutableStateFlow<Set<Long>?>(null)
    val selection: StateFlow<Set<Long>?> = _selection.asStateFlow()

    val selecting: StateFlow<Boolean> = _selection
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val rows: StateFlow<List<FileOverviewRow>> = combine(
        repository.observeAllFiles(),
        _category,
        _query,
        _sort,
    ) { source, category, query, sort ->
        FilesListBuilder.build(source, category, query, sort)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val stats: StateFlow<FileStats> = rows
        .map(FilesListBuilder::stats)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            FileStats(count = 0, totalBytes = 0L),
        )

    fun setCategory(category: FileCategory) {
        _category.value = category
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun setSort(sort: FileSort) {
        _sort.value = sort
    }

    fun enterSelecting() {
        if (_selection.value == null) _selection.value = emptySet()
    }

    fun exitSelecting() {
        _selection.value = null
    }

    fun toggleSelection(messageId: Long) {
        val current = _selection.value ?: emptySet()
        _selection.value = if (messageId in current) {
            current - messageId
        } else {
            current + messageId
        }
    }

    fun selectAll(ids: List<Long>) {
        _selection.value = ids.toSet()
    }

    suspend fun deleteRows(rows: List<FileOverviewRow>): Pair<Int, Int> {
        val deletable = rows.filter { it.sessionEndedAt != null }
        var deleted = 0
        for (row in deletable) {
            if (repository.deleteFileBlob(row.sessionId, row.messageId, row.fileId)) {
                deleted += 1
            }
        }
        exitSelecting()
        return deleted to rows.size
    }
}
