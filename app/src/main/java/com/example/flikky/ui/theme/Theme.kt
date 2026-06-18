package com.example.flikky.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.example.flikky.data.settings.DarkMode
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.data.settings.ThemeMode

val LocalFlikkySettings = compositionLocalOf { FlikkySettings() }

@Composable
fun FlikkyTheme(settings: FlikkySettings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (settings.darkMode) {
        DarkMode.SYSTEM -> systemDark
        DarkMode.LIGHT  -> false
        DarkMode.DARK   -> true
    }
    val base = when {
        settings.themeMode == ThemeMode.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (useDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        else -> presetScheme(settings.presetTheme, useDark)
    }
    val scheme = if (settings.amoled && useDark) amoledOverride(base) else base

    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(useDark) {
            val window = (view.context as Activity).window
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                // 浅色主题 → 深色系统栏图标；深色主题 → 浅色图标。状态栏与导航栏一起设，
                // 配合 isNavigationBarContrastEnforced=false 让透明系统栏上的图标始终可读。
                isAppearanceLightStatusBars = !useDark
                isAppearanceLightNavigationBars = !useDark
            }
            onDispose {}
        }
    }

    CompositionLocalProvider(LocalFlikkySettings provides settings) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            shapes = FlikkyShapes,
            content = content,
        )
    }
}
