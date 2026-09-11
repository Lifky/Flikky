package com.example.flikky.ui.settings

import android.app.Application
import com.example.flikky.data.SessionFileStore
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.data.settings.SettingsRepository
import com.example.flikky.network.UpdateChecker
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsThumbnailCacheViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun calculating_state_is_distinct_from_zero_bytes() = runTest(dispatcher) {
        val vm = createViewModel(store())

        assertNull(vm.thumbnailCacheUsageBytes.value)
        advanceUntilIdle()
        assertEquals(0L, vm.thumbnailCacheUsageBytes.value)
    }

    @Test fun refresh_sums_cached_files_off_the_calling_turn() = runTest(dispatcher) {
        val store = store()
        store.storageThumbFile("a".repeat(64)).writeBytes(ByteArray(3))
        store.storageThumbFile("b".repeat(64)).writeBytes(ByteArray(5))
        val vm = createViewModel(store)

        assertNull(vm.thumbnailCacheUsageBytes.value)
        advanceUntilIdle()
        assertEquals(8L, vm.thumbnailCacheUsageBytes.value)
    }

    @Test fun clear_deletes_cache_and_updates_usage_to_zero() = runTest(dispatcher) {
        val store = store()
        store.storageThumbFile("a".repeat(64)).writeBytes(ByteArray(7))
        val vm = createViewModel(store)
        advanceUntilIdle()
        assertEquals(7L, vm.thumbnailCacheUsageBytes.value)

        vm.clearThumbnailCache()
        advanceUntilIdle()

        assertEquals(0L, vm.thumbnailCacheUsageBytes.value)
        assertFalse(store.storageThumbnailCacheDir().listFiles().orEmpty().any())
    }

    private fun store(): SessionFileStore =
        SessionFileStore(tmp.root, tmp.newFolder("cache-${tmp.root.list().orEmpty().size}"))

    private fun createViewModel(fileStore: SessionFileStore): SettingsViewModel {
        val settingsRepository = mockk<SettingsRepository>()
        every { settingsRepository.settings } returns MutableStateFlow(FlikkySettings())
        return SettingsViewModel(
            app = mockk<Application>(relaxed = true),
            repository = settingsRepository,
            sessionRepository = mockk<SessionRepository>(relaxed = true),
            updateChecker = mockk<UpdateChecker>(relaxed = true),
            fileStore = fileStore,
            cacheIoDispatcher = dispatcher,
        )
    }
}
