package com.example.flikky.util

import java.util.Locale

data class LeadingType(
    val id: String,
    val symbol: String,
    val mimePrefixes: List<String>,
    val mimeExact: List<String>,
    val hueShift: Int,
    val fixedArgb: Long,
) {
    fun matches(mime: String): Boolean =
        mimeExact.any { mime == it } || mimePrefixes.any { mime.startsWith(it) }
}

object LeadingVisualCatalog {
    val types: List<LeadingType> = listOf(
        LeadingType(
            id = "image",
            symbol = "image",
            mimePrefixes = listOf("image/"),
            mimeExact = emptyList(),
            hueShift = 0,
            fixedArgb = 0xFF7E57C2,
        ),
        LeadingType(
            id = "video",
            symbol = "movie",
            mimePrefixes = listOf("video/"),
            mimeExact = emptyList(),
            hueShift = 72,
            fixedArgb = 0xFFEC407A,
        ),
        LeadingType(
            id = "audio",
            symbol = "audio_file",
            mimePrefixes = listOf("audio/"),
            mimeExact = emptyList(),
            hueShift = 144,
            fixedArgb = 0xFF26A69A,
        ),
        LeadingType(
            id = "document",
            symbol = "description",
            mimePrefixes = listOf("text/"),
            mimeExact = listOf(
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            ),
            hueShift = 216,
            fixedArgb = 0xFF42A5F5,
        ),
        LeadingType(
            id = "archive",
            symbol = "folder_zip",
            mimePrefixes = emptyList(),
            mimeExact = listOf(
                "application/zip",
                "application/x-7z-compressed",
                "application/vnd.rar",
                "application/x-rar-compressed",
                "application/x-tar",
                "application/gzip",
                "application/x-gzip",
            ),
            hueShift = 288,
            fixedArgb = 0xFFFFA726,
        ),
        LeadingType(
            id = "other",
            symbol = "draft",
            mimePrefixes = emptyList(),
            mimeExact = emptyList(),
            hueShift = 0,
            fixedArgb = 0xFF78909C,
        ),
    )

    private val other = types.single { it.id == "other" }

    fun typeOf(mime: String?): LeadingType {
        val normalized = mime.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
        if (normalized == "image/svg+xml") return other
        return types.firstOrNull { it.matches(normalized) } ?: other
    }
}
