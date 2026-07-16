package com.example.flikky.data.settings

enum class AppLanguage(val languageTags: String) {
    SYSTEM(""),
    SIMPLIFIED_CHINESE("zh-CN"),
    ENGLISH("en"),
    ;

    companion object {
        const val DEFAULT_LANGUAGE_TAG = "zh-CN"

        fun fromLanguageTags(languageTags: String): AppLanguage {
            val language = languageTags
                .substringBefore(',')
                .substringBefore('-')
                .lowercase()

            return when (language) {
                "zh" -> SIMPLIFIED_CHINESE
                "en" -> ENGLISH
                else -> SYSTEM
            }
        }

        fun resolveEffectiveLanguageTag(
            applicationLanguageTags: String,
            systemLanguageTags: String,
        ): String {
            val candidates = if (applicationLanguageTags.isBlank()) {
                systemLanguageTags
            } else {
                applicationLanguageTags
            }
            return candidates
                .split(',')
                .firstNotNullOfOrNull { tag ->
                    when (tag.trim().substringBefore('-').lowercase()) {
                        "zh" -> SIMPLIFIED_CHINESE.languageTags
                        "en" -> ENGLISH.languageTags
                        else -> null
                    }
                }
                ?: DEFAULT_LANGUAGE_TAG
        }
    }
}
