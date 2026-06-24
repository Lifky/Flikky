package com.example.flikky.data

import java.io.File

class FavoriteFileStore(
    private val filesDir: File,
) {
    private fun favoriteDir(): File = File(filesDir, "favorites").apply { mkdirs() }

    fun copyIn(depotFileId: String, source: File): File {
        require(source.exists() && source.isFile) { "Favorite source file does not exist: ${source.absolutePath}" }
        val target = resolve(depotFileId)
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    fun resolve(depotFileId: String): File = File(favoriteDir(), depotFileId)

    fun delete(depotFileId: String): Boolean {
        val file = resolve(depotFileId)
        if (!file.exists()) return true
        return file.delete()
    }
}
