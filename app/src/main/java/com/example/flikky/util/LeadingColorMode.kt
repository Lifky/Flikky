package com.example.flikky.util

enum class LeadingColorMode {
    THEME,
    HARMONIZED,
    FIXED;

    companion object {
        fun parse(raw: String?): LeadingColorMode =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: THEME
    }
}
