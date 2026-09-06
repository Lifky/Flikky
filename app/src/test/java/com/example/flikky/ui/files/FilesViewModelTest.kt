package com.example.flikky.ui.files

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.settings.SettingsRepository
import com.example.flikky.util.SortKey
import com.example.flikky.util.SortSpec
import com.example.flikky.data.db.FileOverviewRow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FilesViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var app: Application
    private lateinit var repository: SessionRepository
    private lateinit var source: MutableStateFlow<List<FileOverviewRow>>

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        repository = mockk()
        source = MutableStateFlow(emptyList())
        every { repository.observeAllFiles() } returns source
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun row(
        id: Long,
        mime: String,
        size: Long,
        endedAt: Long? = 9L,
    ) = FileOverviewRow(
        messageId = id,
        sessionId = id + 100,
        sessionName = "session-$id",
        sessionEndedAt = endedAt,
        origin = "BROWSER",
        fileId = "file-$id",
        fileName = "name-$id",
        fileSize = size,
        fileMime = mime,
        timestamp = id,
    )

    private fun settingsRepo(): SettingsRepository {
        val ds = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher),
            produceFile = { File.createTempFile("files-sort", ".preferences_pb").also { it.delete() } },
        )
        return SettingsRepository(ds)
    }

    @Test
    fun `setSort writes through to settings instead of living only in memory`() = runTest(dispatcher) {
        // 此前排序只在 ViewModel 内存里，重启即丢。
        val settings = settingsRepo()
        val viewModel = FilesViewModel(app, repository, settings)

        viewModel.setSort(SortKey.SIZE)

        assertEquals(
            SortSpec(SortKey.SIZE, descending = true),
            settings.settings.first().filesSort,
        )
    }

    @Test
    fun `tapping the current key flips direction through the shared kernel`() = runTest(dispatcher) {
        val settings = settingsRepo()
        val viewModel = FilesViewModel(app, repository, settings)
        backgroundScope.launch(dispatcher) { viewModel.sort.collect() }

        viewModel.setSort(SortKey.SIZE)
        viewModel.setSort(SortKey.SIZE)

        assertEquals(
            SortSpec(SortKey.SIZE, descending = false),
            settings.settings.first().filesSort,
        )
    }

    @Test
    fun `rows and stats follow category filtering`() = runTest(dispatcher) {
        source.value = listOf(
            row(1, "image/png", size = 10),
            row(2, "video/mp4", size = 20),
            row(3, "image/jpeg", size = 30),
        )
        val viewModel = FilesViewModel(app, repository)
        backgroundScope.launch(dispatcher) { viewModel.rows.collect() }
        backgroundScope.launch(dispatcher) { viewModel.stats.collect() }

        viewModel.setCategory(FileCategory.IMAGE)

        assertEquals(listOf(3L, 1L), viewModel.rows.value.map { it.messageId })
        assertEquals(FileStats(count = 2, totalBytes = 40L), viewModel.stats.value)
    }

    @Test
    fun `deleteRows skips active sessions and reports requested count`() = runTest(dispatcher) {
        val deleted = row(1, "image/png", size = 10)
        val failed = row(2, "video/mp4", size = 20)
        val active = row(3, "audio/mpeg", size = 30, endedAt = null)
        coEvery {
            repository.deleteFileBlob(deleted.sessionId, deleted.messageId, deleted.fileId)
        } returns true
        coEvery {
            repository.deleteFileBlob(failed.sessionId, failed.messageId, failed.fileId)
        } returns false
        val viewModel = FilesViewModel(app, repository)
        viewModel.enterSelecting()

        val result = viewModel.deleteRows(listOf(deleted, failed, active))

        assertEquals(1 to 3, result)
        coVerify(exactly = 1) {
            repository.deleteFileBlob(deleted.sessionId, deleted.messageId, deleted.fileId)
        }
        coVerify(exactly = 1) {
            repository.deleteFileBlob(failed.sessionId, failed.messageId, failed.fileId)
        }
        coVerify(exactly = 0) {
            repository.deleteFileBlob(active.sessionId, active.messageId, active.fileId)
        }
        assertNull(viewModel.selection.value)
    }

    @Test
    fun `selection entry toggle select all and exit are stable`() {
        val viewModel = FilesViewModel(app, repository)

        assertFalse(viewModel.selecting.value)
        viewModel.enterSelecting()
        viewModel.enterSelecting()
        assertEquals(emptySet<Long>(), viewModel.selection.value)
        assertTrue(viewModel.selecting.value)

        viewModel.toggleSelection(1L)
        viewModel.toggleSelection(2L)
        viewModel.toggleSelection(1L)
        assertEquals(setOf(2L), viewModel.selection.value)

        viewModel.selectAll(listOf(3L, 4L, 4L))
        assertEquals(setOf(3L, 4L), viewModel.selection.value)
        viewModel.exitSelecting()
        assertNull(viewModel.selection.value)
        assertFalse(viewModel.selecting.value)
    }

    @Test
    fun `markMissing delegates deleted marker to repository`() = runTest(dispatcher) {
        coEvery { repository.markFileDeleted(9L) } returns Unit
        val viewModel = FilesViewModel(app, repository)

        viewModel.markMissing(9L).join()

        coVerify(exactly = 1) { repository.markFileDeleted(9L) }
    }
}
