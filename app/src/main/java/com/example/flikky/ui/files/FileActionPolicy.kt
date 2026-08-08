package com.example.flikky.ui.files

/** Row overflow-menu actions, in display order. */
enum class RowAction { FAVORITE, SHARE, GALLERY, SAVE_AS, OPEN_IN_SESSION, DELETE }

data class RowMenuEntry(val action: RowAction, val enabled: Boolean)

/** Pure policy for file-overview row actions and batch sharing. */
object FileActionPolicy {

    fun rowMenu(mime: String?, sessionEnded: Boolean): List<RowMenuEntry> = buildList {
        add(RowMenuEntry(RowAction.FAVORITE, true))
        add(RowMenuEntry(RowAction.SHARE, true))
        if (FilesListBuilder.isMedia(mime)) add(RowMenuEntry(RowAction.GALLERY, true))
        add(RowMenuEntry(RowAction.SAVE_AS, true))
        add(RowMenuEntry(RowAction.OPEN_IN_SESSION, sessionEnded))
        add(RowMenuEntry(RowAction.DELETE, sessionEnded))
    }

    /** Uses a shared major MIME wildcard, falling back to the generic wildcard. */
    fun batchShareMime(mimes: List<String?>): String {
        val majors = mimes.map { mime ->
            mime.orEmpty().substringBefore('/', missingDelimiterValue = "")
                .takeIf { it.isNotBlank() }
        }
        val first = majors.firstOrNull() ?: return "*/*"
        if (majors.any { it != first }) return "*/*"
        return "$first/*"
    }
}
