package com.example.flikky.ui.favorites

import android.app.Application
import androidx.datastore.preferences.core.Preferences
import com.example.flikky.data.FavoritesRepository
import com.example.flikky.data.db.entities.FavoriteEntity
import com.example.flikky.data.db.entities.FavoriteGroupEntity
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.data.settings.SettingsRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

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
