package com.example.flikky.ui.favorites

import android.app.Application
import androidx.datastore.preferences.core.Preferences
import com.example.flikky.data.FavoriteFileStore
import com.example.flikky.data.FavoritesRepository
import com.example.flikky.data.db.entities.FavoriteEntity
import com.example.flikky.data.db.entities.FavoriteGroupEntity
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.data.settings.SettingsRepository
import com.example.flikky.di.ServiceLocator
import com.example.flikky.service.TransferController
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FavoritesViewModelTest {
    private fun preferences(): Preferences = mockk(relaxed = true)

    @Test fun favorites_filter_by_active_group_and_query() = runTest {
        val app = mockk<Application>(relaxed = true)
        val repo = mockk<FavoritesRepository>()
        val all = listOf(
            favorite(id = 1L, text = "Alpha text", groupId = 7L),
            favorite(id = 2L, fileName = "beta.pdf", groupId = 8L),
            favorite(id = 3L, text = "Alpha other", groupId = null),
        )
        val settings = mockk<SettingsRepository>()
        every { repo.observeFavorites() } returns MutableStateFlow(all)
        every { repo.observeGroups() } returns MutableStateFlow(emptyList())
        every { repo.search(any(), any()) } answers {
            val rows = firstArg<List<FavoriteEntity>>()
            val query = secondArg<String>()
            if (query.isBlank()) rows else rows.filter {
                it.textContent?.contains(query, ignoreCase = true) == true ||
                    it.fileName?.contains(query, ignoreCase = true) == true
            }
        }
        every { settings.settings } returns MutableStateFlow(FlikkySettings(activeFavoriteGroupId = 7L))

        val vm = FavoritesViewModel(app, repo, settings)
        vm.setQuery("alpha")

        assertEquals(listOf(1L), vm.items.first().map { it.id })
    }

    @Test fun createGroup_selects_new_favorite_group() = runTest {
        val app = mockk<Application>(relaxed = true)
        val repo = stubRepo()
        val settings = stubSettings()
        coEvery { repo.createGroup("Ammo") } returns 9L
        coEvery { settings.setActiveFavoriteGroup(any()) } returns preferences()

        val vm = FavoritesViewModel(app, repo, settings)
        vm.createGroup("Ammo").join()

        coVerify { repo.createGroup("Ammo") }
        coVerify { settings.setActiveFavoriteGroup(9L) }
    }

    @Test fun addLocalText_trims_and_uses_active_favorite_group() = runTest {
        val app = mockk<Application>(relaxed = true)
        val repo = stubRepo()
        val settings = mockk<SettingsRepository>()
        every { settings.settings } returns MutableStateFlow(FlikkySettings(activeFavoriteGroupId = 7L))
        coEvery { repo.addLocalText(any(), any()) } returns 11L

        val vm = FavoritesViewModel(app, repo, settings)
        val added = vm.addLocalText("  hello  ")

        assertTrue(added)
        coVerify { repo.addLocalText("hello", 7L) }
    }

    @Test fun addLocalText_rejects_blank_without_insert() = runTest {
        val repo = stubRepo()
        val vm = FavoritesViewModel(mockk(relaxed = true), repo, stubSettings())

        val added = vm.addLocalText("   ")

        assertFalse(added)
        coVerify(exactly = 0) { repo.addLocalText(any(), any()) }
    }

    @Test fun deleteGroupWithUndo_clears_active_only_for_deleted_group() = runTest {
        val app = mockk<Application>(relaxed = true)
        val repo = stubRepo()
        val settings = mockk<SettingsRepository>()
        val group = FavoriteGroupEntity(id = 7L, name = "Ammo", sortOrder = 0, createdAt = 1L)
        every { settings.settings } returns MutableStateFlow(FlikkySettings(activeFavoriteGroupId = 7L))
        coEvery { repo.deleteGroup(7L) } returns (group to listOf(1L, 2L))
        coEvery { settings.setActiveFavoriteGroup(any()) } returns preferences()

        val vm = FavoritesViewModel(app, repo, settings)
        val token = vm.deleteGroupWithUndo(7L)

        assertEquals(group to listOf(1L, 2L), token)
        coVerify { settings.setActiveFavoriteGroup(null) }
    }

    @Test fun selection_move_and_delete_delegate_then_exit_selecting() = runTest {
        val app = mockk<Application>(relaxed = true)
        val repo = stubRepo()
        val settings = stubSettings()
        coEvery { repo.moveFavoritesToGroup(any(), any()) } just Runs
        coEvery { repo.deleteFavorites(any()) } just Runs

        val vm = FavoritesViewModel(app, repo, settings)
        vm.toggleSelection(1L)
        vm.toggleSelection(2L)

        assertEquals(2, vm.moveSelectedToGroup(7L))
        coVerify { repo.moveFavoritesToGroup(listOf(1L, 2L), 7L) }
        assertEquals(null, vm.selection.value)

        vm.toggleSelection(3L)
        vm.deleteSelected()
        coVerify { repo.deleteFavorites(listOf(3L)) }
        assertEquals(null, vm.selection.value)
    }

    @Test fun sendFavorite_text_sends_text_and_returns_true() = runTest {
        val controller = mockk<TransferController>()
        coEvery { controller.sendText("hello") } just Runs
        ServiceLocator.currentController = controller

        val vm = FavoritesViewModel(mockk(relaxed = true), stubRepo(), stubSettings())
        val sent = vm.sendFavorite(favorite(id = 1L, text = "hello", groupId = null))

        assertTrue(sent)
        coVerify { controller.sendText("hello") }
    }

    @Test fun sendFavorite_blank_text_returns_false_without_send() = runTest {
        val controller = mockk<TransferController>()
        ServiceLocator.currentController = controller

        val vm = FavoritesViewModel(mockk(relaxed = true), stubRepo(), stubSettings())
        val sent = vm.sendFavorite(favorite(id = 1L, text = "   ", groupId = null))

        assertFalse(sent)
        coVerify(exactly = 0) { controller.sendText(any()) }
    }

    @Test fun sendFavorite_file_delegates_to_offerStoredFile() = runTest {
        val controller = mockk<TransferController>()
        coEvery { controller.offerStoredFile(any(), any(), any(), any()) } returns true
        ServiceLocator.currentController = controller
        val store = mockk<FavoriteFileStore>()
        val depotFile = File("depot-1")
        every { store.resolve("depot-1") } returns depotFile

        val fileFav = FavoriteEntity(
            id = 2L,
            sourceSessionId = 1L,
            sourceMessageId = 2L,
            kind = "FILE",
            fileId = "depot-1",
            fileName = "beta.pdf",
            fileSize = 1234L,
            fileMime = "application/pdf",
            groupId = null,
            createdAt = 2L,
        )
        val vm = FavoritesViewModel(mockk(relaxed = true), stubRepo(), stubSettings(), { store })
        val sent = vm.sendFavorite(fileFav)

        assertTrue(sent)
        coVerify { controller.offerStoredFile(depotFile, "beta.pdf", 1234L, "application/pdf") }
    }

    @Test fun sendFavorite_returns_false_when_no_controller() = runTest {
        ServiceLocator.currentController = null

        val vm = FavoritesViewModel(mockk(relaxed = true), stubRepo(), stubSettings())
        val sent = vm.sendFavorite(favorite(id = 1L, text = "hello", groupId = null))

        assertFalse(sent)
    }

    @After fun clearController() {
        ServiceLocator.currentController = null
    }

    private fun stubRepo(): FavoritesRepository {
        val repo = mockk<FavoritesRepository>()
        every { repo.observeFavorites() } returns MutableStateFlow(emptyList())
        every { repo.observeGroups() } returns MutableStateFlow(emptyList())
        every { repo.search(any(), any()) } answers { firstArg() }
        return repo
    }

    private fun stubSettings(): SettingsRepository {
        val settings = mockk<SettingsRepository>()
        every { settings.settings } returns MutableStateFlow(FlikkySettings())
        return settings
    }

    private fun favorite(
        id: Long,
        text: String? = null,
        fileName: String? = null,
        groupId: Long?,
    ) = FavoriteEntity(
        id = id,
        sourceSessionId = 1L,
        sourceMessageId = id,
        kind = if (fileName == null) "TEXT" else "FILE",
        textContent = text,
        fileName = fileName,
        groupId = groupId,
        createdAt = id,
    )
}
