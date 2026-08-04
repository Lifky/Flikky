package com.example.flikky.data

import java.io.File

/**
 * Executes every delete-all step independently. Once confirmed, one failed step must not
 * prevent the remaining user data from being removed.
 */
class AppDataWiper(
    private val clearDatabase: suspend () -> Unit,
    private val fileStore: SessionFileStore,
    private val favoriteFileStore: FavoriteFileStore,
    private val tempFiles: () -> List<File>,
    private val clearSettings: suspend () -> Unit,
    private val resetRuntime: () -> Unit,
) {
    suspend fun wipe(resetSettings: Boolean) {
        runCatching { clearDatabase() }
        runCatching { fileStore.deleteAllSessionDirs() }
        runCatching { favoriteFileStore.deleteAll() }
        tempFiles().forEach { runCatching { it.delete() } }
        if (resetSettings) runCatching { clearSettings() }
        runCatching { resetRuntime() }
    }
}
