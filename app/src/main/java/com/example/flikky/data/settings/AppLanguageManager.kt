package com.example.flikky.data.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

object AppLanguageManager {
    fun current(context: Context): AppLanguage = AppLanguage.fromLanguageTags(
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
    )

    fun set(context: Context, language: AppLanguage) {
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            if (language == AppLanguage.SYSTEM) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(language.languageTags)
            }
    }

    fun effectiveLanguageTag(context: Context): String {
        val localeManager = context.getSystemService(LocaleManager::class.java)
        return AppLanguage.resolveEffectiveLanguageTag(
            applicationLanguageTags = localeManager.applicationLocales.toLanguageTags(),
            systemLanguageTags = localeManager.systemLocales.toLanguageTags(),
        )
    }
}
