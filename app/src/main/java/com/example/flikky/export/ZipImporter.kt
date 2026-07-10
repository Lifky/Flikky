package com.example.flikky.export

import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class ParsedSession(
    val originalId: Long,
    val name: String,
    val startedAt: Long,
    val endedAt: Long?,
    val pinned: Boolean,
    val messages: List<ParsedMessage>,
    val version: String?,
    val sessionDir: String,
)

data class ParsedBackup(
    val scope: ExportScope,
    val sessions: List<ParsedSession>,
    val favoriteGroups: List<FavoriteGroupExport>,
    val favorites: List<FavoriteExport>,
    val settings: SettingsExport?,
)

sealed class ParsedMessage {
    abstract val ts: Long
    abstract val origin: String

    data class Text(
        override val ts: Long,
        override val origin: String,
        val content: String,
    ) : ParsedMessage()

    data class File(
        override val ts: Long,
        override val origin: String,
        val fileId: String,
        val name: String,
        val mime: String,
        val sizeBytes: Long,
        val relativePath: String,
    ) : ParsedMessage()
}

object ZipImporter {

    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val versionRegex = Regex("""Version:\s*([\d.]+)""")
    private val sessionDirRegex = Regex("""^sessions/([^/]+)/messages\.json$""")

    fun parse(zipFile: ZipFile): List<ParsedSession> {
        val version = readVersion(zipFile)
        val sessions = mutableListOf<ParsedSession>()

        for (entry in zipFile.entries()) {
            if (isPathTraversal(entry.name)) continue
            val match = sessionDirRegex.matchEntire(entry.name) ?: continue
            val sessionDir = "sessions/${match.groupValues[1]}"

            val jsonContent = zipFile.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
            val dto = runCatching {
                jsonParser.decodeFromString(SessionDto.serializer(), jsonContent)
            }.getOrNull() ?: continue

            val messages = dto.messages.map { it.toParsedMessage() }
            sessions.add(ParsedSession(
                originalId = dto.sessionId,
                name = dto.name,
                startedAt = dto.startedAt,
                endedAt = dto.endedAt,
                pinned = dto.pinned,
                messages = messages,
                version = version,
                sessionDir = sessionDir,
            ))
        }
        return sessions
    }

    fun parseBackup(zipFile: ZipFile): ParsedBackup {
        val manifest = zipFile.getEntry("manifest.json")
            ?.takeUnless { isPathTraversal(it.name) }
            ?.let { entry ->
                val raw = zipFile.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
                jsonParser.decodeFromString(BackupManifestDto.serializer(), raw)
            }
        val favorites = zipFile.getEntry("favorites/favorites.json")
            ?.takeUnless { isPathTraversal(it.name) }
            ?.let { entry ->
                val raw = zipFile.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
                jsonParser.decodeFromString(FavoritesArchiveDto.serializer(), raw)
            }
        val settings = zipFile.getEntry("settings/settings.json")
            ?.takeUnless { isPathTraversal(it.name) }
            ?.let { entry ->
                val raw = zipFile.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
                jsonParser.decodeFromString(SettingsExport.serializer(), raw)
            }
        return ParsedBackup(
            scope = manifest?.scope ?: ExportScope.SESSIONS,
            sessions = parse(zipFile),
            favoriteGroups = favorites?.groups.orEmpty(),
            favorites = favorites?.favorites.orEmpty(),
            settings = settings,
        )
    }

    fun resolveFavoriteFileEntry(favorite: FavoriteExport, zipFile: ZipFile): ZipEntry? {
        val path = favorite.relativePath ?: return null
        if (isPathTraversal(path) || !path.startsWith("favorites/files/")) return null
        return zipFile.getEntry(path)?.takeUnless { isPathTraversal(it.name) }
    }

    fun resolveFileEntry(
        version: String?,
        fileMessages: List<ParsedMessage.File>,
        targetFileId: String,
        sessionDir: String,
        zipFile: ZipFile,
    ): ZipEntry? {
        val isV14OrLater = version != null && compareVersions(version, "1.4") >= 0

        if (isV14OrLater) {
            val target = fileMessages.firstOrNull { it.fileId == targetFileId } ?: return null
            if (isPathTraversal(target.relativePath) || !target.relativePath.startsWith("files/")) {
                return null
            }
            val entryPath = "$sessionDir/${target.relativePath}"
            return zipFile.getEntry(entryPath)?.takeUnless { isPathTraversal(it.name) }
        }

        // Backward compat: replay the dedup algorithm to compute actual zip entry names
        val seen = mutableMapOf<String, Int>()
        for (fm in fileMessages) {
            val dedupName = ZipExporter.nextUniqueName(seen, fm.name)
            if (fm.fileId == targetFileId) {
                val entryPath = "$sessionDir/files/$dedupName"
                return zipFile.getEntry(entryPath)
            }
        }
        return null
    }

    fun getEntryStream(zipFile: ZipFile, entry: ZipEntry): InputStream = zipFile.getInputStream(entry)

    private fun readVersion(zipFile: ZipFile): String? {
        val readme = zipFile.getEntry("README.txt") ?: return null
        val content = zipFile.getInputStream(readme).use { it.readBytes().toString(Charsets.UTF_8) }
        return versionRegex.find(content)?.groupValues?.get(1)
    }

    private fun compareVersions(a: String, b: String): Int {
        val partsA = a.split(".").map { it.toIntOrNull() ?: 0 }
        val partsB = b.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(partsA.size, partsB.size)
        for (i in 0 until len) {
            val pa = partsA.getOrElse(i) { 0 }
            val pb = partsB.getOrElse(i) { 0 }
            if (pa != pb) return pa.compareTo(pb)
        }
        return 0
    }

    private fun isPathTraversal(name: String): Boolean =
        name.startsWith("/") ||
            name.startsWith("\\") ||
            name.split('/', '\\').any { it == ".." }

    private fun MessageDto.toParsedMessage(): ParsedMessage = when (this) {
        is MessageDto.TextDto -> ParsedMessage.Text(
            ts = ts, origin = origin, content = content,
        )
        is MessageDto.FileDto -> ParsedMessage.File(
            ts = ts, origin = origin, fileId = fileId,
            name = name, mime = mime, sizeBytes = sizeBytes,
            relativePath = relativePath,
        )
    }
}
